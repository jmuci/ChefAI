package com.tenmilelabs.chefai.household.domain.repository

import com.tenmilelabs.chefai.household.domain.model.Household
import com.tenmilelabs.chefai.household.domain.model.HouseholdInvite
import com.tenmilelabs.chefai.household.domain.model.HouseholdInviteLink
import com.tenmilelabs.chefai.household.domain.model.HouseholdInvitePreview
import com.tenmilelabs.chefai.household.domain.model.HouseholdJoinOutcome
import com.tenmilelabs.chefai.household.domain.model.PendingHouseholdInvite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/** Settable-field fake — same convention as `FakeHouseholdNetworkDataSource`. */
class FakeHouseholdRepository : HouseholdRepository {

    private val householdFlow = MutableStateFlow<Household?>(null)
    var household: Household?
        get() = householdFlow.value
        set(value) { householdFlow.value = value }

    private val pendingInvitesFlow = MutableStateFlow<List<PendingHouseholdInvite>>(emptyList())
    var pendingInvites: List<PendingHouseholdInvite>
        get() = pendingInvitesFlow.value
        set(value) { pendingInvitesFlow.value = value }

    /** What [household] becomes once [refresh] runs — lets a test simulate the cache catching up. */
    var householdAfterRefresh: Household? = null
    var refreshResult: Result<Unit> = Result.success(Unit)

    /**
     * Sets [household] AND [householdAfterRefresh] together. Most callers under test (e.g.
     * `HouseholdViewModel.init`) trigger a [refresh] as soon as they're created, and a successful
     * [refresh] always replaces [household] with [householdAfterRefresh] — so seeding only
     * [household] gets silently wiped back to `null` the moment that refresh runs. Use this instead
     * of the [household] setter directly whenever the seeded value should still be there after that.
     */
    fun seedHousehold(value: Household?) {
        household = value
        householdAfterRefresh = value
    }

    var createHouseholdResult: Result<Household> = Result.failure(UnsupportedOperationException())
    var leaveHouseholdResult: Result<Unit> = Result.failure(UnsupportedOperationException())
    var removeMemberResult: Result<Unit> = Result.failure(UnsupportedOperationException())
    var createInviteLinkResult: Result<HouseholdInviteLink> = Result.failure(UnsupportedOperationException())
    var inviteByEmailResult: Result<Unit> = Result.failure(UnsupportedOperationException())
    var acceptInviteResult: HouseholdJoinOutcome = HouseholdJoinOutcome.NetworkError
    var declineInviteResult: Result<Unit> = Result.success(Unit)
    var previewInviteResult: Result<HouseholdInvitePreview> = Result.failure(UnsupportedOperationException())
    var joinWithTokenResult: HouseholdJoinOutcome = HouseholdJoinOutcome.NetworkError
    var listOutstandingInvitesResult: Result<List<HouseholdInvite>> = Result.success(emptyList())
    var revokeInviteResult: Result<Unit> = Result.failure(UnsupportedOperationException())

    var refreshCount = 0
        private set

    /**
     * One gate per [refresh] call, consumed in call order — lets a test hold a call suspended
     * (simulating an in-flight network request) while later calls run and complete around it, to
     * reproduce a stale response landing after a newer one already has. A call made with no queued
     * gate proceeds immediately, so most tests can ignore this entirely.
     */
    private val refreshGates = ArrayDeque<CompletableDeferred<Unit>>()

    fun enqueueRefreshGate(): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { refreshGates.addLast(it) }
    var lastCreatedHouseholdName: String? = null
        private set
    var lastRemovedMemberId: UUID? = null
        private set
    var lastInvitedEmail: String? = null
        private set
    var lastAcceptedInviteId: UUID? = null
        private set
    var lastDeclinedInviteId: UUID? = null
        private set
    var lastPreviewedToken: String? = null
        private set
    var lastJoinedToken: String? = null
        private set
    var lastRevokedInviteId: UUID? = null
        private set

    override fun observeMyHousehold(): Flow<Household?> = householdFlow

    override fun observePendingInvites(): Flow<List<PendingHouseholdInvite>> = pendingInvitesFlow

    override suspend fun refresh(): Result<Unit> {
        refreshCount++
        // Captured now, before any gate suspends us — mirrors a real response carrying whatever
        // data the server had when the REQUEST was made, not whenever this call happens to finish.
        val resultToApply = householdAfterRefresh
        if (refreshGates.isNotEmpty()) refreshGates.removeFirst().await()
        if (refreshResult.isSuccess) household = resultToApply
        return refreshResult
    }

    override suspend fun clearLocalCache() {
        household = null
        pendingInvites = emptyList()
    }

    override suspend fun createHousehold(name: String): Result<Household> {
        lastCreatedHouseholdName = name
        createHouseholdResult.onSuccess { household = it }
        return createHouseholdResult
    }

    override suspend fun renameHousehold(name: String): Result<Household> =
        Result.failure(UnsupportedOperationException())

    override suspend fun deleteHousehold(): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override suspend fun leaveHousehold(): Result<Unit> {
        leaveHouseholdResult.onSuccess { household = null }
        return leaveHouseholdResult
    }

    override suspend fun removeMember(userId: UUID): Result<Unit> {
        lastRemovedMemberId = userId
        return removeMemberResult
    }

    override suspend fun createInviteLink(): Result<HouseholdInviteLink> = createInviteLinkResult

    override suspend fun inviteByEmail(email: String): Result<Unit> {
        lastInvitedEmail = email
        return inviteByEmailResult
    }

    override suspend fun previewInvite(token: String): Result<HouseholdInvitePreview> {
        lastPreviewedToken = token
        return previewInviteResult
    }

    override suspend fun joinWithToken(token: String): HouseholdJoinOutcome {
        lastJoinedToken = token
        return joinWithTokenResult
    }

    override suspend fun acceptInvite(inviteId: UUID): HouseholdJoinOutcome {
        lastAcceptedInviteId = inviteId
        return acceptInviteResult
    }

    override suspend fun declineInvite(inviteId: UUID): Result<Unit> {
        lastDeclinedInviteId = inviteId
        return declineInviteResult
    }

    override suspend fun listOutstandingInvites(): Result<List<HouseholdInvite>> =
        listOutstandingInvitesResult

    override suspend fun revokeInvite(inviteId: UUID): Result<Unit> {
        lastRevokedInviteId = inviteId
        return revokeInviteResult
    }
}
