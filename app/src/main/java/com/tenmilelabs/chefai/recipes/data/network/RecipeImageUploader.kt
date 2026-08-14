package com.tenmilelabs.chefai.recipes.data.network

import com.tenmilelabs.chefai.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.put
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Header the backend uses to verify the body arrived intact, and to key the stored blob. */
private const val CONTENT_SHA256_HEADER = "X-Content-SHA256"

/**
 * Uploaded images are always JPEG: a picked photo is re-encoded by `RecipeImageStore.writeFromUri`,
 * and a scraped one is whatever the source served. Declaring JPEG for the latter is harmless — the
 * backend sniffs the magic bytes rather than trusting this header — but see [detectMimeType].
 */
private val DEFAULT_IMAGE_TYPE = ContentType.Image.JPEG

@Serializable
private data class UploadImageResponse(
    @SerialName("imageBlobId") val imageBlobId: String,
    @SerialName("updatedAt") val updatedAt: Long = 0,
)

/**
 * The three outcomes the upload sweep needs to tell apart.
 *
 * The permanent/transient split is what stops a rejected upload being retried forever: a body the
 * server will never accept (wrong hash, wrong type, too big, quota, a recipe that isn't ours) fails
 * identically on every attempt, so it burns the attempt counter straight to its cap rather than one
 * at a time.
 */
sealed interface ImageUploadOutcome {
    data class Success(val imageBlobId: String) : ImageUploadOutcome

    /** The server will never accept these bytes for this recipe. Stop asking. */
    data class Permanent(val reason: String) : ImageUploadOutcome

    /** Server-side or network trouble. Worth another go later. */
    data class Transient(val reason: String) : ImageUploadOutcome
}

/**
 * Uploads a recipe's cached image to the backend.
 *
 * Deliberately uses the **default, authenticated** [HttpClient], never
 * [com.tenmilelabs.chefai.core.di.ScraperHttpClient] — this is our own host and the request must
 * carry the JWT. The inverse matters just as much: the URL is built only from
 * [BuildConfig.API_BASE_URL], never from a recipe's `imageUrl`, so an auth token can never be sent
 * to a third-party CDN (ADR-010).
 *
 * Raw body rather than multipart. There is exactly one file and no form fields, so a multipart
 * envelope would add a boundary parser on both ends and buy nothing.
 */
@Singleton
class RecipeImageUploader @Inject constructor(
    private val client: HttpClient,
    private val json: Json,
) {

    suspend fun upload(recipeId: UUID, bytes: ByteArray): ImageUploadOutcome = try {
        val response = client.put("${BuildConfig.API_BASE_URL}/recipes/$recipeId/image") {
            contentType(detectMimeType(bytes))
            header(CONTENT_SHA256_HEADER, sha256Hex(bytes))
            setBody(bytes)
            expectSuccess = false
        }

        when {
            response.status.isSuccess() -> {
                val body = json.decodeFromString<UploadImageResponse>(response.bodyAsText())
                Timber.i("Uploaded image for %s as %s", recipeId, body.imageBlobId)
                ImageUploadOutcome.Success(body.imageBlobId)
            }

            // 408 and 429 are the two 4xx that say "later", not "never".
            response.status == HttpStatusCode.RequestTimeout ||
                response.status == HttpStatusCode.TooManyRequests ->
                ImageUploadOutcome.Transient("HTTP ${response.status.value}")

            response.status.value in 400..499 -> {
                Timber.w(
                    "Image upload for %s permanently rejected: %s", recipeId, response.status
                )
                ImageUploadOutcome.Permanent("HTTP ${response.status.value}")
            }

            else -> ImageUploadOutcome.Transient("HTTP ${response.status.value}")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Includes a malformed success body: a 200 we cannot parse leaves us not knowing the blob
        // id, which is indistinguishable from not having uploaded, so it must be retried.
        Timber.w(e, "Image upload for %s failed", recipeId)
        ImageUploadOutcome.Transient(e.message ?: e::class.java.simpleName)
    }
}

/** Lowercase hex SHA-256, the form the backend compares against. */
internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

/**
 * Picks a `Content-Type` from the bytes themselves rather than from the file name, which these files
 * do not have — [com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore] stores them extensionless
 * because Coil sniffs the type anyway.
 *
 * The backend re-sniffs and rejects a mismatch, so getting this wrong is a failed upload rather than
 * a security hole; it is here so that a legitimately-PNG scraped image isn't rejected for claiming
 * to be a JPEG.
 */
internal fun detectMimeType(bytes: ByteArray): ContentType = when {
    bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() &&
        bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte() -> ContentType.Image.JPEG

    bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() &&
        bytes[1] == 'P'.code.toByte() &&
        bytes[2] == 'N'.code.toByte() &&
        bytes[3] == 'G'.code.toByte() -> ContentType.Image.PNG

    bytes.size >= 12 &&
        bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
        bytes.copyOfRange(8, 12).decodeToString() == "WEBP" -> ContentType("image", "webp")

    else -> DEFAULT_IMAGE_TYPE
}
