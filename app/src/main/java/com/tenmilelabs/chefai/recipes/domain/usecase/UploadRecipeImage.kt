package com.tenmilelabs.chefai.recipes.domain.usecase

import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeImageStateDao
import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore
import com.tenmilelabs.chefai.recipes.data.network.ImageUploadOutcome
import com.tenmilelabs.chefai.recipes.data.network.RecipeImageUploader
import com.tenmilelabs.chefai.recipes.data.worker.MAX_IMAGE_UPLOAD_ATTEMPTS
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Sends one recipe's cached image to the backend and records the result.
 *
 * On success the returned blob id is written back with a targeted UPDATE that deliberately does not
 * touch `updatedAt` or `syncState`: the value came from the server, so pushing it back would churn
 * the pull delta forever. The bookkeeping row is then cleared, which is also what resets the attempt
 * counter for a future replacement of the same image.
 *
 * Never throws. A failure here leaves the local file exactly where it was, which is still a working
 * image on this device — the cost is only that other devices don't get it yet.
 */
class UploadRecipeImage @Inject constructor(
    private val recipeImageStore: RecipeImageStore,
    private val recipeImageUploader: RecipeImageUploader,
    private val recipeDao: RecipeDao,
    private val recipeImageStateDao: RecipeImageStateDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** @return `true` if the bytes are now on the backend. */
    suspend operator fun invoke(recipeId: UUID): Boolean = withContext(ioDispatcher) {
        val bytes = recipeImageStore.read(recipeId)
        if (bytes == null) {
            // The row claimed a local path but the file is gone. Nothing to upload; the download
            // sweep is the one that repairs this.
            Timber.d("No local image to upload for %s", recipeId)
            return@withContext false
        }

        // Captured before the upload, not after: if the user replaces the photo mid-flight, the
        // later mtime must not be recorded against these bytes, or the replacement never uploads.
        val modifiedAt = recipeImageStore.lastModified(recipeId)

        when (val outcome = recipeImageUploader.upload(recipeId, bytes)) {
            is ImageUploadOutcome.Success -> {
                recipeDao.updateImageBlobId(recipeId, outcome.imageBlobId)
                recipeImageStateDao.recordUploadSuccess(recipeId, modifiedAt)
                true
            }

            is ImageUploadOutcome.Permanent -> {
                Timber.w("Giving up on uploading %s: %s", recipeId, outcome.reason)
                recipeImageStateDao.burnUploadAttempts(
                    recipeId = recipeId,
                    at = System.currentTimeMillis(),
                    cap = MAX_IMAGE_UPLOAD_ATTEMPTS,
                )
                false
            }

            is ImageUploadOutcome.Transient -> {
                Timber.d("Will retry uploading %s: %s", recipeId, outcome.reason)
                false
            }
        }
    }
}
