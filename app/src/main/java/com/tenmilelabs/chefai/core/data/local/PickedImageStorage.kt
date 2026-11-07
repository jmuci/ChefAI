package com.tenmilelabs.chefai.core.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
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
 * Writes [bytes] to `[directory]/[name]` atomically: a `.tmp` sibling is written first, then
 * renamed over the target, so a reader never sees a half-written file.
 *
 * Blocking; callers run it on an IO dispatcher.
 *
 * @return the absolute path of the stored file, or `null` if the write failed.
 */
internal fun writeFileAtomically(directory: File, name: String, bytes: ByteArray): String? {
    return try {
        if (!directory.exists() && !directory.mkdirs()) {
            Timber.w("Failed to create image directory %s", directory.name)
            return null
        }
        val target = File(directory, name)
        val tmpFile = File(directory, "$name.tmp")
        tmpFile.writeBytes(bytes)
        if (!tmpFile.renameTo(target)) {
            Timber.w("Failed to move image into place for %s", name)
            tmpFile.delete()
            return null
        }
        target.absolutePath
    } catch (e: Exception) {
        Timber.w(e, "Failed to write image %s", name)
        null
    }
}

/**
 * Decodes the image at [uri] — a `content://` from the photo picker — downscales it to
 * [PICKED_IMAGE_MAX_EDGE] and re-encodes it as JPEG. [label] only identifies the owner in logs.
 *
 * Decoding rather than copying the bytes verbatim is deliberate on two counts. A photo straight
 * off a modern camera is routinely 8–15MB, so a raw copy would store an order of magnitude more
 * than a hero image needs; and [ImageDecoder] applies the EXIF orientation tag while decoding,
 * where a raw copy would leave portrait shots rendering sideways.
 *
 * Blocking; callers run it on an IO dispatcher.
 *
 * @return the JPEG bytes, or `null` if the uri is unparseable, not an image, or undecodable.
 */
internal fun decodePickedImageAsJpeg(context: Context, uri: String, label: String): ByteArray? {
    val parsed = runCatching { Uri.parse(uri) }.getOrNull()
    if (parsed == null) {
        Timber.w("Could not parse picked image uri for %s", label)
        return null
    }

    val declaredType = runCatching { context.contentResolver.getType(parsed) }.getOrNull()
    if (declaredType != null && !declaredType.startsWith("image/")) {
        Timber.w("Refusing to store %s for %s — not an image", declaredType, label)
        return null
    }

    return try {
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
        Timber.w(e, "Failed to decode picked image for %s", label)
        null
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
