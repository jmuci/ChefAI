package com.tenmilelabs.chefai.recipes.data.network

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable

/**
 * Reads at most [MAX_IMAGE_BYTES], returning `null` — rather than a truncated image — if the body is
 * larger, whether or not a (possibly absent or wrong) `Content-Length` header said so.
 *
 * Shared by both directions of the image ladder: the scrape fetch, which reads from an untrusted
 * third-party CDN, and the backend fetch, which reads from our own server. The cap is worth keeping
 * on both — a truncated image is indistinguishable from a corrupt one once it is on disk, and our
 * own server is not a reason to read an unbounded body into memory.
 */
internal suspend fun HttpResponse.readImageBodyCapped(): ByteArray? {
    val declaredLength = contentLength()
    if (declaredLength != null && declaredLength > MAX_IMAGE_BYTES) return null

    val channel = bodyAsChannel()
    val buffer = ByteArray(MAX_IMAGE_BYTES)
    var offset = 0
    while (offset < buffer.size) {
        val read = channel.readAvailable(buffer, offset, buffer.size - offset)
        if (read == -1) break
        offset += read
    }
    if (offset == buffer.size) {
        val probe = ByteArray(1)
        if (channel.readAvailable(probe, 0, 1) > 0) return null
    }
    return buffer.copyOf(offset)
}
