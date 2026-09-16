package com.tenmilelabs.chefai.household.data.network

import com.tenmilelabs.chefai.BuildConfig
import com.tenmilelabs.chefai.household.data.network.dto.CreateHouseholdRequest
import com.tenmilelabs.chefai.household.data.network.dto.CreateInviteRequest
import com.tenmilelabs.chefai.household.data.network.dto.CreateInviteResponse
import com.tenmilelabs.chefai.household.data.network.dto.HouseholdResponse
import com.tenmilelabs.chefai.household.data.network.dto.InvitePreviewResponse
import com.tenmilelabs.chefai.household.data.network.dto.InviteSummaryResponse
import com.tenmilelabs.chefai.household.data.network.dto.JoinHouseholdRequest
import com.tenmilelabs.chefai.household.data.network.dto.RenameHouseholdRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct REST calls, no dirty queue — see [com.tenmilelabs.chefai.household.domain.repository
 * .HouseholdRepository]'s doc for why. `AuthInterceptor` (installed on the shared [HttpClient],
 * see NetworkModule.kt) attaches `Authorization` automatically; every endpoint here requires it
 * except [previewInvite], which the backend mounts with optional auth.
 */
@Singleton
class HouseholdApiService @Inject constructor(
    private val client: HttpClient
) : HouseholdNetworkDataSource {

    private companion object {
        val BASE_URL = "${BuildConfig.API_BASE_URL}/api/v1/households"
    }

    override suspend fun createHousehold(name: String): HouseholdResponse {
        val response = client.post(BASE_URL) {
            contentType(ContentType.Application.Json)
            setBody(CreateHouseholdRequest(name))
            expectSuccess = false
        }
        return response.orThrow("Create household failed")
    }

    override suspend fun getMyHousehold(): HouseholdResponse? {
        val response = client.get("$BASE_URL/me") { expectSuccess = false }
        if (response.status == HttpStatusCode.NotFound) return null
        return response.orThrow("Fetch household failed")
    }

    override suspend fun renameHousehold(householdId: UUID, name: String): HouseholdResponse {
        val response = client.patch("$BASE_URL/$householdId") {
            contentType(ContentType.Application.Json)
            setBody(RenameHouseholdRequest(name))
            expectSuccess = false
        }
        return response.orThrow("Rename household failed")
    }

    override suspend fun deleteHousehold(householdId: UUID) {
        val response = client.delete("$BASE_URL/$householdId") { expectSuccess = false }
        response.requireSuccess("Delete household failed")
    }

    override suspend fun removeMember(householdId: UUID, userId: UUID) {
        val response = client.delete("$BASE_URL/$householdId/members/$userId") { expectSuccess = false }
        response.requireSuccess("Remove member failed")
    }

    override suspend fun leaveHousehold(householdId: UUID) {
        val response = client.post("$BASE_URL/$householdId/members/me/leave") { expectSuccess = false }
        response.requireSuccess("Leave household failed")
    }

    override suspend fun createInvite(
        householdId: UUID,
        inviteeEmail: String?,
        singleUse: Boolean,
        maxUses: Int?,
        expiresInHours: Long?,
    ): CreateInviteResult {
        val response = client.post("$BASE_URL/$householdId/invites") {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(inviteeEmail, singleUse, maxUses, expiresInHours))
            expectSuccess = false
        }
        if (response.status == HttpStatusCode.NotFound) return CreateInviteResult.InviteeNotFound
        val body: CreateInviteResponse = response.orThrow("Create invite failed")
        return CreateInviteResult.Success(token = body.token, url = body.url, expiresAt = body.expiresAt)
    }

    override suspend fun listOutstandingInvites(householdId: UUID): List<InviteSummaryResponse> {
        val response = client.get("$BASE_URL/$householdId/invites") { expectSuccess = false }
        return response.orThrow("List invites failed")
    }

    override suspend fun revokeInvite(householdId: UUID, inviteId: UUID) {
        val response = client.delete("$BASE_URL/$householdId/invites/$inviteId") { expectSuccess = false }
        response.requireSuccess("Revoke invite failed")
    }

    override suspend fun previewInvite(token: String): InvitePreviewResponse? {
        val response = client.get("$BASE_URL/invites/preview") {
            parameter("token", token)
            expectSuccess = false
        }
        if (response.status == HttpStatusCode.NotFound) return null
        return response.orThrow("Preview invite failed")
    }

    override suspend fun joinWithToken(token: String): HouseholdJoinNetworkResult {
        val response = client.post("$BASE_URL/join") {
            contentType(ContentType.Application.Json)
            setBody(JoinHouseholdRequest(token))
            expectSuccess = false
        }
        return response.toJoinResult()
    }

    override suspend fun listPendingInvites(): List<InviteSummaryResponse> {
        val response = client.get("$BASE_URL/invites/pending") { expectSuccess = false }
        return response.orThrow("List pending invites failed")
    }

    override suspend fun acceptInvite(inviteId: UUID): HouseholdJoinNetworkResult {
        val response = client.post("$BASE_URL/invites/$inviteId/accept") { expectSuccess = false }
        return response.toJoinResult()
    }

    override suspend fun declineInvite(inviteId: UUID) {
        val response = client.post("$BASE_URL/invites/$inviteId/decline") { expectSuccess = false }
        response.requireSuccess("Decline invite failed")
    }

    private suspend fun HttpResponse.toJoinResult(): HouseholdJoinNetworkResult =
        when (status) {
            HttpStatusCode.NotFound -> HouseholdJoinNetworkResult.InvalidOrExpired
            HttpStatusCode.Conflict -> HouseholdJoinNetworkResult.AlreadyInAHousehold
            else -> if (status.isSuccess()) {
                HouseholdJoinNetworkResult.Success(body())
            } else {
                HouseholdJoinNetworkResult.Error("Join household failed: $status")
            }
        }

    private suspend inline fun <reified T> HttpResponse.orThrow(message: String): T {
        requireSuccess(message)
        return body()
    }

    private fun HttpResponse.requireSuccess(message: String) {
        if (!status.isSuccess()) throw HouseholdApiException("$message: $status", status.value)
    }
}
