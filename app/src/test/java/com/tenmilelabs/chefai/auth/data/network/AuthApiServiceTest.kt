package com.tenmilelabs.chefai.auth.data.network

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.data.network.dto.GoogleSignInRequest
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
import org.junit.Test

class AuthApiServiceTest {

    private lateinit var engine: MockEngine

    private fun service(engine: MockEngine): AuthApiService {
        this.engine = engine
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return AuthApiService(client)
    }

    private val lastRequest: HttpRequestData get() = engine.requestHistory.last()
    private val lastBody: String get() = (lastRequest.body as TextContent).text

    private fun jsonEngine(body: String, status: HttpStatusCode) = MockEngine {
        respond(content = body, status = status, headers = headersOf("Content-Type", ContentType.Application.Json.toString()))
    }

    private val authResponseJson = """
        {"token":"a","refreshToken":"r","userId":"0194b3c0-0000-7000-8000-000000000001",
         "username":"Jose","email":"j@example.com","expiresIn":3600,
         "hasPassword":false,"providers":["google"]}
    """.trimIndent()

    // ==================== googleSignIn ====================

    @Test
    fun `googleSignIn posts the idToken to auth-google and parses sign-in methods`() = runTest {
        val subject = service(jsonEngine(authResponseJson, HttpStatusCode.OK))

        val response = subject.googleSignIn(GoogleSignInRequest(idToken = "tok"))

        assertThat(lastRequest.url.encodedPath).isEqualTo("/auth/google")
        assertThat(lastRequest.method.value).isEqualTo("POST")
        assertThat(lastBody).isEqualTo("""{"idToken":"tok"}""")
        assertThat(response.hasPassword).isFalse()
        assertThat(response.providers).containsExactly("google")
    }

    @Test
    fun `an auth response from an older backend defaults to a password and no providers`() = runTest {
        val subject = service(
            jsonEngine(
                """{"token":"a","refreshToken":"r","userId":"0194b3c0-0000-7000-8000-000000000001","username":"J","email":"j@example.com","expiresIn":3600}""",
                HttpStatusCode.OK,
            )
        )

        val response = subject.googleSignIn(GoogleSignInRequest(idToken = "tok"))

        assertThat(response.hasPassword).isTrue()
        assertThat(response.providers).isEmpty()
    }

    @Test
    fun `googleSignIn maps a 409 to AuthHttpException carrying the code`() = runTest {
        val subject = service(
            jsonEngine("""{"message":"Email already registered","code":"GOOGLE_EMAIL_EXISTS"}""", HttpStatusCode.Conflict)
        )

        val error = runCatching { subject.googleSignIn(GoogleSignInRequest(idToken = "tok")) }.exceptionOrNull()

        assertThat(error).isInstanceOf(AuthHttpException::class.java)
        error as AuthHttpException
        assertThat(error.statusCode).isEqualTo(409)
        assertThat(error.code).isEqualTo("GOOGLE_EMAIL_EXISTS")
        assertThat(error.message).isEqualTo("Email already registered")
    }

    @Test
    fun `googleSignIn maps a 401 INVALID_GOOGLE_TOKEN`() = runTest {
        val subject = service(
            jsonEngine("""{"message":"Invalid token","code":"INVALID_GOOGLE_TOKEN"}""", HttpStatusCode.Unauthorized)
        )

        val error = runCatching { subject.googleSignIn(GoogleSignInRequest(idToken = "tok")) }.exceptionOrNull()

        assertThat((error as AuthHttpException).code).isEqualTo("INVALID_GOOGLE_TOKEN")
    }

    @Test
    fun `googleSignIn survives a non-JSON error body`() = runTest {
        val subject = service(
            MockEngine {
                respond(
                    content = "<html>Bad gateway</html>",
                    status = HttpStatusCode.BadGateway,
                    headers = headersOf("Content-Type", ContentType.Text.Html.toString()),
                )
            }
        )

        val error = runCatching { subject.googleSignIn(GoogleSignInRequest(idToken = "tok")) }.exceptionOrNull()

        assertThat(error).isInstanceOf(AuthHttpException::class.java)
        assertThat((error as AuthHttpException).statusCode).isEqualTo(502)
        assertThat(error.code).isNull()
    }

    // ==================== linkGoogle ====================

    @Test
    fun `linkGoogle posts to auth-google-link and accepts 204`() = runTest {
        val subject = service(MockEngine { respond(content = "", status = HttpStatusCode.NoContent) })

        subject.linkGoogle(GoogleSignInRequest(idToken = "tok"))

        assertThat(lastRequest.url.encodedPath).isEqualTo("/auth/google/link")
        assertThat(lastRequest.method.value).isEqualTo("POST")
        assertThat(lastBody).isEqualTo("""{"idToken":"tok"}""")
    }

    @Test
    fun `linkGoogle maps a 409 to AuthHttpException carrying the code`() = runTest {
        val subject = service(
            jsonEngine("""{"message":"Different email","code":"GOOGLE_EMAIL_MISMATCH"}""", HttpStatusCode.Conflict)
        )

        val error = runCatching { subject.linkGoogle(GoogleSignInRequest(idToken = "tok")) }.exceptionOrNull()

        assertThat((error as AuthHttpException).code).isEqualTo("GOOGLE_EMAIL_MISMATCH")
        assertThat(error.statusCode).isEqualTo(409)
    }

    @Test
    fun `GoogleSignInRequest never prints its token`() {
        assertThat(GoogleSignInRequest(idToken = "secret-token").toString()).doesNotContain("secret-token")
    }
}
