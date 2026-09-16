package com.tenmilelabs.chefai.household.ui

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.data.local.FakeSecurePreferences
import com.tenmilelabs.chefai.auth.data.network.dto.AuthResponse
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.testutil.createTestSessionManager
import com.tenmilelabs.chefai.core.testutil.createTestSessionManagerWithAuthSource
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.household.data.network.HouseholdApiException
import com.tenmilelabs.chefai.household.domain.model.Household
import com.tenmilelabs.chefai.household.domain.model.HouseholdInvitePreview
import com.tenmilelabs.chefai.household.domain.model.HouseholdJoinOutcome
import com.tenmilelabs.chefai.household.domain.repository.FakeHouseholdRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@ExperimentalCoroutinesApi
class AcceptInviteViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val householdRepository = FakeHouseholdRepository()
    private val securePreferences = FakeSecurePreferences()

    private val preview = HouseholdInvitePreview(
        householdName = "The Test Kitchen",
        inviterDisplayName = "Chef Owner",
    )

    private fun anonymousSessionManager(): SessionManager =
        createTestSessionManager(CoroutineScope(mainCoroutineRule.testDispatcher))

    private fun authenticatedSessionManager(): SessionManager {
        val (manager, authSource) =
            createTestSessionManagerWithAuthSource(CoroutineScope(mainCoroutineRule.testDispatcher))
        authSource.authResponse = AuthResponse(
            token = "fake_token",
            refreshToken = "fake_refresh",
            userId = UuidV7Generator.newId().toString(),
            username = "chef",
            email = "chef@example.com",
            expiresIn = 3600,
        )
        runBlocking { manager.login("chef@example.com", "password123") }
        return manager
    }

    private fun createViewModel(
        token: String? = null,
        sessionManager: SessionManager = authenticatedSessionManager(),
    ) = AcceptInviteViewModel(
        savedStateHandle = SavedStateHandle(
            token?.let { mapOf(AppDestinationArgs.INVITE_TOKEN_ARG to it) } ?: emptyMap()
        ),
        householdRepository = householdRepository,
        sessionManager = sessionManager,
        securePreferences = securePreferences,
    )

    @Test
    fun `starts in EnterCode when no token is supplied`() = runTest {
        val viewModel = createViewModel()

        assertThat(viewModel.uiState.value).isEqualTo(AcceptInviteUiState.EnterCode())
    }

    @Test
    fun `onCodeChanged updates the EnterCode field`() = runTest {
        val viewModel = createViewModel()

        viewModel.onCodeChanged("abc123")

        assertThat(viewModel.uiState.value).isEqualTo(AcceptInviteUiState.EnterCode("abc123"))
    }

    @Test
    fun `onSubmitCode with a blank code does nothing`() = runTest {
        val viewModel = createViewModel()

        viewModel.onSubmitCode()

        assertThat(householdRepository.lastPreviewedToken).isNull()
        assertThat(viewModel.uiState.value).isEqualTo(AcceptInviteUiState.EnterCode())
    }

    @Test
    fun `a valid token resolves to Preview for an authenticated caller with no household`() = runTest {
        householdRepository.previewInviteResult = Result.success(preview)
        val viewModel = createViewModel(token = "abc")

        assertThat(viewModel.uiState.value).isEqualTo(AcceptInviteUiState.Preview(preview, "abc"))
    }

    @Test
    fun `onSubmitCode from manual entry loads the preview for the trimmed code`() = runTest {
        householdRepository.previewInviteResult = Result.success(preview)
        val viewModel = createViewModel()

        viewModel.onCodeChanged("  abc  ")
        viewModel.onSubmitCode()

        assertThat(householdRepository.lastPreviewedToken).isEqualTo("abc")
        assertThat(viewModel.uiState.value).isEqualTo(AcceptInviteUiState.Preview(preview, "abc"))
    }

    @Test
    fun `an expired or unknown token resolves to InvalidOrExpired`() = runTest {
        householdRepository.previewInviteResult =
            Result.failure(HouseholdApiException("Invite not found or no longer usable", 404))

        val viewModel = createViewModel(token = "bad-token")

        assertThat(viewModel.uiState.value).isEqualTo(AcceptInviteUiState.InvalidOrExpired)
    }

    @Test
    fun `a transient preview failure resolves to Error, not InvalidOrExpired`() = runTest {
        householdRepository.previewInviteResult = Result.failure(RuntimeException("boom"))

        val viewModel = createViewModel(token = "abc")

        assertThat(viewModel.uiState.value).isInstanceOf(AcceptInviteUiState.Error::class.java)
    }

    @Test
    fun `an anonymous session resolves to RequiresSignIn instead of Preview`() = runTest {
        householdRepository.previewInviteResult = Result.success(preview)

        val viewModel = createViewModel(token = "abc", sessionManager = anonymousSessionManager())

        assertThat(viewModel.uiState.value).isEqualTo(AcceptInviteUiState.RequiresSignIn)
    }

    @Test
    fun `resolving to RequiresSignIn persists the token so sign-up can resume the join`() = runTest {
        householdRepository.previewInviteResult = Result.success(preview)

        createViewModel(token = "abc", sessionManager = anonymousSessionManager())

        assertThat(securePreferences.getPendingInviteToken().first()).isEqualTo("abc")
    }

    @Test
    fun `already being in a household resolves to AlreadyInAHousehold without calling join`() = runTest {
        householdRepository.previewInviteResult = Result.success(preview)
        householdRepository.household = Household(
            uuid = UuidV7Generator.newId(),
            name = "My Existing Household",
            ownerId = UuidV7Generator.newId(),
            members = emptyList(),
        )

        val viewModel = createViewModel(token = "abc")

        assertThat(viewModel.uiState.value).isEqualTo(AcceptInviteUiState.AlreadyInAHousehold)
        viewModel.onAccept()
        assertThat(householdRepository.lastJoinedToken).isNull()
    }

    @Test
    fun `onAccept joins on success`() = runTest {
        householdRepository.previewInviteResult = Result.success(preview)
        val joinedHousehold = Household(
            uuid = UuidV7Generator.newId(),
            name = "The Test Kitchen",
            ownerId = UuidV7Generator.newId(),
            members = emptyList(),
        )
        householdRepository.joinWithTokenResult = HouseholdJoinOutcome.Joined(joinedHousehold)
        val viewModel = createViewModel(token = "abc")

        viewModel.onAccept()

        assertThat(viewModel.uiState.value).isEqualTo(AcceptInviteUiState.Joined)
        assertThat(householdRepository.lastJoinedToken).isEqualTo("abc")
    }

    @Test
    fun `onAccept clears the pending invite token once the join succeeds`() = runTest {
        householdRepository.previewInviteResult = Result.success(preview)
        val joinedHousehold = Household(
            uuid = UuidV7Generator.newId(),
            name = "The Test Kitchen",
            ownerId = UuidV7Generator.newId(),
            members = emptyList(),
        )
        householdRepository.joinWithTokenResult = HouseholdJoinOutcome.Joined(joinedHousehold)
        securePreferences.savePendingInviteToken("abc")
        val viewModel = createViewModel(token = "abc")

        viewModel.onAccept()

        assertThat(securePreferences.getPendingInviteToken().first()).isNull()
    }

    @Test
    fun `onAccept leaves the pending invite token stored when the join fails`() = runTest {
        householdRepository.previewInviteResult = Result.success(preview)
        householdRepository.joinWithTokenResult = HouseholdJoinOutcome.NetworkError
        securePreferences.savePendingInviteToken("abc")
        val viewModel = createViewModel(token = "abc")

        viewModel.onAccept()

        assertThat(securePreferences.getPendingInviteToken().first()).isEqualTo("abc")
    }

    @Test
    fun `onAccept surfaces InvalidOrExpired when the token was revoked between preview and accept`() = runTest {
        householdRepository.previewInviteResult = Result.success(preview)
        householdRepository.joinWithTokenResult = HouseholdJoinOutcome.InvalidOrExpired
        val viewModel = createViewModel(token = "abc")

        viewModel.onAccept()

        assertThat(viewModel.uiState.value).isEqualTo(AcceptInviteUiState.InvalidOrExpired)
    }

    @Test
    fun `onAccept surfaces a transient network failure as Error`() = runTest {
        householdRepository.previewInviteResult = Result.success(preview)
        householdRepository.joinWithTokenResult = HouseholdJoinOutcome.NetworkError
        val viewModel = createViewModel(token = "abc")

        viewModel.onAccept()

        assertThat(viewModel.uiState.value).isInstanceOf(AcceptInviteUiState.Error::class.java)
    }

    @Test
    fun `onAccept does nothing outside the Preview state`() = runTest {
        val viewModel = createViewModel()

        viewModel.onAccept()

        assertThat(viewModel.uiState.value).isEqualTo(AcceptInviteUiState.EnterCode())
        assertThat(householdRepository.lastJoinedToken).isNull()
    }

    @Test
    fun `onRetry returns to EnterCode from any state`() = runTest {
        householdRepository.previewInviteResult =
            Result.failure(HouseholdApiException("not found", 404))
        val viewModel = createViewModel(token = "bad-token")
        assertThat(viewModel.uiState.value).isEqualTo(AcceptInviteUiState.InvalidOrExpired)

        viewModel.onRetry()

        assertThat(viewModel.uiState.value).isEqualTo(AcceptInviteUiState.EnterCode())
    }
}
