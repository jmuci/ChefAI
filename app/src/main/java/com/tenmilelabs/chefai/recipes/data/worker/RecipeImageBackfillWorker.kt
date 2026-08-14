package com.tenmilelabs.chefai.recipes.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeImageStateDao
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeImageCandidate
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.recipes.domain.usecase.CacheRecipeImage
import com.tenmilelabs.chefai.recipes.domain.usecase.FetchRecipeImageFromBackend
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
 * A recipe arriving over sync never carries the importing device's cached copy — a local file path
 * is meaningless on the wire (ADR-011). What it may carry is an `imageBlobId`, and the sweep prefers
 * that: a plain authenticated GET against our own backend, which will not bot-wall us. For a photo
 * the user took themselves that is the only possibility at all, since `imageUrl` is blank and there
 * is nothing to re-derive from.
 *
 * Failing that, the image is re-derived from its source the same way the first device got it, with
 * [CacheRecipeImage]'s HTTP-then-WebView ladder. That path still earns its place: a blob only exists
 * once some device has uploaded one, and the CDNs that made on-device caching necessary in the first
 * place (Akamai on Food Network, Cloudflare on Serious Eats) refuse a plain HTTP client wherever it
 * runs — including the app's own image loader, so hotlinking is not a working fallback for them.
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
    private val fetchRecipeImageFromBackend: FetchRecipeImageFromBackend,
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
            recipeImageStateDao.recordDownloadAttempt(
                candidate.recipeId, System.currentTimeMillis()
            )

            val storedPath = fetchImage(candidate)
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

    /**
     * Our own backend first, the source CDN second.
     *
     * The backend copy is preferred whenever it exists: it is a plain authenticated GET against a
     * host that will not bot-wall us, where the scrape ladder may have to spin up a `WebView` and
     * still fail. For a user's own photo it is not merely preferred but the only possibility —
     * `imageUrl` is blank and there is nothing to re-derive from.
     *
     * The fallback still matters, though. A blob is only there once *some* device has uploaded it,
     * so in the window before that, or if the upload was refused, re-deriving from the source is
     * what keeps the image appearing at all.
     */
    private suspend fun fetchImage(candidate: RecipeImageCandidate): String? {
        if (candidate.imageBlobId != null) {
            fetchRecipeImageFromBackend(candidate.recipeId)?.let { return it }
            Timber.d("Backend image unavailable for %s, falling back", candidate.recipeId)
        }
        if (candidate.imageUrl.isEmpty()) return null
        return cacheRecipeImage(candidate.recipeId, candidate.imageUrl)
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
