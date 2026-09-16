package com.tenmilelabs.chefai.household.data.repository

import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.data.local.room.TransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.HouseholdDao
import com.tenmilelabs.chefai.core.data.local.room.dao.MealPlanDao
import com.tenmilelabs.chefai.household.data.mapper.toDomain
import com.tenmilelabs.chefai.household.data.mapper.toEntity
import com.tenmilelabs.chefai.household.data.mapper.toPendingInviteEntity
import com.tenmilelabs.chefai.household.data.network.CreateInviteResult
import com.tenmilelabs.chefai.household.data.network.HouseholdApiException
import com.tenmilelabs.chefai.household.data.network.HouseholdJoinNetworkResult
import com.tenmilelabs.chefai.household.data.network.HouseholdNetworkDataSource
import com.tenmilelabs.chefai.household.data.network.dto.HouseholdResponse
import com.tenmilelabs.chefai.household.domain.model.Household
import com.tenmilelabs.chefai.household.domain.model.HouseholdInvite
import com.tenmilelabs.chefai.household.domain.model.HouseholdInviteLink
import com.tenmilelabs.chefai.household.domain.model.HouseholdInvitePreview
import com.tenmilelabs.chefai.household.domain.model.HouseholdJoinOutcome
import com.tenmilelabs.chefai.household.domain.model.PendingHouseholdInvite
import com.tenmilelabs.chefai.household.domain.repository.HouseholdRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct REST orchestration over [HouseholdNetworkDataSource] plus the read-through cache in
 * [HouseholdDao] — see [HouseholdRepository]'s doc for why this is not a dirty-queue repository.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultHouseholdRepository @Inject constructor(
    private val networkDataSource: HouseholdNetworkDataSource,
    private val householdDao: HouseholdDao,
    private val mealPlanDao: MealPlanDao,
    private val transactionRunner: TransactionRunner,
    private val sessionManager: SessionManager,
) : HouseholdRepository {

    override fun observeMyHousehold(): Flow<Household?> =
        householdDao.observeCachedHousehold().flatMapLatest { entity ->
            if (entity == null) {
                flowOf(null)
            } else {
                householdDao.observeMembers(entity.uuid).map { members -> entity.toDomain(members) }
            }
        }

    override fun observePendingInvites(): Flow<List<PendingHouseholdInvite>> =
        householdDao.observePendingInvites().map { entities -> entities.map { it.toDomain() } }

    override suspend fun refresh(): Result<Unit> = resultOf {
        val household = networkDataSource.getMyHousehold()
        val pendingInvites = networkDataSource.listPendingInvites()

        transactionRunner {
            if (household == null) {
                householdDao.getCachedHouseholdId()?.let { householdDao.deleteHousehold(it) }
            } else {
                cacheHousehold(household)
            }

            // Diff rather than blind-replace: an invite the server no longer lists (accepted,
            // declined, or revoked elsewhere) must disappear locally too.
            val freshIds = pendingInvites.map { UUID.fromString(it.id) }.toSet()
            val staleIds = householdDao.getCachedInviteIds().toSet() - freshIds
            staleIds.forEach { householdDao.removeInvite(it) }
            householdDao.upsertInvites(pendingInvites.map { it.toPendingInviteEntity() })
        }
    }

    override suspend fun createHousehold(name: String): Result<Household> = resultOf {
        val response = networkDataSource.createHousehold(name)
        cacheHousehold(response)
        response.toDomain()
    }

    override suspend fun renameHousehold(name: String): Result<Household> = resultOf {
        val id = requireCachedHouseholdId()
        val response = networkDataSource.renameHousehold(id, name)
        cacheHousehold(response)
        response.toDomain()
    }

    override suspend fun deleteHousehold(): Result<Unit> = resultOf {
        val id = requireCachedHouseholdId()
        networkDataSource.deleteHousehold(id)
        householdDao.deleteHousehold(id)
    }

    override suspend fun leaveHousehold(): Result<Unit> = resultOf {
        val id = requireCachedHouseholdId()
        val userId = requireCurrentUserId()
        networkDataSource.leaveHousehold(id)
        // Do NOT reset the sync cursor here — the backend bumps server_updated_at on household
        // rows at accept time and referenced recipes arrive via the gap clause, so a client-side
        // reset would only force a needless full re-pull (ADR-014 §0.6).
        transactionRunner {
            mealPlanDao.deleteHouseholdPlansNotOwnedBy(id, keepOwnedBy = userId)
            mealPlanDao.clearHouseholdLinkForOwnPlans(id, userId)
            householdDao.deleteHousehold(id)
        }
    }

    override suspend fun removeMember(userId: UUID): Result<Unit> = resultOf {
        val id = requireCachedHouseholdId()
        networkDataSource.removeMember(id, userId)
        // Re-fetch rather than guess the new roster locally — a plain GET, not a hot path.
        val fresh = networkDataSource.getMyHousehold()
        if (fresh != null) cacheHousehold(fresh) else householdDao.deleteHousehold(id)
    }

    override suspend fun createInviteLink(): Result<HouseholdInviteLink> = resultOf {
        val id = requireCachedHouseholdId()
        val result = networkDataSource.createInvite(
            householdId = id, inviteeEmail = null, singleUse = true, maxUses = null, expiresInHours = null,
        )
        when (result) {
            is CreateInviteResult.Success -> result.toDomain()
            CreateInviteResult.InviteeNotFound ->
                throw IllegalStateException("unexpected InviteeNotFound with no inviteeEmail sent")
        }
    }

    override suspend fun inviteByEmail(email: String): Result<Unit> = resultOf {
        val id = requireCachedHouseholdId()
        val result = networkDataSource.createInvite(
            householdId = id, inviteeEmail = email, singleUse = true, maxUses = null, expiresInHours = null,
        )
        when (result) {
            is CreateInviteResult.Success -> Unit
            CreateInviteResult.InviteeNotFound ->
                throw HouseholdApiException("No account found for $email", 404)
        }
    }

    override suspend fun previewInvite(token: String): Result<HouseholdInvitePreview> = resultOf {
        networkDataSource.previewInvite(token)?.toDomain()
            ?: throw HouseholdApiException("Invite not found or no longer usable", 404)
    }

    override suspend fun joinWithToken(token: String): HouseholdJoinOutcome =
        networkDataSource.joinWithToken(token).toOutcome()

    override suspend fun acceptInvite(inviteId: UUID): HouseholdJoinOutcome {
        val outcome = networkDataSource.acceptInvite(inviteId).toOutcome()
        if (outcome is HouseholdJoinOutcome.Joined) householdDao.removeInvite(inviteId)
        return outcome
    }

    override suspend fun declineInvite(inviteId: UUID): Result<Unit> = resultOf {
        networkDataSource.declineInvite(inviteId)
        householdDao.removeInvite(inviteId)
    }

    override suspend fun listOutstandingInvites(): Result<List<HouseholdInvite>> = resultOf {
        val id = requireCachedHouseholdId()
        networkDataSource.listOutstandingInvites(id).map { it.toDomain() }
    }

    override suspend fun revokeInvite(inviteId: UUID): Result<Unit> = resultOf {
        val id = requireCachedHouseholdId()
        networkDataSource.revokeInvite(id, inviteId)
    }

    private suspend fun requireCachedHouseholdId(): UUID =
        householdDao.getCachedHouseholdId() ?: throw IllegalStateException("No cached household")

    private fun requireCurrentUserId(): UUID =
        sessionManager.getCurrentUserId() ?: throw IllegalStateException("No authenticated user")

    private suspend fun cacheHousehold(response: HouseholdResponse) {
        val householdId = UUID.fromString(response.id)
        transactionRunner {
            householdDao.upsertHousehold(response.toEntity(updatedAt = System.currentTimeMillis()))
            householdDao.clearMembers(householdId)
            householdDao.upsertMembers(response.members.map { it.toEntity(householdId) })
        }
    }

    private suspend fun HouseholdJoinNetworkResult.toOutcome(): HouseholdJoinOutcome = when (this) {
        is HouseholdJoinNetworkResult.Success -> {
            cacheHousehold(household)
            HouseholdJoinOutcome.Joined(household.toDomain())
        }
        HouseholdJoinNetworkResult.InvalidOrExpired -> HouseholdJoinOutcome.InvalidOrExpired
        HouseholdJoinNetworkResult.AlreadyInAHousehold -> HouseholdJoinOutcome.AlreadyInAHousehold
        is HouseholdJoinNetworkResult.Error -> HouseholdJoinOutcome.NetworkError
    }
}

/** [kotlin.runCatching] would also swallow [CancellationException] — every other API service in
 *  this codebase (e.g. RecipeDetailApiService) explicitly rethrows it before mapping to a result,
 *  which this mirrors at the repository layer instead of the network layer. */
private suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
