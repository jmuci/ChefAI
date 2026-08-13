package com.tenmilelabs.chefai.recipes.data.local

import android.content.Context
import com.tenmilelabs.chefai.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists downloaded recipe images to app-internal storage, keyed by recipe id.
 *
 * Lives in `filesDir`, not `cacheDir` — these bytes came from a CDN that may 403 a plain HTTP
 * client (see [com.tenmilelabs.chefai.recipes.domain.usecase.CacheRecipeImage]), so an OS-triggered
 * cache eviction would lose the image permanently rather than merely cost a re-fetch. Filenames
 * carry no extension; Coil sniffs the content type from the bytes themselves.
 */
@Singleton
class RecipeImageStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val directory: File
        get() = File(context.filesDir, "recipe_images")

    private fun fileFor(recipeId: UUID) = File(directory, recipeId.toString())

    /**
     * Writes [bytes] for [recipeId], atomically (a `.tmp` file is written first, then renamed).
     *
     * @return the absolute path of the stored file, or `null` if the write failed.
     */
    suspend fun write(recipeId: UUID, bytes: ByteArray): String? = withContext(ioDispatcher) {
        try {
            val dir = directory
            if (!dir.exists() && !dir.mkdirs()) {
                Timber.w("Failed to create recipe image directory")
                return@withContext null
            }
            val target = fileFor(recipeId)
            val tmpFile = File(dir, "${recipeId}.tmp")
            tmpFile.writeBytes(bytes)
            if (!tmpFile.renameTo(target)) {
                Timber.w("Failed to move recipe image into place for %s", recipeId)
                tmpFile.delete()
                return@withContext null
            }
            target.absolutePath
        } catch (e: Exception) {
            Timber.w(e, "Failed to write recipe image for %s", recipeId)
            null
        }
    }

    /** Removes the stored image for [recipeId], if any. */
    suspend fun delete(recipeId: UUID): Unit = withContext(ioDispatcher) {
        fileFor(recipeId).delete()
        Unit
    }

    /** Removes every stored recipe image — used when the local database is wiped wholesale. */
    suspend fun deleteAll(): Unit = withContext(ioDispatcher) {
        directory.deleteRecursively()
        Unit
    }
}
