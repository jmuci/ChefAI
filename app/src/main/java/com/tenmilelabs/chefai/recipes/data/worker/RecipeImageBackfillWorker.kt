package com.tenmilelabs.chefai.recipes.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tenmilelabs.chefai.core.data.local.room.RecipeImageStateEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeImageStateDao
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeImageCandidate
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.recipes.domain.usecase.CacheRecipeImage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.File

/** How many times a single recipe's image is retried before it is given up on for good. */
internal const val MAX_IMAGE_BACKFILL_ATTEMPTS = 3

/** Rows examined per run. Bounds the query, not the work — most rows are already resolved. */
internal const val IMAGE_BACKFILL_SCAN_LIMIT = 200

/** Images actually fetched per run, so one sweep can't pull tens of megabytes in a burst. */
internal const val IMAGE_BACKFILL_BATCH = 10

/**
 * Fetches recipe images this device is missing but another one already has.
 *
 * A recipe arriving over sync carries only its remote `imageUrl`, never the importing device's
 * cached copy — a local file path is meaningless on the wire (ADR-011). For most sources the URL is
 * enough and the UI just hotlinks it, but the CDNs that made on-device caching necessary in the
 * first place (Akamai on Food Network, Cloudflare on Serious Eats) refuse a plain HTTP client
 * wherever it runs. So the second device re-derives the image the same way the first one did, with
 * [CacheRecipeImage]'s HTTP-then-WebView ladder.
 *
 * Deliberately not on the pull path: spinning up a `WebView` inside the sync transaction would
 * stretch every sync by seconds for something no one is waiting on. Deliberately not on the render
 * path either — that would land the same spin-up mid-scroll. It runs on its own schedule, on a
 * charger and off metered data, and until it does the UI degrades to hotlinking, which already works
 * everywhere except the bot-walled minority.
 */
@HiltWorker
class RecipeImageBackfillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val recipeDao: RecipeDao,
    private val recipeImageStateDao: RecipeImageStateDao,
    private val cacheRecipeImage: CacheRecipeImage,
    private val syncScheduler: SyncScheduler,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val candidates = recipeDao
            .getImageBackfillCandidates(MAX_IMAGE_BACKFILL_ATTEMPTS, IMAGE_BACKFILL_SCAN_LIMIT)
            .needingBackfill()

        if (candidates.isEmpty()) {
            Timber.d("Image backfill: nothing to do")
            return Result.success()
        }

        val batch = candidates.take(IMAGE_BACKFILL_BATCH)
        Timber.i("Image backfill: fetching %d of %d missing images", batch.size, candidates.size)

        var restored = 0
        for (candidate in batch) {
            // Recorded before the fetch, not after: a run killed mid-WebView (the budget expiring,
            // the charger unplugged, the process dying) must still count, or a URL that reliably
            // hangs would be retried forever.
            recordAttempt(candidate)

            val storedPath = cacheRecipeImage(candidate.recipeId, candidate.imageUrl)
            if (storedPath != null) {
                recipeDao.updateLocalImagePath(candidate.recipeId, storedPath)
                recipeImageStateDao.delete(candidate.recipeId)
                restored++
            }
        }

        Timber.i("Image backfill: restored %d of %d", restored, batch.size)

        // More than one batch's worth outstanding — come back for the rest rather than waiting for
        // the next sync, which might be a day away.
        if (candidates.size > batch.size) {
            syncScheduler.scheduleImageBackfill()
        }
        return Result.success()
    }

    private suspend fun recordAttempt(candidate: RecipeImageCandidate) {
        val previous = recipeImageStateDao.getByRecipeId(candidate.recipeId)
        recipeImageStateDao.upsert(
            RecipeImageStateEntity(
                recipeId = candidate.recipeId,
                attempts = (previous?.attempts ?: 0) + 1,
                lastAttemptAt = System.currentTimeMillis(),
            )
        )
    }
}

/**
 * Narrows the over-selected candidates from
 * [RecipeDao.getImageBackfillCandidates] to those whose image is genuinely absent.
 *
 * A null [RecipeImageCandidate.localImagePath] is the never-cached case; a non-null path pointing at
 * nothing is a file that has since been deleted, which this repairs. Split out from the worker so it
 * is testable without WorkManager.
 */
internal fun List<RecipeImageCandidate>.needingBackfill(
    fileExists: (String) -> Boolean = { File(it).exists() },
): List<RecipeImageCandidate> = filter { candidate ->
    val path = candidate.localImagePath
    path == null || !fileExists(path)
}
