package com.tenmilelabs.chefai.household.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.FakeTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.HouseholdEntity
import com.tenmilelabs.chefai.core.data.local.room.HouseholdInviteEntity
import com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeHouseholdDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeMealPlanDao
import com.tenmilelabs.chefai.household.data.network.CreateInviteResult
import com.tenmilelabs.chefai.household.data.network.FakeHouseholdNetworkDataSource
import com.tenmilelabs.chefai.household.data.network.HouseholdJoinNetworkResult
import com.tenmilelabs.chefai.household.data.network.dto.HouseholdResponse
import com.tenmilelabs.chefai.household.data.network.dto.InviteSummaryResponse
import com.tenmilelabs.chefai.household.data.network.dto.MemberResponse
import com.tenmilelabs.chefai.household.domain.model.HouseholdJoinOutcome
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

class DefaultHouseholdRepositoryTest {

    private val network = FakeHouseholdNetworkDataSource()
    private lateinit var dao: FakeHouseholdDao
    private lateinit var mealPlanDao: FakeMealPlanDao
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: DefaultHouseholdRepository

    private val householdId = UUID.randomUUID()
    private val ownerId = UUID.randomUUID()
    private val currentUserId = UUID.randomUUID()

    private fun householdResponse(members: List<MemberResponse> = emptyList()) = HouseholdResponse(
        id = householdId.toString(), name = "The Test Kitchen", ownerId = ownerId.toString(), members = members,
    )

    @Before
    fun setUp() {
        dao = FakeHouseholdDao()
        mealPlanDao = FakeMealPlanDao()
        sessionManager = mockk<SessionManager>()
        every { sessionManager.getCurrentUserId() } returns currentUserId
        repository = DefaultHouseholdRepository(
            network, dao, mealPlanDao, FakeTransactionRunner(), sessionManager,
        )
    }

    @Test
    fun `observeMyHousehold emits null when nothing is cached`() = runTest {
        repository.observeMyHousehold().test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `refresh with a household caches it and members, observable afterward`() = runTest {
        network.household = householdResponse(
            members = listOf(MemberResponse(ownerId.toString(), "Chef Owner", "", "OWNER", 0L))
        )

        val result = repository.refresh()

        assertThat(result.isSuccess).isTrue()
        repository.observeMyHousehold().test {
            val household = awaitItem()
            assertThat(household?.uuid).isEqualTo(householdId)
            assertThat(household?.members).hasSize(1)
        }
    }

    @Test
    fun `refresh with no household clears a previously cached one`() = runTest {
        dao.upsertHousehold(HouseholdEntity(householdId, "Stale", ownerId, 0L, 0L))
        network.household = null

        repository.refresh()

        assertThat(dao.getCachedHouseholdId()).isNull()
    }

    @Test
    fun `refresh drops a locally cached invite the server no longer lists`() = runTest {
        val staleInviteId = UUID.randomUUID()
        dao.upsertInvites(listOf(HouseholdInviteEntity(staleInviteId, householdId, 0L, 0L)))
        network.pendingInvites = emptyList()

        repository.refresh()

        assertThat(dao.getCachedInviteIds()).isEmpty()
    }

    @Test
    fun `refresh upserts the server's current pending invites`() = runTest {
        val inviteId = UUID.randomUUID()
        network.pendingInvites = listOf(
            InviteSummaryResponse(inviteId.toString(), householdId.toString(), null, true, null, 0, 999L, 1L)
        )

        repository.refresh()

        assertThat(dao.getCachedInviteIds()).containsExactly(inviteId)
    }

    @Test
    fun `createHousehold caches the result and returns it as domain`() = runTest {
        network.household = householdResponse()

        val result = repository.createHousehold("The Test Kitchen")

        assertThat(result.isSuccess).isTrue()
        assertThat(dao.getCachedHouseholdId()).isEqualTo(householdId)
    }

    @Test
    fun `mutating methods fail cleanly when no household is cached`() = runTest {
        val result = repository.leaveHousehold()

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `leaveHousehold clears the local cache on success`() = runTest {
        dao.upsertHousehold(HouseholdEntity(householdId, "The Test Kitchen", ownerId, 0L, 0L))

        val result = repository.leaveHousehold()

        assertThat(result.isSuccess).isTrue()
        assertThat(network.lastLeftHouseholdId).isEqualTo(householdId)
        assertThat(dao.getCachedHouseholdId()).isNull()
    }

    @Test
    fun `leaveHousehold hard-deletes shared plans this device doesn't own`() = runTest {
        dao.upsertHousehold(HouseholdEntity(householdId, "The Test Kitchen", ownerId, 0L, 0L))
        val someoneElsesPlan = mealPlan(userId = ownerId, householdId = householdId)
        mealPlanDao.upsertMealPlan(someoneElsesPlan)

        repository.leaveHousehold()

        assertThat(mealPlanDao.getMealPlanById(someoneElsesPlan.uuid)).isNull()
    }

    @Test
    fun `leaveHousehold un-shares the leaving user's own plans without deleting them`() = runTest {
        dao.upsertHousehold(HouseholdEntity(householdId, "The Test Kitchen", ownerId, 0L, 0L))
        val ownPlan = mealPlan(userId = currentUserId, householdId = householdId)
        mealPlanDao.upsertMealPlan(ownPlan)

        repository.leaveHousehold()

        val stored = mealPlanDao.getMealPlanById(ownPlan.uuid)
        assertThat(stored).isNotNull()
        assertThat(stored?.householdId).isNull()
    }

    @Test
    fun `leaveHousehold doesn't touch a personal plan or another household's plan`() = runTest {
        dao.upsertHousehold(HouseholdEntity(householdId, "The Test Kitchen", ownerId, 0L, 0L))
        val personalPlan = mealPlan(userId = currentUserId, householdId = null)
        val otherHouseholdPlan = mealPlan(userId = ownerId, householdId = UUID.randomUUID())
        mealPlanDao.upsertMealPlan(personalPlan)
        mealPlanDao.upsertMealPlan(otherHouseholdPlan)

        repository.leaveHousehold()

        assertThat(mealPlanDao.getMealPlanById(personalPlan.uuid)).isEqualTo(personalPlan)
        assertThat(mealPlanDao.getMealPlanById(otherHouseholdPlan.uuid)).isEqualTo(otherHouseholdPlan)
    }

    @Test
    fun `leaveHousehold fails cleanly with no authenticated user`() = runTest {
        dao.upsertHousehold(HouseholdEntity(householdId, "The Test Kitchen", ownerId, 0L, 0L))
        every { sessionManager.getCurrentUserId() } returns null

        val result = repository.leaveHousehold()

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `inviteByEmail maps InviteeNotFound to a failure`() = runTest {
        dao.upsertHousehold(HouseholdEntity(householdId, "The Test Kitchen", ownerId, 0L, 0L))
        network.createInviteResult = CreateInviteResult.InviteeNotFound

        val result = repository.inviteByEmail("nobody@example.com")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `createInviteLink returns the link on success`() = runTest {
        dao.upsertHousehold(HouseholdEntity(householdId, "The Test Kitchen", ownerId, 0L, 0L))
        network.createInviteResult = CreateInviteResult.Success("raw-token", "https://chefai.app?token=raw-token", 999L)

        val result = repository.createInviteLink()

        assertThat(result.getOrNull()?.manualCode).isEqualTo("raw-token")
    }

    @Test
    fun `joinWithToken on success caches the joined household`() = runTest {
        network.joinResult = HouseholdJoinNetworkResult.Success(householdResponse())

        val outcome = repository.joinWithToken("token")

        assertThat(outcome).isInstanceOf(HouseholdJoinOutcome.Joined::class.java)
        assertThat(dao.getCachedHouseholdId()).isEqualTo(householdId)
    }

    @Test
    fun `joinWithToken on a network error maps to NetworkError, not a thrown exception`() = runTest {
        network.joinResult = HouseholdJoinNetworkResult.Error("boom")

        val outcome = repository.joinWithToken("token")

        assertThat(outcome).isEqualTo(HouseholdJoinOutcome.NetworkError)
    }

    @Test
    fun `acceptInvite on success removes the accepted invite from the pending cache`() = runTest {
        val inviteId = UUID.randomUUID()
        dao.upsertInvites(listOf(HouseholdInviteEntity(inviteId, householdId, 0L, 0L)))
        network.acceptResult = HouseholdJoinNetworkResult.Success(householdResponse())

        repository.acceptInvite(inviteId)

        assertThat(dao.getCachedInviteIds()).doesNotContain(inviteId)
    }

    @Test
    fun `declineInvite removes the invite from the local cache on success`() = runTest {
        val inviteId = UUID.randomUUID()
        dao.upsertInvites(listOf(HouseholdInviteEntity(inviteId, householdId, 0L, 0L)))

        repository.declineInvite(inviteId)

        assertThat(dao.getCachedInviteIds()).doesNotContain(inviteId)
    }

    @Test
    fun `a thrown CancellationException propagates rather than becoming a failed Result`() = runTest {
        dao.upsertHousehold(HouseholdEntity(householdId, "The Test Kitchen", ownerId, 0L, 0L))
        network.throwOnNextCall = kotlinx.coroutines.CancellationException("cancelled")

        try {
            repository.leaveHousehold()
            throw AssertionError("expected CancellationException to propagate")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // expected
        }
    }

    private fun mealPlan(userId: UUID, householdId: UUID?) = MealPlanEntity(
        uuid = UuidV7Generator.newId(),
        userId = userId,
        name = "This week",
        status = "READY",
        preferencesJson = "{}",
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
        householdId = householdId,
    )
}
