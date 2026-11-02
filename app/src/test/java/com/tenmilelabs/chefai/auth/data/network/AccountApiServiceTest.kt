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
import org.junit.Test

class AccountApiServiceTest {

    private lateinit var engine: MockEngine

    private fun service(engine: MockEngine): AccountApiService {
        this.engine = engine
        val client = HttpClient(engine) {
            // Same Json as NetworkModule: defaults are not encoded, so a null field is left out.
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return AccountApiService(client)
    }

    private val lastRequest: HttpRequestData get() = engine.requestHistory.last()
    private val lastBody: String get() = (lastRequest.body as TextContent).text

    private fun jsonEngine(body: String, status: HttpStatusCode) = MockEngine {
        respond(content = body, status = status, headers = headersOf("Content-Type", ContentType.Application.Json.toString()))
    }

    /** What the backend's JWT `challenge` sends: plain text, not JSON. */
    private fun jwtChallengeEngine() = MockEngine {
        respond(
            content = "Token is not valid or has expired",
            status = HttpStatusCode.Unauthorized,
            headers = headersOf("Content-Type", ContentType.Text.Plain.toString()),
        )
    }

    // ==================== deletion preview ====================

    @Test
    fun `getDeletionPreview parses the counts`() = runTest {
        val subject = service(
            jsonEngine("""{"publicRecipeCount":2,"privateRecipeCount":5,"ownsHousehold":true}""", HttpStatusCode.OK)
        )

        val result = subject.getDeletionPreview()

        assertThat(result).isInstanceOf(AccountDeletionPreviewNetworkResult.Success::class.java)
        val response = (result as AccountDeletionPreviewNetworkResult.Success).response
        assertThat(response.publicRecipeCount).isEqualTo(2)
        assertThat(response.privateRecipeCount).isEqualTo(5)
        assertThat(response.ownsHousehold).isTrue()
        assertThat(lastRequest.url.encodedPath).isEqualTo("/api/v1/me/deletion-preview")
        assertThat(lastRequest.method.value).isEqualTo("GET")
    }

    @Test
    fun `getDeletionPreview maps a 401 to Unauthenticated`() = runTest {
        val subject = service(jwtChallengeEngine())

        assertThat(subject.getDeletionPreview()).isEqualTo(AccountDeletionPreviewNetworkResult.Unauthenticated)
    }

    @Test
    fun `getDeletionPreview maps a 5xx to Failed`() = runTest {
        val subject = service(jsonEngine("""{"message":"boom"}""", HttpStatusCode.InternalServerError))

        assertThat(subject.getDeletionPreview()).isEqualTo(AccountDeletionPreviewNetworkResult.Failed(500))
    }

    // ==================== delete ====================

    @Test
    fun `deleteAccount posts the password and choice and maps 204 to Deleted`() = runTest {
        val subject = service(MockEngine { respond(content = "", status = HttpStatusCode.NoContent) })

        val result = subject.deleteAccount(password = "hunter2", publicRecipes = "ANONYMIZE")

        assertThat(result).isEqualTo(DeleteAccountNetworkResult.Deleted)
        assertThat(lastRequest.url.encodedPath).isEqualTo("/api/v1/me/delete")
        assertThat(lastRequest.method.value).isEqualTo("POST")
        assertThat(lastBody).isEqualTo("""{"password":"hunter2","publicRecipes":"ANONYMIZE"}""")
    }

    @Test
    fun `deleteAccount leaves publicRecipes out of the body when there is no choice`() = runTest {
        val subject = service(MockEngine { respond(content = "", status = HttpStatusCode.NoContent) })

        subject.deleteAccount(password = "hunter2", publicRecipes = null)

        assertThat(lastBody).isEqualTo("""{"password":"hunter2"}""")
    }

    @Test
    fun `deleteAccount maps the password check's JSON 401 to WrongPassword`() = runTest {
        val subject = service(jsonEngine("""{"message":"Invalid password"}""", HttpStatusCode.Unauthorized))

        assertThat(subject.deleteAccount("wrong", null)).isEqualTo(DeleteAccountNetworkResult.WrongPassword)
    }

    @Test
    fun `deleteAccount maps the JWT challenge's plain-text 401 to Unauthenticated`() = runTest {
        val subject = service(jwtChallengeEngine())

        assertThat(subject.deleteAccount("hunter2", null)).isEqualTo(DeleteAccountNetworkResult.Unauthenticated)
    }

    @Test
    fun `deleteAccount maps PUBLIC_RECIPE_CHOICE_REQUIRED to the choice with the server's count`() = runTest {
        val subject = service(
            jsonEngine(
                """{"code":"PUBLIC_RECIPE_CHOICE_REQUIRED","message":"Choose","publicRecipeCount":4}""",
                HttpStatusCode.BadRequest,
            )
        )

        assertThat(subject.deleteAccount("hunter2", null))
            .isEqualTo(DeleteAccountNetworkResult.PublicRecipeChoiceRequired(publicRecipeCount = 4))
    }

    @Test
    fun `deleteAccount maps any other 400 to Failed`() = runTest {
        val subject = service(jsonEngine("""{"message":"Invalid request format"}""", HttpStatusCode.BadRequest))

        assertThat(subject.deleteAccount("hunter2", null)).isEqualTo(DeleteAccountNetworkResult.Failed(400))
    }

    @Test
    fun `deleteAccount maps 409 to Conflict and 429 to RateLimited`() = runTest {
        assertThat(service(jsonEngine("""{"message":"retry"}""", HttpStatusCode.Conflict)).deleteAccount("p", null))
            .isEqualTo(DeleteAccountNetworkResult.Conflict)
        assertThat(service(MockEngine { respond("", HttpStatusCode.TooManyRequests) }).deleteAccount("p", null))
            .isEqualTo(DeleteAccountNetworkResult.RateLimited)
    }
}
