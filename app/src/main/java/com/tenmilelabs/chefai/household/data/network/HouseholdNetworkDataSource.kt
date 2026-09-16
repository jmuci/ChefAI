package com.tenmilelabs.chefai.household.data.network

import com.tenmilelabs.chefai.household.data.network.dto.HouseholdResponse
import com.tenmilelabs.chefai.household.data.network.dto.InvitePreviewResponse
import com.tenmilelabs.chefai.household.data.network.dto.InviteSummaryResponse
import java.util.UUID

/** Never thrown for a business-outcome 404/409 on join/accept — see [HouseholdJoinNetworkResult].
 *  Every other method throws [HouseholdApiException] on a non-2xx response. */
interface HouseholdNetworkDataSource {
    suspend fun createHousehold(name: String): HouseholdResponse

    /** `null` for a 404 — the caller has no household, not an error. */
    suspend fun getMyHousehold(): HouseholdResponse?

    suspend fun renameHousehold(householdId: UUID, name: String): HouseholdResponse
    suspend fun deleteHousehold(householdId: UUID)
    suspend fun removeMember(householdId: UUID, userId: UUID)
    suspend fun leaveHousehold(householdId: UUID)

    suspend fun createInvite(
        householdId: UUID,
        inviteeEmail: String?,
        singleUse: Boolean,
        maxUses: Int?,
        expiresInHours: Long?,
    ): CreateInviteResult

    suspend fun listOutstandingInvites(householdId: UUID): List<InviteSummaryResponse>
    suspend fun revokeInvite(householdId: UUID, inviteId: UUID)

    /** `null` for a 404 — bad/expired/revoked/exhausted token, uniformly (enumeration resistance
     *  on the server side; this side just can't tell them apart, by design). */
    suspend fun previewInvite(token: String): InvitePreviewResponse?

    suspend fun joinWithToken(token: String): HouseholdJoinNetworkResult
    suspend fun listPendingInvites(): List<InviteSummaryResponse>
    suspend fun acceptInvite(inviteId: UUID): HouseholdJoinNetworkResult
    suspend fun declineInvite(inviteId: UUID)
}

/** [CreateHouseholdRequest.inviteeEmail] resolving to no account is a 404 the caller should show
 *  distinctly from a generic failure — see `HouseholdRepository.inviteByEmail`. */
sealed interface CreateInviteResult {
    data class Success(val token: String, val url: String, val expiresAt: Long) : CreateInviteResult
    data object InviteeNotFound : CreateInviteResult
}

/** Mirrors [com.tenmilelabs.chefai.household.domain.model.HouseholdJoinOutcome] one layer down —
 *  see that type's doc for why transient failures are folded in rather than left to a thrown
 *  exception. */
sealed interface HouseholdJoinNetworkResult {
    data class Success(val household: HouseholdResponse) : HouseholdJoinNetworkResult
    data object InvalidOrExpired : HouseholdJoinNetworkResult
    data object AlreadyInAHousehold : HouseholdJoinNetworkResult
    data class Error(val message: String) : HouseholdJoinNetworkResult
}

data class HouseholdApiException(override val message: String, val statusCode: Int) : Exception(message)
