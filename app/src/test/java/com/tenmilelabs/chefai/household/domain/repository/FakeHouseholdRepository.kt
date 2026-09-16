package com.tenmilelabs.chefai.household.domain.repository

import com.tenmilelabs.chefai.household.domain.model.Household
import com.tenmilelabs.chefai.household.domain.model.HouseholdInvite
import com.tenmilelabs.chefai.household.domain.model.HouseholdInviteLink
import com.tenmilelabs.chefai.household.domain.model.HouseholdInvitePreview
import com.tenmilelabs.chefai.household.domain.model.HouseholdJoinOutcome
import com.tenmilelabs.chefai.household.domain.model.PendingHouseholdInvite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/** Settable-field fake — same convention as `FakeHouseholdNetworkDataSource`. */
class FakeHouseholdRepository : HouseholdRepository {

    private val householdFlow = MutableStateFlow<Household?>(null)
    var household: Household?
        get() = householdFlow.value
        set(value) { householdFlow.value = value }

    /** What [household] becomes once [refresh] runs — lets a test simulate the cache catching up. */
    var householdAfterRefresh: Household? = null

    var refreshCount = 0
        private set

    override fun observeMyHousehold(): Flow<Household?> = householdFlow

    override fun observePendingInvites(): Flow<List<PendingHouseholdInvite>> =
        MutableStateFlow(emptyList())

    override suspend fun refresh(): Result<Unit> {
        refreshCount++
        household = householdAfterRefresh
        return Result.success(Unit)
    }

    override suspend fun createHousehold(name: String): Result<Household> =
        Result.failure(UnsupportedOperationException())

    override suspend fun renameHousehold(name: String): Result<Household> =
        Result.failure(UnsupportedOperationException())

    override suspend fun deleteHousehold(): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override suspend fun leaveHousehold(): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override suspend fun removeMember(userId: UUID): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override suspend fun createInviteLink(): Result<HouseholdInviteLink> =
        Result.failure(UnsupportedOperationException())

    override suspend fun inviteByEmail(email: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override suspend fun previewInvite(token: String): Result<HouseholdInvitePreview> =
        Result.failure(UnsupportedOperationException())

    override suspend fun joinWithToken(token: String): HouseholdJoinOutcome =
        HouseholdJoinOutcome.NetworkError

    override suspend fun acceptInvite(inviteId: UUID): HouseholdJoinOutcome =
        HouseholdJoinOutcome.NetworkError

    override suspend fun declineInvite(inviteId: UUID): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override suspend fun listOutstandingInvites(): Result<List<HouseholdInvite>> =
        Result.failure(UnsupportedOperationException())

    override suspend fun revokeInvite(inviteId: UUID): Result<Unit> =
        Result.failure(UnsupportedOperationException())
}
