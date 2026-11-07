package com.tenmilelabs.chefai.mealplans.data.local

import android.content.Context
import com.tenmilelabs.chefai.core.data.local.decodePickedImageAsJpeg
import com.tenmilelabs.chefai.core.data.local.writeFileAtomically
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
 * Stores the user's hero photo for a saved meal plan under `filesDir/saved_plan_images/<uuid>`.
 *
 * Device-only: the photo is never uploaded or synced. Shares its decode/downsample/atomic-write
 * logic with `RecipeImageStore`.
 */
@Singleton
class SavedPlanImageStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val directory: File
        get() = File(context.filesDir, "saved_plan_images")

    /**
     * Decodes the picked image at [uri], downscales it and stores it for the saved plan [uuid].
     *
     * @return the absolute path of the stored file, or `null` if the image could not be read.
     */
    suspend fun writeFromUri(uuid: UUID, uri: String): String? = withContext(ioDispatcher) {
        val bytes = decodePickedImageAsJpeg(context, uri, uuid.toString())
            ?: return@withContext null
        Timber.d("Stored saved-plan image for %s (%d bytes)", uuid, bytes.size)
        writeFileAtomically(directory, uuid.toString(), bytes)
    }

    /** Removes the stored image for the saved plan [uuid], if any. */
    suspend fun delete(uuid: UUID): Unit = withContext(ioDispatcher) {
        File(directory, uuid.toString()).delete()
        Unit
    }

    /**
     * Renames the stored image of [oldUuid] to [newUuid] after the plan was re-keyed.
     *
     * @return the new absolute path, or `null` when there was no image to move.
     */
    suspend fun move(oldUuid: UUID, newUuid: UUID): String? = withContext(ioDispatcher) {
        val source = File(directory, oldUuid.toString())
        if (!source.exists()) return@withContext null
        val target = File(directory, newUuid.toString())
        if (source.renameTo(target)) target.absolutePath else null
    }

    /** Removes every stored saved-plan image — for when the local database is wiped wholesale. */
    suspend fun deleteAll(): Unit = withContext(ioDispatcher) {
        directory.deleteRecursively()
        Unit
    }
}
