package com.tenmilelabs.chefai.auth.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.LogoutResult
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.data.sync.FakeConnectivityObserver
import com.tenmilelabs.chefai.core.data.sync.FakePendingChangesPusher
import com.tenmilelabs.chefai.core.data.sync.FakeSyncManager
import com.tenmilelabs.chefai.core.data.sync.network.SyncHttpException
import com.tenmilelabs.chefai.core.testutil.createTestSessionManagerWithAuthSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LogoutUseCaseTest {

    private val pusher = FakePendingChangesPusher()
    private val connectivity = FakeConnectivityObserver()
    private val syncScheduler = FakeSyncManager()
    private lateinit var sessionManager: SessionManager

    private suspend fun TestScope.logoutUseCase(signedIn: Boolean = true): LogoutUseCase {
        sessionManager = createTestSessionManagerWithAuthSource(backgroundScope, syncScheduler).first
        if (signedIn) sessionManager.login("cook@example.com", "hunter2")
        return LogoutUseCase(sessionManager, pusher, connectivity)
    }

    private fun assertLoggedOut() {
        assertThat(sessionManager.userSession.value).isInstanceOf(UserSession.Anonymous::class.java)
        assertThat(syncScheduler.cancelAllCount).isEqualTo(1)
    }

    @Test
    fun `pending changes are pushed with the still-valid token before sync is cancelled`() = runTest {
        val logout = logoutUseCase()
        pusher.pending = 3
        var tokenAtPush: String? = null
        var cancelsAtPush = -1
        pusher.onPush = {
            tokenAtPush = sessionManager.getAccessToken()
            cancelsAtPush = syncScheduler.cancelAllCount
        }

        val result = logout()

        assertThat(result).isEqualTo(LogoutResult.AllChangesUploaded)
        assertThat(pusher.pushCount).isEqualTo(1)
        assertThat(tokenAtPush).isNotNull()
        assertThat(cancelsAtPush).isEqualTo(0)
        assertThat(sessionManager.getAccessToken()).isNull()
        assertLoggedOut()
    }

    @Test
    fun `offline skips the push and logs out, reporting what was left`() = runTest {
        val logout = logoutUseCase()
        pusher.pending = 2
        connectivity.setOnline(false)

        val result = logout()

        assertThat(result).isEqualTo(LogoutResult.ChangesNotUploaded(2, LogoutResult.Reason.OFFLINE))
        assertThat(pusher.pushCount).isEqualTo(0)
        assertLoggedOut()
    }

    @Test
    fun `a push that outlasts the budget is abandoned and logout goes ahead`() = runTest {
        val logout = logoutUseCase()
        pusher.pending = 3
        pusher.pushDelayMs = 60_000
        val start = currentTime

        val result = logout()

        assertThat(result).isEqualTo(LogoutResult.ChangesNotUploaded(3, LogoutResult.Reason.TIMED_OUT))
        assertThat(currentTime - start).isEqualTo(LogoutUseCase.PUSH_TIMEOUT_MS)
        assertLoggedOut()
    }

    @Test
    fun `nothing to push logs out without a network call`() = runTest {
        val logout = logoutUseCase()
        pusher.pending = 0

        val result = logout()

        assertThat(result).isEqualTo(LogoutResult.AllChangesUploaded)
        assertThat(pusher.pushCount).isEqualTo(0)
        assertLoggedOut()
    }

    @Test
    fun `a failed push still logs out, reporting what was left`() = runTest {
        val logout = logoutUseCase()
        pusher.pending = 4
        pusher.failures += IOException("connection reset")

        val result = logout()

        assertThat(result).isEqualTo(LogoutResult.ChangesNotUploaded(4, LogoutResult.Reason.FAILED))
        assertLoggedOut()
    }

    @Test
    fun `rows the server rejects are reported as not uploaded`() = runTest {
        val logout = logoutUseCase()
        pusher.pending = 5
        pusher.rowsRejectedByServer = 1

        val result = logout()

        assertThat(result).isEqualTo(LogoutResult.ChangesNotUploaded(1, LogoutResult.Reason.FAILED))
        assertLoggedOut()
    }

    @Test
    fun `a 401 refreshes the token and retries the push once`() = runTest {
        val logout = logoutUseCase()
        pusher.pending = 2
        pusher.failures += SyncHttpException("Push failed: 401", statusCode = 401)

        val result = logout()

        assertThat(result).isEqualTo(LogoutResult.AllChangesUploaded)
        assertThat(pusher.pushCount).isEqualTo(2)
        assertLoggedOut()
    }

    @Test
    fun `a non-401 HTTP error is not retried`() = runTest {
        val logout = logoutUseCase()
        pusher.pending = 2
        pusher.failures += SyncHttpException("Push failed: 500", statusCode = 500)

        val result = logout()

        assertThat(result).isEqualTo(LogoutResult.ChangesNotUploaded(2, LogoutResult.Reason.FAILED))
        assertThat(pusher.pushCount).isEqualTo(1)
    }

    @Test
    fun `an anonymous session never counts or pushes`() = runTest {
        val logout = logoutUseCase(signedIn = false)
        pusher.pending = 2

        val result = logout()

        assertThat(result).isEqualTo(LogoutResult.AllChangesUploaded)
        assertThat(pusher.countCalls).isEqualTo(0)
        assertThat(pusher.pushCount).isEqualTo(0)
    }

    @Test
    fun `stranded-change check reports pending rows only when offline`() = runTest {
        val logout = logoutUseCase()
        pusher.pending = 3

        assertThat(logout.changesStrandedByOfflineLogout()).isEqualTo(0)
        connectivity.setOnline(false)
        assertThat(logout.changesStrandedByOfflineLogout()).isEqualTo(3)
        pusher.pending = 0
        assertThat(logout.changesStrandedByOfflineLogout()).isEqualTo(0)
    }
}
