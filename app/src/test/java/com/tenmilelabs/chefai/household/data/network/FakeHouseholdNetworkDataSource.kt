package com.tenmilelabs.chefai.household.data.network

import com.tenmilelabs.chefai.household.data.network.dto.HouseholdResponse
import com.tenmilelabs.chefai.household.data.network.dto.InvitePreviewResponse
import com.tenmilelabs.chefai.household.data.network.dto.InviteSummaryResponse
import java.util.UUID

/** Each field is a settable hook/queue the test configures before calling the repository. */
class FakeHouseholdNetworkDataSource : HouseholdNetworkDataSource {
    var household: HouseholdResponse? = null
    var pendingInvites: List<InviteSummaryResponse> = emptyList()
    var outstandingInvites: List<InviteSummaryResponse> = emptyList()
    var invitePreview: InvitePreviewResponse? = null
    var createInviteResult: CreateInviteResult = CreateInviteResult.InviteeNotFound
    var joinResult: HouseholdJoinNetworkResult = HouseholdJoinNetworkResult.InvalidOrExpired
    var acceptResult: HouseholdJoinNetworkResult = HouseholdJoinNetworkResult.InvalidOrExpired
    var throwOnNextCall: Exception? = null

    var lastRenameName: String? = null
    var lastRemovedMember: UUID? = null
    var lastLeftHouseholdId: UUID? = null
    var lastDeletedHouseholdId: UUID? = null
    var lastRevokedInvite: UUID? = null
    var lastDeclinedInvite: UUID? = null

    private fun maybeThrow() {
        throwOnNextCall?.let { throwOnNextCall = null; throw it }
    }

    override suspend fun createHousehold(name: String): HouseholdResponse {
        maybeThrow()
        return household ?: error("no household configured")
    }

    override suspend fun getMyHousehold(): HouseholdResponse? {
        maybeThrow()
        return household
    }

    override suspend fun renameHousehold(householdId: UUID, name: String): HouseholdResponse {
        maybeThrow()
        lastRenameName = name
        return household ?: error("no household configured")
    }

    override suspend fun deleteHousehold(householdId: UUID) {
        maybeThrow()
        lastDeletedHouseholdId = householdId
    }

    override suspend fun removeMember(householdId: UUID, userId: UUID) {
        maybeThrow()
        lastRemovedMember = userId
    }

    override suspend fun leaveHousehold(householdId: UUID) {
        maybeThrow()
        lastLeftHouseholdId = householdId
    }

    override suspend fun createInvite(
        householdId: UUID,
        inviteeEmail: String?,
        singleUse: Boolean,
        maxUses: Int?,
        expiresInHours: Long?,
    ): CreateInviteResult {
        maybeThrow()
        return createInviteResult
    }

    override suspend fun listOutstandingInvites(householdId: UUID): List<InviteSummaryResponse> {
        maybeThrow()
        return outstandingInvites
    }

    override suspend fun revokeInvite(householdId: UUID, inviteId: UUID) {
        maybeThrow()
        lastRevokedInvite = inviteId
    }

    override suspend fun previewInvite(token: String): InvitePreviewResponse? {
        maybeThrow()
        return invitePreview
    }

    override suspend fun joinWithToken(token: String): HouseholdJoinNetworkResult {
        maybeThrow()
        return joinResult
    }

    override suspend fun listPendingInvites(): List<InviteSummaryResponse> {
        maybeThrow()
        return pendingInvites
    }

    override suspend fun acceptInvite(inviteId: UUID): HouseholdJoinNetworkResult {
        maybeThrow()
        return acceptResult
    }

    override suspend fun declineInvite(inviteId: UUID) {
        maybeThrow()
        lastDeclinedInvite = inviteId
    }
}
