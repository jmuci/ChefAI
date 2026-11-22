package com.tenmilelabs.chefai.auth.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.data.network.AuthHttpException
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.LogoutResult
import com.tenmilelabs.chefai.auth.ui.google.GoogleIdTokenResult
import com.tenmilelabs.chefai.auth.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/** Where the profile menu's logout flow stands. */
sealed interface LogoutUiState {
    data object Idle : LogoutUiState

    /** Checking for, or pushing, unsynced changes before the session ends — a few seconds at most. */
    data object LoggingOut : LogoutUiState

    /** Offline with [unsyncedChanges] waiting to upload: the user decides whether to log out anyway. */
    data class ConfirmOfflineLogout(val unsyncedChanges: Int) : LogoutUiState
}

/** Everything the user can do in the profile menu's logout flow. */
sealed interface UserProfileAction {
    data object LogoutClicked : UserProfileAction
    data object OfflineLogoutConfirmed : UserProfileAction
    data object OfflineLogoutDismissed : UserProfileAction

    /** The account picker (run by the UI, which has the Activity) finished; "Connect Google" continues from here. */
    data class GoogleLinkPicked(val result: GoogleIdTokenResult) : UserProfileAction
}

sealed interface UserProfileEffect {
    /** The session has ended. [notice] is what to tell the user about changes left behind, if anything. */
    data class LoggedOut(val notice: LogoutNotice?) : UserProfileEffect

    /** A one-line outcome for the snackbar, e.g. after "Connect Google". */
    data class ShowMessage(@StringRes val message: Int) : UserProfileEffect
}

/** Changes a logout left on the device without uploading them. */
sealed interface LogoutNotice {
    data class ChangesNotUploaded(val count: Int) : LogoutNotice

    /** The pre-logout check itself failed, so the count is unknown. */
    data object UploadStatusUnknown : LogoutNotice
}

/**
 * The profile menu's logout flow: asks first when logging out offline would strand unsynced
 * changes, otherwise logs out straight away via [LogoutUseCase] (which tries to push them first).
 */
@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val logout: LogoutUseCase,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _logoutState = MutableStateFlow<LogoutUiState>(LogoutUiState.Idle)
    val logoutState: StateFlow<LogoutUiState> = _logoutState.asStateFlow()

    private val _effects = Channel<UserProfileEffect>(Channel.BUFFERED)
    val effects: Flow<UserProfileEffect> = _effects.receiveAsFlow()

    fun onAction(action: UserProfileAction) {
        when (action) {
            UserProfileAction.LogoutClicked -> startLogout()
            UserProfileAction.OfflineLogoutConfirmed -> {
                if (_logoutState.value !is LogoutUiState.ConfirmOfflineLogout) return
                // The dialog already told the user what stays behind; no snackbar repeating it.
                performLogout(noticeIfChangesLeft = false)
            }
            UserProfileAction.OfflineLogoutDismissed -> {
                if (_logoutState.value is LogoutUiState.ConfirmOfflineLogout) {
                    _logoutState.value = LogoutUiState.Idle
                }
            }
            is UserProfileAction.GoogleLinkPicked -> onGoogleLinkPicked(action.result)
        }
    }

    private fun onGoogleLinkPicked(result: GoogleIdTokenResult) {
        when (result) {
            GoogleIdTokenResult.Cancelled -> Unit
            GoogleIdTokenResult.NoGoogleAccount -> showMessage(R.string.error_google_no_account)
            is GoogleIdTokenResult.Failure -> {
                // The cause is a Credential Manager exception; it carries no token.
                Timber.e(result.cause, "Google credential request failed")
                showMessage(R.string.error_google_sign_in_failed)
            }
            is GoogleIdTokenResult.Success -> viewModelScope.launch {
                sessionManager.linkGoogle(result.idToken).fold(
                    onSuccess = { _effects.send(UserProfileEffect.ShowMessage(R.string.google_connected)) },
                    onFailure = { error -> _effects.send(UserProfileEffect.ShowMessage(error.toLinkMessage())) },
                )
            }
        }
    }

    private fun showMessage(@StringRes message: Int) {
        viewModelScope.launch { _effects.send(UserProfileEffect.ShowMessage(message)) }
    }

    private fun startLogout() {
        if (_logoutState.value != LogoutUiState.Idle) return
        _logoutState.value = LogoutUiState.LoggingOut
        viewModelScope.launch {
            val stranded = logout.changesStrandedByOfflineLogout()
            if (stranded > 0) {
                _logoutState.value = LogoutUiState.ConfirmOfflineLogout(stranded)
            } else {
                runLogout(noticeIfChangesLeft = true)
            }
        }
    }

    private fun performLogout(noticeIfChangesLeft: Boolean) {
        _logoutState.value = LogoutUiState.LoggingOut
        viewModelScope.launch { runLogout(noticeIfChangesLeft) }
    }

    private suspend fun runLogout(noticeIfChangesLeft: Boolean) {
        val result = logout()
        _logoutState.value = LogoutUiState.Idle
        _effects.send(UserProfileEffect.LoggedOut(result.toNotice().takeIf { noticeIfChangesLeft }))
    }
}

private fun LogoutResult.toNotice(): LogoutNotice? = when (this) {
    LogoutResult.AllChangesUploaded -> null
    is LogoutResult.ChangesNotUploaded -> LogoutNotice.ChangesNotUploaded(count)
    LogoutResult.UploadCheckFailed -> LogoutNotice.UploadStatusUnknown
}

@StringRes
private fun Throwable.toLinkMessage(): Int = when {
    this is IOException -> R.string.error_network_error
    this is AuthHttpException && code == "GOOGLE_EMAIL_MISMATCH" -> R.string.error_google_email_mismatch
    this is AuthHttpException && code == "GOOGLE_IDENTITY_TAKEN" -> R.string.error_google_identity_taken
    else -> R.string.error_google_sign_in_failed
}
