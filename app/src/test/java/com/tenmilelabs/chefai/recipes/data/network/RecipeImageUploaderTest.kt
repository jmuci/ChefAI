package com.tenmilelabs.chefai.recipes.data.network

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.IOException
import java.util.UUID

private val JPEG_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x01)
private val PNG_BYTES = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

class RecipeImageUploaderTest {

    private val recipeId: UUID = UUID.randomUUID()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var engine: MockEngine

    private fun uploader(engine: MockEngine): RecipeImageUploader {
        this.engine = engine
        val client = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        return RecipeImageUploader(client, json)
    }

    private val lastRequest: HttpRequestData get() = engine.requestHistory.last()

    private fun okEngine(blobId: String = "x") = MockEngine {
        respond(
            content = """{"imageBlobId":"$blobId","updatedAt":1755100000000}""",
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
        )
    }

    private fun errorEngine(status: HttpStatusCode) = MockEngine { respondError(status) }

    @Test
    fun `a successful upload returns the blob id the server assigned`() = runTest {
        val subject = uploader(okEngine("deadbeef"))

        val outcome = subject.upload(recipeId, JPEG_BYTES)

        assertThat(outcome).isEqualTo(ImageUploadOutcome.Success("deadbeef"))
    }

    @Test
    fun `the request PUTs the raw bytes with their hash and detected type`() = runTest {
        val subject = uploader(okEngine())

        subject.upload(recipeId, JPEG_BYTES)

        val request = lastRequest
        assertThat(request.method).isEqualTo(HttpMethod.Put)
        assertThat(request.url.encodedPath).endsWith("/recipes/$recipeId/image")
        assertThat(request.headers["X-Content-SHA256"]).isEqualTo(sha256Hex(JPEG_BYTES))
        assertThat(request.body.contentType?.withoutParameters())
            .isEqualTo(ContentType.Image.JPEG)
        assertThat((request.body as OutgoingContent.ByteArrayContent).bytes())
            .isEqualTo(JPEG_BYTES)
    }

    @Test
    fun `a PNG is declared as a PNG, not blanket JPEG`() = runTest {
        val subject = uploader(okEngine())

        subject.upload(recipeId, PNG_BYTES)

        assertThat(lastRequest.body.contentType?.withoutParameters())
            .isEqualTo(ContentType.Image.PNG)
    }

    @Test
    fun `a 4xx rejection is permanent, so the sweep stops asking`() = runTest {
        val subject = uploader(errorEngine(HttpStatusCode.UnprocessableEntity))

        val outcome = subject.upload(recipeId, JPEG_BYTES)

        assertThat(outcome).isInstanceOf(ImageUploadOutcome.Permanent::class.java)
    }

    @Test
    fun `a disabled scraped upload is permanent — the flag will not flip on retry`() = runTest {
        val subject = uploader(errorEngine(HttpStatusCode.Forbidden))

        val outcome = subject.upload(recipeId, JPEG_BYTES)

        assertThat(outcome).isInstanceOf(ImageUploadOutcome.Permanent::class.java)
    }

    @Test
    fun `429 is transient despite being a 4xx — it explicitly means later`() = runTest {
        val subject = uploader(errorEngine(HttpStatusCode.TooManyRequests))

        val outcome = subject.upload(recipeId, JPEG_BYTES)

        assertThat(outcome).isInstanceOf(ImageUploadOutcome.Transient::class.java)
    }

    @Test
    fun `a 5xx is transient`() = runTest {
        val subject = uploader(errorEngine(HttpStatusCode.BadGateway))

        val outcome = subject.upload(recipeId, JPEG_BYTES)

        assertThat(outcome).isInstanceOf(ImageUploadOutcome.Transient::class.java)
    }

    @Test
    fun `an unparseable success body is transient, not a silent success`() = runTest {
        // A 200 we can't read leaves us not knowing the blob id, which is indistinguishable from
        // never having uploaded. Treating it as success would strand the recipe pointing at nothing.
        val subject = uploader(
            MockEngine {
                respond(
                    content = "not json",
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }
        )

        val outcome = subject.upload(recipeId, JPEG_BYTES)

        assertThat(outcome).isInstanceOf(ImageUploadOutcome.Transient::class.java)
    }

    @Test
    fun `a network failure is transient`() = runTest {
        val subject = uploader(MockEngine { throw IOException("connection reset") })

        val outcome = subject.upload(recipeId, JPEG_BYTES)

        assertThat(outcome).isInstanceOf(ImageUploadOutcome.Transient::class.java)
    }

    @Test
    fun `sha256Hex matches the known digest of an empty input`() {
        assertThat(sha256Hex(ByteArray(0)))
            .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    }
}
