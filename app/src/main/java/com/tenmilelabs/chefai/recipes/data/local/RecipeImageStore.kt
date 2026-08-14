package com.tenmilelabs.chefai.recipes.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.tenmilelabs.chefai.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Longest edge, in pixels, a user-picked photo is downscaled to before it is stored.
 *
 * A hero image is never displayed larger than the screen, so keeping a 12MP camera original would
 * cost tens of megabytes for pixels nothing ever reads.
 */
private const val PICKED_IMAGE_MAX_EDGE = 2048

/** JPEG quality for a re-encoded user-picked photo — visually lossless at [PICKED_IMAGE_MAX_EDGE]. */
private const val PICKED_IMAGE_JPEG_QUALITY = 85

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

    /**
     * Decodes the image at [uri] — a `content://` from the photo picker — downscales it, and stores
     * it for [recipeId].
     *
     * Decoding rather than copying the bytes verbatim is deliberate on two counts. A photo straight
     * off a modern camera is routinely 8–15MB, so a raw copy would store an order of magnitude more
     * than a hero image needs; and [ImageDecoder] applies the EXIF orientation tag while decoding,
     * where a raw copy would leave portrait shots rendering sideways.
     *
     * The [com.tenmilelabs.chefai.recipes.data.network.MAX_IMAGE_BYTES] cap deliberately does *not*
     * apply here: it bounds an untrusted network read, whereas this is a local file the user picked
     * explicitly, already bounded by [PICKED_IMAGE_MAX_EDGE].
     *
     * @return the absolute path of the stored file, or `null` if the image could not be read.
     */
    suspend fun writeFromUri(recipeId: UUID, uri: String): String? = withContext(ioDispatcher) {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull()
        if (parsed == null) {
            Timber.w("Could not parse picked image uri for %s", recipeId)
            return@withContext null
        }

        val declaredType = runCatching { context.contentResolver.getType(parsed) }.getOrNull()
        if (declaredType != null && !declaredType.startsWith("image/")) {
            Timber.w("Refusing to store %s for %s — not an image", declaredType, recipeId)
            return@withContext null
        }

        val bytes = try {
            val source = ImageDecoder.createSource(context.contentResolver, parsed)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.setTargetSampleSize(sampleSizeFor(info.size.width, info.size.height))
                // ImageDecoder hands back a hardware bitmap by default, which cannot be compressed.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, PICKED_IMAGE_JPEG_QUALITY, out)
                bitmap.recycle()
                out.toByteArray()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to decode picked image for %s", recipeId)
            return@withContext null
        }

        Timber.d("Stored picked image for %s (%d bytes)", recipeId, bytes.size)
        write(recipeId, bytes)
    }

    /**
     * Reads the stored image for [recipeId], or `null` if there isn't one.
     *
     * Returns the whole file in memory, which is fine here and only here: everything this store
     * holds has already been bounded — a network fetch by
     * [com.tenmilelabs.chefai.recipes.data.network.MAX_IMAGE_BYTES], a picked photo by
     * [PICKED_IMAGE_MAX_EDGE] and JPEG re-encoding.
     */
    suspend fun read(recipeId: UUID): ByteArray? = withContext(ioDispatcher) {
        try {
            fileFor(recipeId).takeIf { it.exists() }?.readBytes()
        } catch (e: Exception) {
            Timber.w(e, "Failed to read recipe image for %s", recipeId)
            null
        }
    }

    /**
     * Last-modified timestamp of the stored image, or `null` if there isn't one.
     *
     * The upload sweep uses this to notice a photo that was *replaced*: files are keyed by recipe
     * id, so a new pick overwrites the same path and every other signal stays identical. [write]
     * renames a temp file into place, so this always moves when the bytes do.
     */
    suspend fun lastModified(recipeId: UUID): Long? = withContext(ioDispatcher) {
        fileFor(recipeId).takeIf { it.exists() }?.lastModified()
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

/**
 * Smallest power-of-two subsampling factor that brings a [width]×[height] image's longest edge down
 * to [PICKED_IMAGE_MAX_EDGE]. Powers of two are what `ImageDecoder` subsamples most cheaply.
 */
internal fun sampleSizeFor(width: Int, height: Int): Int {
    var sampleSize = 1
    while (max(width, height) / sampleSize > PICKED_IMAGE_MAX_EDGE) {
        sampleSize *= 2
    }
    return sampleSize
}
