package com.tenmilelabs.chefai.household.data.network

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import java.util.UUID

class HouseholdApiServiceTest {

    private lateinit var engine: MockEngine

    private fun service(engine: MockEngine): HouseholdApiService {
        this.engine = engine
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return HouseholdApiService(client)
    }

    private val lastRequest: HttpRequestData get() = engine.requestHistory.last()

    private fun jsonEngine(body: String, status: HttpStatusCode = HttpStatusCode.OK) = MockEngine {
        respond(content = body, status = status, headers = headersOf("Content-Type", ContentType.Application.Json.toString()))
    }

    private val householdJson = """{"id":"${UUID(0, 1)}","name":"The Test Kitchen","ownerId":"${UUID(0, 2)}","members":[]}"""

    @Test
    fun `createHousehold posts the name and returns the parsed household`() = runTest {
        val subject = service(jsonEngine(householdJson, HttpStatusCode.Created))

        val result = subject.createHousehold("The Test Kitchen")

        assertThat(result.name).isEqualTo("The Test Kitchen")
        assertThat(lastRequest.url.encodedPath).isEqualTo("/api/v1/households")
        assertThat(lastRequest.method.value).isEqualTo("POST")
    }

    @Test
    fun `createHousehold on a non-2xx throws HouseholdApiException`() = runTest {
        val subject = service(MockEngine { respondError(HttpStatusCode.BadRequest) })

        var thrown: Throwable? = null
        try {
            subject.createHousehold("")
        } catch (e: HouseholdApiException) {
            thrown = e
        }
        assertThat(thrown).isNotNull()
    }

    @Test
    fun `getMyHousehold on 200 returns the parsed household`() = runTest {
        val subject = service(jsonEngine(householdJson))

        assertThat(subject.getMyHousehold()?.name).isEqualTo("The Test Kitchen")
    }

    @Test
    fun `getMyHousehold on 404 returns null, not an exception`() = runTest {
        val subject = service(MockEngine { respondError(HttpStatusCode.NotFound) })

        assertThat(subject.getMyHousehold()).isNull()
    }

    @Test
    fun `previewInvite on 404 returns null — uniform for bad, expired, revoked or exhausted`() = runTest {
        val subject = service(MockEngine { respondError(HttpStatusCode.NotFound) })

        assertThat(subject.previewInvite("bad-token")).isNull()
    }

    @Test
    fun `previewInvite sends the token as a query parameter`() = runTest {
        val subject = service(jsonEngine("""{"householdName":"Kitchen","inviterDisplayName":"Chef"}"""))

        subject.previewInvite("secret-token")

        assertThat(lastRequest.url.parameters["token"]).isEqualTo("secret-token")
    }

    @Test
    fun `createInvite maps a 404 to InviteeNotFound rather than throwing`() = runTest {
        val subject = service(MockEngine { respondError(HttpStatusCode.NotFound) })

        val result = subject.createInvite(UUID.randomUUID(), "nobody@example.com", true, null, null)

        assertThat(result).isEqualTo(CreateInviteResult.InviteeNotFound)
    }

    @Test
    fun `createInvite on success returns the raw token and url`() = runTest {
        val subject = service(
            jsonEngine(
                """{"token":"raw-token","url":"https://chefai.app/invite?token=raw-token","expiresAt":1000,"singleUse":true,"maxUses":null}""",
                HttpStatusCode.Created,
            )
        )

        val result = subject.createInvite(UUID.randomUUID(), null, true, null, null) as CreateInviteResult.Success

        assertThat(result.token).isEqualTo("raw-token")
    }

    @Test
    fun `joinWithToken on 200 returns Success with the household`() = runTest {
        val subject = service(jsonEngine(householdJson))

        val result = subject.joinWithToken("token")

        assertThat(result).isInstanceOf(HouseholdJoinNetworkResult.Success::class.java)
    }

    @Test
    fun `joinWithToken on 404 maps to InvalidOrExpired`() = runTest {
        val subject = service(MockEngine { respondError(HttpStatusCode.NotFound) })

        assertThat(subject.joinWithToken("token")).isEqualTo(HouseholdJoinNetworkResult.InvalidOrExpired)
    }

    @Test
    fun `joinWithToken on 409 maps to AlreadyInAHousehold`() = runTest {
        val subject = service(MockEngine { respondError(HttpStatusCode.Conflict) })

        assertThat(subject.joinWithToken("token")).isEqualTo(HouseholdJoinNetworkResult.AlreadyInAHousehold)
    }

    @Test
    fun `joinWithToken on a 500 maps to Error, not a thrown exception`() = runTest {
        val subject = service(MockEngine { respondError(HttpStatusCode.InternalServerError) })

        assertThat(subject.joinWithToken("token")).isInstanceOf(HouseholdJoinNetworkResult.Error::class.java)
    }

    @Test
    fun `acceptInvite posts to the invite's accept path with no body`() = runTest {
        val inviteId = UUID.randomUUID()
        val subject = service(jsonEngine(householdJson))

        subject.acceptInvite(inviteId)

        assertThat(lastRequest.url.encodedPath).isEqualTo("/api/v1/households/invites/$inviteId/accept")
        assertThat(lastRequest.method.value).isEqualTo("POST")
    }

    @Test
    fun `declineInvite posts to the invite's decline path`() = runTest {
        val inviteId = UUID.randomUUID()
        val subject = service(MockEngine { respond("", HttpStatusCode.NoContent) })

        subject.declineInvite(inviteId)

        assertThat(lastRequest.url.encodedPath).isEqualTo("/api/v1/households/invites/$inviteId/decline")
    }

    @Test
    fun `removeMember deletes the member's own path`() = runTest {
        val householdId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val subject = service(MockEngine { respond("", HttpStatusCode.NoContent) })

        subject.removeMember(householdId, userId)

        assertThat(lastRequest.url.encodedPath).isEqualTo("/api/v1/households/$householdId/members/$userId")
        assertThat(lastRequest.method.value).isEqualTo("DELETE")
    }
}
