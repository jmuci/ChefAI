package com.tenmilelabs.chefai.auth.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.data.network.AuthHttpException
import com.tenmilelabs.chefai.auth.data.network.FakeAuthNetworkDataSource
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.ui.google.GoogleIdTokenResult
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.auth.domain.usecase.LogoutUseCase
import com.tenmilelabs.chefai.core.data.sync.FakeConnectivityObserver
import com.tenmilelabs.chefai.core.data.sync.FakePendingChangesPusher
import com.tenmilelabs.chefai.core.testutil.createTestSessionManagerWithAuthSource
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val pusher = FakePendingChangesPusher()
    private val connectivity = FakeConnectivityObserver()
    private lateinit var sessionManager: SessionManager
    private lateinit var authSource: FakeAuthNetworkDataSource

    private suspend fun signedInViewModel(): UserProfileViewModel {
        val (manager, source) = createTestSessionManagerWithAuthSource()
        sessionManager = manager
        authSource = source
        sessionManager.login("cook@example.com", "hunter2")
        return UserProfileViewModel(LogoutUseCase(sessionManager, pusher, connectivity), sessionManager)
    }

    private fun assertSignedIn() =
        assertThat(sessionManager.userSession.value).isInstanceOf(UserSession.Authenticated::class.java)

    private fun assertLoggedOut() =
        assertThat(sessionManager.userSession.value).isInstanceOf(UserSession.Anonymous::class.java)

    @Test
    fun `online with pending changes pushes them and logs out without a notice`() = runTest {
        val viewModel = signedInViewModel()
        pusher.pending = 3

        viewModel.effects.test {
            viewModel.onAction(UserProfileAction.LogoutClicked)

            assertThat(awaitItem()).isEqualTo(UserProfileEffect.LoggedOut(notice = null))
        }
        assertThat(pusher.pushCount).isEqualTo(1)
        assertThat(viewModel.logoutState.value).isEqualTo(LogoutUiState.Idle)
        assertLoggedOut()
    }

    @Test
    fun `a failed push still logs out, with a notice of what was left`() = runTest {
        val viewModel = signedInViewModel()
        pusher.pending = 2
        pusher.failures += IOException("connection reset")

        viewModel.effects.test {
            viewModel.onAction(UserProfileAction.LogoutClicked)

            assertThat(awaitItem()).isEqualTo(
                UserProfileEffect.LoggedOut(LogoutNotice.ChangesNotUploaded(2)),
            )
        }
        assertLoggedOut()
    }

    @Test
    fun `offline with pending changes asks before logging out`() = runTest {
        val viewModel = signedInViewModel()
        pusher.pending = 2
        connectivity.setOnline(false)

        viewModel.onAction(UserProfileAction.LogoutClicked)

        assertThat(viewModel.logoutState.value).isEqualTo(LogoutUiState.ConfirmOfflineLogout(2))
        assertSignedIn()
    }

    @Test
    fun `staying signed in from the offline dialog keeps the session`() = runTest {
        val viewModel = signedInViewModel()
        pusher.pending = 2
        connectivity.setOnline(false)
        viewModel.onAction(UserProfileAction.LogoutClicked)

        viewModel.onAction(UserProfileAction.OfflineLogoutDismissed)

        assertThat(viewModel.logoutState.value).isEqualTo(LogoutUiState.Idle)
        assertSignedIn()
    }

    @Test
    fun `logging out anyway from the offline dialog logs out without repeating the warning`() = runTest {
        val viewModel = signedInViewModel()
        pusher.pending = 2
        connectivity.setOnline(false)
        viewModel.onAction(UserProfileAction.LogoutClicked)

        viewModel.effects.test {
            viewModel.onAction(UserProfileAction.OfflineLogoutConfirmed)

            assertThat(awaitItem()).isEqualTo(UserProfileEffect.LoggedOut(notice = null))
        }
        assertThat(pusher.pushCount).isEqualTo(0)
        assertLoggedOut()
    }

    @Test
    fun `offline with nothing pending logs out without asking`() = runTest {
        val viewModel = signedInViewModel()
        connectivity.setOnline(false)

        viewModel.effects.test {
            viewModel.onAction(UserProfileAction.LogoutClicked)

            assertThat(awaitItem()).isEqualTo(UserProfileEffect.LoggedOut(notice = null))
        }
        assertLoggedOut()
    }

    @Test
    fun `a stray confirm without the dialog open does nothing`() = runTest {
        val viewModel = signedInViewModel()

        viewModel.onAction(UserProfileAction.OfflineLogoutConfirmed)

        assertThat(viewModel.logoutState.value).isEqualTo(LogoutUiState.Idle)
        assertSignedIn()
    }

    // --- Connect Google (ADR-019) ---

    @Test
    fun `connecting google links the account and says so`() = runTest {
        val viewModel = signedInViewModel()

        viewModel.effects.test {
            viewModel.onAction(UserProfileAction.GoogleLinkPicked(GoogleIdTokenResult.Success("id-token")))

            assertThat(awaitItem()).isEqualTo(UserProfileEffect.ShowMessage(R.string.google_connected))
        }
        assertThat(authSource.linkGoogleRequests.map { it.idToken }).containsExactly("id-token")
        val session = sessionManager.userSession.value as UserSession.Authenticated
        assertThat(session.signInMethods.hasGoogle).isTrue()
    }

    @Test
    fun `connecting google with a different email explains the mismatch`() = runTest {
        val viewModel = signedInViewModel()
        authSource.linkGoogleError =
            AuthHttpException(message = "mismatch", statusCode = 409, code = "GOOGLE_EMAIL_MISMATCH")

        viewModel.effects.test {
            viewModel.onAction(UserProfileAction.GoogleLinkPicked(GoogleIdTokenResult.Success("id-token")))

            assertThat(awaitItem()).isEqualTo(UserProfileEffect.ShowMessage(R.string.error_google_email_mismatch))
        }
        assertThat((sessionManager.userSession.value as UserSession.Authenticated).signInMethods.hasGoogle).isFalse()
    }

    @Test
    fun `connecting a google account owned by someone else explains it is taken`() = runTest {
        val viewModel = signedInViewModel()
        authSource.linkGoogleError =
            AuthHttpException(message = "taken", statusCode = 409, code = "GOOGLE_IDENTITY_TAKEN")

        viewModel.effects.test {
            viewModel.onAction(UserProfileAction.GoogleLinkPicked(GoogleIdTokenResult.Success("id-token")))

            assertThat(awaitItem()).isEqualTo(UserProfileEffect.ShowMessage(R.string.error_google_identity_taken))
        }
    }

    @Test
    fun `connecting google with an invalid token shows the generic failure`() = runTest {
        val viewModel = signedInViewModel()
        authSource.linkGoogleError =
            AuthHttpException(message = "bad", statusCode = 401, code = "INVALID_GOOGLE_TOKEN")

        viewModel.effects.test {
            viewModel.onAction(UserProfileAction.GoogleLinkPicked(GoogleIdTokenResult.Success("id-token")))

            assertThat(awaitItem()).isEqualTo(UserProfileEffect.ShowMessage(R.string.error_google_sign_in_failed))
        }
    }

    @Test
    fun `connecting google while offline shows the network error`() = runTest {
        val viewModel = signedInViewModel()
        authSource.linkGoogleError = IOException("no route")

        viewModel.effects.test {
            viewModel.onAction(UserProfileAction.GoogleLinkPicked(GoogleIdTokenResult.Success("id-token")))

            assertThat(awaitItem()).isEqualTo(UserProfileEffect.ShowMessage(R.string.error_network_error))
        }
    }

    @Test
    fun `cancelling the google picker is silent`() = runTest {
        val viewModel = signedInViewModel()

        viewModel.effects.test {
            viewModel.onAction(UserProfileAction.GoogleLinkPicked(GoogleIdTokenResult.Cancelled))

            expectNoEvents()
        }
        assertThat(authSource.linkGoogleRequests).isEmpty()
    }

    @Test
    fun `no google account on the device shows a message and links nothing`() = runTest {
        val viewModel = signedInViewModel()

        viewModel.effects.test {
            viewModel.onAction(UserProfileAction.GoogleLinkPicked(GoogleIdTokenResult.NoGoogleAccount))

            assertThat(awaitItem()).isEqualTo(UserProfileEffect.ShowMessage(R.string.error_google_no_account))
        }
        assertThat(authSource.linkGoogleRequests).isEmpty()
    }

    @Test
    fun `a failed google credential request shows the generic failure`() = runTest {
        val viewModel = signedInViewModel()

        viewModel.effects.test {
            viewModel.onAction(UserProfileAction.GoogleLinkPicked(GoogleIdTokenResult.Failure(IllegalStateException("x"))))

            assertThat(awaitItem()).isEqualTo(UserProfileEffect.ShowMessage(R.string.error_google_sign_in_failed))
        }
    }
}
