package com.tenmilelabs.chefai.auth.domain.usecase

import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.LogoutResult
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.data.sync.ConnectivityObserver
import com.tenmilelabs.chefai.core.data.sync.PendingChangesPusher
import com.tenmilelabs.chefai.core.data.sync.network.SyncHttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Logs out after a best-effort upload of the departing account's unpushed changes.
 *
 * [SessionManager.logout] cancels sync without flushing, and when a different account signs in
 * next, the account-switch cleanup deletes whatever the departing account still had `PENDING` or
 * `DELETED` (ADR-008). So before the token is cleared, this runs one push-only pass with it,
 * bounded by [PUSH_TIMEOUT_MS]. No pull: anything pulled would land for an account that is leaving.
 *
 * Logout is never blocked: offline, a timeout or a failure all end in [SessionManager.logout]
 * anyway, and the [LogoutResult] says what was left behind so the UI can tell the user.
 *
 * Runs in the caller's coroutine; no scope of its own. If the caller is cancelled during the push,
 * nothing has been cleared yet, so the user is simply still signed in.
 */
class LogoutUseCase @Inject constructor(
    private val sessionManager: SessionManager,
    private val pendingChangesPusher: PendingChangesPusher,
    private val connectivityObserver: ConnectivityObserver,
) {

    /**
     * The number of changes a logout right now would leave behind because the device is offline,
     * or 0 when online, signed out, or nothing is waiting. Lets the UI ask before logging out in
     * the one case where the push can't even be tried.
     */
    suspend fun changesStrandedByOfflineLogout(): Int {
        if (sessionManager.userSession.value !is UserSession.Authenticated) return 0
        if (connectivityObserver.isOnline.value) return 0
        return try {
            pendingChangesPusher.countPendingChanges()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Logout: couldn't count pending changes")
            0
        }
    }

    suspend operator fun invoke(): LogoutResult {
        val result = if (sessionManager.userSession.value is UserSession.Authenticated) {
            uploadPendingChanges()
        } else {
            LogoutResult.AllChangesUploaded
        }
        sessionManager.logout()
        return result
    }

    private suspend fun uploadPendingChanges(): LogoutResult = try {
        val pending = pendingChangesPusher.countPendingChanges()
        when {
            pending == 0 -> LogoutResult.AllChangesUploaded
            !connectivityObserver.isOnline.value -> {
                Timber.i("Logout: offline, leaving $pending change(s) unpushed")
                LogoutResult.ChangesNotUploaded(pending, LogoutResult.Reason.OFFLINE)
            }
            else -> {
                val pushed = withTimeoutOrNull(PUSH_TIMEOUT_MS) { pushWithOneRefresh() }
                // Counted again rather than derived from the push: rows the server rejected stay
                // dirty, and a push cut short by the timeout may have landed some batches.
                val remaining = pendingChangesPusher.countPendingChanges()
                when {
                    remaining == 0 -> LogoutResult.AllChangesUploaded
                    pushed == null -> LogoutResult.ChangesNotUploaded(remaining, LogoutResult.Reason.TIMED_OUT)
                    else -> LogoutResult.ChangesNotUploaded(remaining, LogoutResult.Reason.FAILED)
                }.also { Timber.i("Logout: push before logout finished as $it") }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Logout: couldn't count pending changes")
        LogoutResult.UploadCheckFailed
    }

    /**
     * One push, plus one retry after a token refresh on a 401 — the same recovery `SyncWorker`
     * makes. Returns whether a push completed; never throws except to propagate cancellation (the
     * timeout included).
     */
    private suspend fun pushWithOneRefresh(): Boolean {
        val error = tryPush() ?: return true
        val unauthorized = error is SyncHttpException && error.statusCode == 401
        return unauthorized && sessionManager.refreshToken().isSuccess && tryPush() == null
    }

    /** Pushes once; returns the failure, or null on success. */
    private suspend fun tryPush(): Exception? = try {
        pendingChangesPusher.pushPendingChanges()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Logout: push before logout failed")
        e
    }

    companion object {
        /** Long enough for a push on a decent connection; short enough that logout feels prompt. */
        const val PUSH_TIMEOUT_MS = 4_000L
    }
}
