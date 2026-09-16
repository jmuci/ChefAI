package com.tenmilelabs.chefai.household.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.data.network.dto.AuthResponse
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.domain.model.HouseholdRole
import com.tenmilelabs.chefai.core.testutil.createTestSessionManagerWithAuthSource
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.household.domain.model.Household
import com.tenmilelabs.chefai.household.domain.model.HouseholdInviteLink
import com.tenmilelabs.chefai.household.domain.model.HouseholdJoinOutcome
import com.tenmilelabs.chefai.household.domain.model.HouseholdMember
import com.tenmilelabs.chefai.household.domain.model.PendingHouseholdInvite
import com.tenmilelabs.chefai.household.domain.repository.FakeHouseholdRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@ExperimentalCoroutinesApi
class HouseholdViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var householdRepository: FakeHouseholdRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var userId: UUID

    @Before
    fun setup() {
        householdRepository = FakeHouseholdRepository()
        val (manager, authSource) =
            createTestSessionManagerWithAuthSource(CoroutineScope(mainCoroutineRule.testDispatcher))
        sessionManager = manager
        userId = UuidV7Generator.newId()
        authSource.authResponse = AuthResponse(
            token = "fake_token",
            refreshToken = "fake_refresh",
            userId = userId.toString(),
            username = "chef",
            email = "chef@example.com",
            expiresIn = 3600,
        )
        runBlocking { sessionManager.login("chef@example.com", "password123") }
    }

    private fun createViewModel() = HouseholdViewModel(householdRepository, sessionManager)

    private fun householdWithMe(role: HouseholdRole) = Household(
        uuid = UuidV7Generator.newId(),
        name = "The Test Kitchen",
        ownerId = if (role == HouseholdRole.OWNER) userId else UuidV7Generator.newId(),
        members = listOf(
            HouseholdMember(userId, "Me", "", role),
            HouseholdMember(UuidV7Generator.newId(), "Someone Else", "", HouseholdRole.OWNER)
                .takeIf { role != HouseholdRole.OWNER },
        ).filterNotNull(),
    )

    @Test
    fun `emits NoHousehold when the cache has nothing`() = runTest {
        createViewModel().uiState.test {
            val state = awaitItem().let { if (it is HouseholdUiState.Loading) awaitItem() else it }
            assertThat(state).isEqualTo(HouseholdUiState.NoHousehold)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits Success with the owner role`() = runTest {
        householdRepository.seedHousehold(householdWithMe(HouseholdRole.OWNER))

        createViewModel().uiState.test {
            val state = awaitItem().let { if (it is HouseholdUiState.Loading) awaitItem() else it }
            assertThat(state).isInstanceOf(HouseholdUiState.Success::class.java)
            assertThat((state as HouseholdUiState.Success).myRole).isEqualTo(HouseholdRole.OWNER)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits Success with the member role`() = runTest {
        householdRepository.seedHousehold(householdWithMe(HouseholdRole.MEMBER))

        createViewModel().uiState.test {
            val state = awaitItem().let { if (it is HouseholdUiState.Loading) awaitItem() else it }
            assertThat((state as HouseholdUiState.Success).myRole).isEqualTo(HouseholdRole.MEMBER)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `myRole falls back to MEMBER when the caller is not in the cached roster`() = runTest {
        householdRepository.seedHousehold(
            Household(
                uuid = UuidV7Generator.newId(),
                name = "Stale Cache",
                ownerId = UuidV7Generator.newId(),
                members = listOf(HouseholdMember(UuidV7Generator.newId(), "Someone Else", "", HouseholdRole.OWNER)),
            )
        )

        createViewModel().uiState.test {
            val state = awaitItem().let { if (it is HouseholdUiState.Loading) awaitItem() else it }
            assertThat((state as HouseholdUiState.Success).myRole).isEqualTo(HouseholdRole.MEMBER)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a refresh failure with nothing cached surfaces Error`() = runTest {
        householdRepository.refreshResult = Result.failure(RuntimeException("boom"))

        createViewModel().uiState.test {
            val state = awaitItem().let { if (it is HouseholdUiState.Loading) awaitItem() else it }
            assertThat(state).isInstanceOf(HouseholdUiState.Error::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a background refresh failure leaves an already-cached household visible`() = runTest {
        householdRepository.household = householdWithMe(HouseholdRole.OWNER)
        householdRepository.refreshResult = Result.failure(RuntimeException("boom"))

        createViewModel().uiState.test {
            val state = awaitItem().let { if (it is HouseholdUiState.Loading) awaitItem() else it }
            assertThat(state).isInstanceOf(HouseholdUiState.Success::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onCreateHousehold with a blank name does not call the repository`() = runTest {
        createViewModel().onCreateHousehold("   ")

        assertThat(householdRepository.lastCreatedHouseholdName).isNull()
    }

    @Test
    fun `onCreateHousehold trims the name before creating`() = runTest {
        householdRepository.createHouseholdResult = Result.success(householdWithMe(HouseholdRole.OWNER))

        createViewModel().onCreateHousehold("  The Test Kitchen  ")

        assertThat(householdRepository.lastCreatedHouseholdName).isEqualTo("The Test Kitchen")
    }

    @Test
    fun `onCreateHousehold failure emits ShowError`() = runTest {
        householdRepository.createHouseholdResult = Result.failure(RuntimeException("boom"))
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onCreateHousehold("The Test Kitchen")
            assertThat(awaitItem()).isInstanceOf(HouseholdEvent.ShowError::class.java)
        }
    }

    @Test
    fun `onInviteByLink success emits ShareInviteLink`() = runTest {
        householdRepository.seedHousehold(householdWithMe(HouseholdRole.OWNER))
        householdRepository.createInviteLinkResult =
            Result.success(HouseholdInviteLink("https://chefai.app?token=abc", "abc", null))
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onInviteByLink()
            assertThat(awaitItem()).isInstanceOf(HouseholdEvent.ShareInviteLink::class.java)
        }
    }

    @Test
    fun `onInviteByLink failure emits ShowError`() = runTest {
        householdRepository.seedHousehold(householdWithMe(HouseholdRole.OWNER))
        householdRepository.createInviteLinkResult = Result.failure(RuntimeException("boom"))
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onInviteByLink()
            assertThat(awaitItem()).isInstanceOf(HouseholdEvent.ShowError::class.java)
        }
    }

    @Test
    fun `onInviteByEmail success emits InviteSent`() = runTest {
        householdRepository.seedHousehold(householdWithMe(HouseholdRole.OWNER))
        householdRepository.inviteByEmailResult = Result.success(Unit)
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onInviteByEmail("friend@example.com")
            assertThat(awaitItem()).isEqualTo(HouseholdEvent.InviteSent)
        }
        assertThat(householdRepository.lastInvitedEmail).isEqualTo("friend@example.com")
    }

    @Test
    fun `onInviteByEmail failure emits ShowError`() = runTest {
        householdRepository.seedHousehold(householdWithMe(HouseholdRole.OWNER))
        householdRepository.inviteByEmailResult = Result.failure(RuntimeException("no such user"))
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onInviteByEmail("nobody@example.com")
            assertThat(awaitItem()).isInstanceOf(HouseholdEvent.ShowError::class.java)
        }
    }

    @Test
    fun `onRemoveMember refused for a MEMBER`() = runTest {
        householdRepository.seedHousehold(householdWithMe(HouseholdRole.MEMBER))
        val viewModel = createViewModel()
        val someoneElse = UuidV7Generator.newId()

        viewModel.onRemoveMember(someoneElse)

        assertThat(householdRepository.lastRemovedMemberId).isNull()
    }

    @Test
    fun `onRemoveMember succeeds for an OWNER`() = runTest {
        householdRepository.seedHousehold(householdWithMe(HouseholdRole.OWNER))
        householdRepository.removeMemberResult = Result.success(Unit)
        val viewModel = createViewModel()
        val someoneElse = UuidV7Generator.newId()

        viewModel.onRemoveMember(someoneElse)

        assertThat(householdRepository.lastRemovedMemberId).isEqualTo(someoneElse)
    }

    @Test
    fun `onLeaveHousehold emits LeftHousehold on success`() = runTest {
        householdRepository.seedHousehold(householdWithMe(HouseholdRole.MEMBER))
        householdRepository.leaveHouseholdResult = Result.success(Unit)
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onLeaveHousehold()
            assertThat(awaitItem()).isEqualTo(HouseholdEvent.LeftHousehold)
        }
    }

    @Test
    fun `onLeaveHousehold failure emits ShowError`() = runTest {
        householdRepository.seedHousehold(householdWithMe(HouseholdRole.MEMBER))
        householdRepository.leaveHouseholdResult = Result.failure(RuntimeException("boom"))
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onLeaveHousehold()
            assertThat(awaitItem()).isInstanceOf(HouseholdEvent.ShowError::class.java)
        }
    }

    // --- Pending invite inbox ---

    private fun pendingInvite() = PendingHouseholdInvite(
        inviteId = UuidV7Generator.newId(),
        householdId = UuidV7Generator.newId(),
        expiresAt = System.currentTimeMillis() + 999_999L,
        createdAt = System.currentTimeMillis(),
    )

    @Test
    fun `pendingInvites reflects the repository's list`() = runTest {
        val invite = pendingInvite()
        householdRepository.pendingInvites = listOf(invite)

        createViewModel().pendingInvites.test {
            assertThat(awaitItem()).containsExactly(invite)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onAcceptPendingInvite on success emits PendingInviteResolved`() = runTest {
        householdRepository.acceptInviteResult =
            HouseholdJoinOutcome.Joined(householdWithMe(HouseholdRole.MEMBER))
        val invite = pendingInvite()
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onAcceptPendingInvite(invite.inviteId)
            assertThat(awaitItem()).isEqualTo(HouseholdEvent.PendingInviteResolved)
        }
        assertThat(householdRepository.lastAcceptedInviteId).isEqualTo(invite.inviteId)
    }

    @Test
    fun `onAcceptPendingInvite maps InvalidOrExpired to ShowError`() = runTest {
        householdRepository.acceptInviteResult = HouseholdJoinOutcome.InvalidOrExpired
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onAcceptPendingInvite(UuidV7Generator.newId())
            assertThat(awaitItem()).isInstanceOf(HouseholdEvent.ShowError::class.java)
        }
    }

    @Test
    fun `onDeclinePendingInvite on success emits PendingInviteResolved`() = runTest {
        householdRepository.declineInviteResult = Result.success(Unit)
        val invite = pendingInvite()
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onDeclinePendingInvite(invite.inviteId)
            assertThat(awaitItem()).isEqualTo(HouseholdEvent.PendingInviteResolved)
        }
        assertThat(householdRepository.lastDeclinedInviteId).isEqualTo(invite.inviteId)
    }

    @Test
    fun `onDeclinePendingInvite failure emits ShowError`() = runTest {
        householdRepository.declineInviteResult = Result.failure(RuntimeException("boom"))
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onDeclinePendingInvite(UuidV7Generator.newId())
            assertThat(awaitItem()).isInstanceOf(HouseholdEvent.ShowError::class.java)
        }
    }
}
