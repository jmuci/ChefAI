package com.tenmilelabs.chefai.recipes.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeImageStateDao
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeImageUploadCandidate
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.recipes.domain.usecase.UploadRecipeImage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.File

/** How many times one recipe's image is offered to the backend before it is given up on. */
internal const val MAX_IMAGE_UPLOAD_ATTEMPTS = 3

/** Rows examined per run. Bounds the query, not the work — most rows are already uploaded. */
internal const val IMAGE_UPLOAD_SCAN_LIMIT = 200

/** Images actually sent per run, so one sweep can't push tens of megabytes in a burst. */
internal const val IMAGE_UPLOAD_BATCH = 10

/**
 * Sends recipe images this device has and the backend doesn't.
 *
 * This is the half of cross-device images that no amount of client cleverness can replace. A scraped
 * image can, in principle, be re-derived anywhere from its source URL; a photo the user took has no
 * source at all, so until it is uploaded the device holding it holds the only copy in existence, and
 * a reinstall or a lost phone destroys it. The candidate query puts those first for exactly that
 * reason.
 *
 * Runs after a successful sync rather than on the mutation path: the recipe row must already exist
 * server-side for its image to attach to, and nothing is waiting on the bytes.
 *
 * Constraints are deliberately weaker than [RecipeImageBackfillWorker]'s — unmetered, but no charging
 * requirement. Backfill waits for a charger because a `WebView` spin-up per image is genuinely
 * expensive and costs nothing to defer. An upload is one request of a few hundred kilobytes, and
 * every hour it waits is another hour a user's only copy of a photo lives on one device.
 */
@HiltWorker
class RecipeImageUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val recipeDao: RecipeDao,
    private val recipeImageStateDao: RecipeImageStateDao,
    private val uploadRecipeImage: UploadRecipeImage,
    private val syncScheduler: SyncScheduler,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val candidates = recipeDao
            .getImageUploadCandidates(MAX_IMAGE_UPLOAD_ATTEMPTS, IMAGE_UPLOAD_SCAN_LIMIT)
            .needingUpload()

        if (candidates.isEmpty()) {
            Timber.d("Image upload: nothing to do")
            return Result.success()
        }

        val batch = candidates.take(IMAGE_UPLOAD_BATCH)
        Timber.i("Image upload: sending %d of %d", batch.size, candidates.size)

        var uploaded = 0
        for (candidate in batch) {
            recipeImageStateDao.recordUploadAttempt(candidate.recipeId, System.currentTimeMillis())
            if (uploadRecipeImage(candidate.recipeId)) uploaded++
        }

        Timber.i("Image upload: %d of %d landed", uploaded, batch.size)

        if (candidates.size > batch.size) {
            syncScheduler.scheduleImageUpload()
        }
        return Result.success()
    }
}

/**
 * Narrows the over-selected rows from [RecipeDao.getImageUploadCandidates] to those whose bytes are
 * genuinely not on the backend.
 *
 * Two cases qualify. A null [RecipeImageUploadCandidate.imageBlobId] has never been uploaded. A
 * non-null one whose file's `lastModified()` differs from the timestamp recorded at upload has been
 * *replaced* since — the picker writes a new photo over the same path, so nothing else about the row
 * changes and the pointer alone would say the work was already done.
 *
 * A row whose file has vanished is skipped rather than uploaded; repairing that is the download
 * sweep's job, not this one's.
 *
 * Split out from the worker so it is JVM-testable without WorkManager.
 */
internal fun List<RecipeImageUploadCandidate>.needingUpload(
    lastModified: (String) -> Long? = { File(it).takeIf(File::exists)?.lastModified() },
): List<RecipeImageUploadCandidate> = filter { candidate ->
    val currentMtime = lastModified(candidate.localImagePath)
    when {
        currentMtime == null -> false
        candidate.imageBlobId == null -> true
        else -> currentMtime != candidate.uploadedFileModifiedAt
    }
}
