package com.tenmilelabs.chefai.auth.data.network

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class PasswordResetApiServiceTest {

    private lateinit var engine: MockEngine

    private fun service(engine: MockEngine): PasswordResetApiService {
        this.engine = engine
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return PasswordResetApiService(client)
    }

    private val lastRequest: HttpRequestData get() = engine.requestHistory.last()
    private val lastBody: String get() = (lastRequest.body as TextContent).text

    private fun engine(status: HttpStatusCode, body: String = "", json: Boolean = true) = MockEngine {
        respond(
            content = body,
            status = status,
            headers = headersOf(
                "Content-Type",
                (if (json) ContentType.Application.Json else ContentType.Text.Plain).toString(),
            ),
        )
    }

    @Test
    fun `request posts the email to the request endpoint and maps 202 to Success`() = runTest {
        val subject = service(engine(HttpStatusCode.Accepted))

        val result = subject.requestReset("a@b.com")

        assertThat(result).isEqualTo(PasswordResetNetworkResult.Success)
        assertThat(lastRequest.url.encodedPath).isEqualTo("/auth/password-reset/request")
        assertThat(lastBody).isEqualTo("""{"email":"a@b.com"}""")
    }

    @Test
    fun `validate posts the token and maps 204 to Success`() = runTest {
        val subject = service(engine(HttpStatusCode.NoContent))

        val result = subject.validateToken("tok")

        assertThat(result).isEqualTo(PasswordResetNetworkResult.Success)
        assertThat(lastRequest.url.encodedPath).isEqualTo("/auth/password-reset/validate")
        assertThat(lastBody).isEqualTo("""{"token":"tok"}""")
    }

    @Test
    fun `the token never appears in the url`() = runTest {
        val subject = service(engine(HttpStatusCode.NoContent))

        subject.validateToken("secret-token")

        assertThat(lastRequest.url.toString()).doesNotContain("secret-token")
    }

    @Test
    fun `confirm posts the token and password and maps 204 to Success`() = runTest {
        val subject = service(engine(HttpStatusCode.NoContent))

        val result = subject.confirmReset("tok", "hunter22!")

        assertThat(result).isEqualTo(PasswordResetNetworkResult.Success)
        assertThat(lastRequest.url.encodedPath).isEqualTo("/auth/password-reset/confirm")
        assertThat(lastBody).isEqualTo("""{"token":"tok","newPassword":"hunter22!"}""")
    }

    @Test
    fun `410 maps to InvalidToken`() = runTest {
        val subject = service(
            engine(HttpStatusCode.Gone, """{"message":"Link expired","code":"RESET_TOKEN_INVALID"}""")
        )

        assertThat(subject.validateToken("tok")).isEqualTo(PasswordResetNetworkResult.InvalidToken)
        assertThat(subject.confirmReset("tok", "hunter22!")).isEqualTo(PasswordResetNetworkResult.InvalidToken)
    }

    @Test
    fun `400 carries the server message`() = runTest {
        val subject = service(
            engine(HttpStatusCode.BadRequest, """{"message":"Password must be at least 8 characters"}""")
        )

        val result = subject.confirmReset("tok", "short")

        assertThat(result)
            .isEqualTo(PasswordResetNetworkResult.Validation("Password must be at least 8 characters"))
    }

    @Test
    fun `400 with a body that is not json still maps to Validation with an empty message`() = runTest {
        val subject = service(engine(HttpStatusCode.BadRequest, "<html>nope</html>", json = false))

        assertThat(subject.requestReset("nope")).isEqualTo(PasswordResetNetworkResult.Validation(""))
    }

    @Test
    fun `429 maps to RateLimited`() = runTest {
        val subject = service(engine(HttpStatusCode.TooManyRequests))

        assertThat(subject.requestReset("a@b.com")).isEqualTo(PasswordResetNetworkResult.RateLimited)
    }

    @Test
    fun `any other status maps to Failed with the code`() = runTest {
        val subject = service(engine(HttpStatusCode.InternalServerError))

        assertThat(subject.requestReset("a@b.com")).isEqualTo(PasswordResetNetworkResult.Failed(500))
    }

    @Test
    fun `a transport failure propagates for the repository to classify`() = runTest {
        val subject = service(MockEngine { throw IOException("timeout") })

        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { subject.requestReset("a@b.com") } }
    }
}
