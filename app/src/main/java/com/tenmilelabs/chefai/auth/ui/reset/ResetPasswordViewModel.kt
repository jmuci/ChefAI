package com.tenmilelabs.chefai.auth.ui.reset

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.auth.domain.TokenProvider
import com.tenmilelabs.chefai.auth.domain.model.PasswordResetResult
import com.tenmilelabs.chefai.auth.domain.repository.PasswordResetRepository
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Same floor the backend enforces (`InputValidator.MIN_PASSWORD_LENGTH`). */
internal const val MIN_PASSWORD_LENGTH = 8

enum class ResetPasswordFieldError { TooShort, Mismatch }

/** A failure that isn't about the token or a field — the user can simply try again. */
enum class ResetPasswordError { Network, Unknown }

sealed interface ResetPasswordUiState {
    data object Validating : ResetPasswordUiState

    /** Unknown, expired, used, or malformed token — the server doesn't say which, and neither do we. */
    data object Expired : ResetPasswordUiState

    /** The token check itself couldn't complete; "Try again" re-runs it. */
    data class LoadFailed(val error: ResetPasswordError) : ResetPasswordUiState

    data class Editing(
        val newPassword: String = "",
        val confirmPassword: String = "",
        val isPasswordVisible: Boolean = false,
        val newPasswordError: ResetPasswordFieldError? = null,
        val confirmPasswordError: ResetPasswordFieldError? = null,
        /** The server's own (user-presentable) rejection, e.g. its password rules. */
        val serverMessage: String? = null,
        val error: ResetPasswordError? = null,
        val isSubmitting: Boolean = false,
    ) : ResetPasswordUiState

    /** [otherDevicesSignedOut]: the user was signed in here, and the reset revoked every session. */
    data class Done(val otherDevicesSignedOut: Boolean) : ResetPasswordUiState
}

sealed interface ResetPasswordAction {
    data class NewPasswordChanged(val value: String) : ResetPasswordAction
    data class ConfirmPasswordChanged(val value: String) : ResetPasswordAction
    data object TogglePasswordVisibility : ResetPasswordAction
    data object Submit : ResetPasswordAction
    data object Retry : ResetPasswordAction
    data object RequestNewLink : ResetPasswordAction
    data object SignIn : ResetPasswordAction
}

sealed interface ResetPasswordEvent {
    data object NavigateToForgotPassword : ResetPasswordEvent
    data object NavigateToLogin : ResetPasswordEvent
}

/**
 * The token arrives as a navigation argument (so it lives only in [SavedStateHandle]) and is never
 * logged or stored elsewhere; the new password is held in UI state only and is cleared nowhere
 * because the screen leaves for Login right after.
 */
@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PasswordResetRepository,
    private val tokenProvider: TokenProvider,
) : ViewModel() {

    private val token: String? = savedStateHandle.get<String>(AppDestinationArgs.RESET_TOKEN_ARG)
        ?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow<ResetPasswordUiState>(ResetPasswordUiState.Validating)
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ResetPasswordEvent>()
    val events: SharedFlow<ResetPasswordEvent> = _events.asSharedFlow()

    init {
        validate()
    }

    fun onAction(action: ResetPasswordAction) {
        when (action) {
            is ResetPasswordAction.NewPasswordChanged -> edit {
                copy(newPassword = action.value, newPasswordError = null, serverMessage = null, error = null)
            }
            is ResetPasswordAction.ConfirmPasswordChanged -> edit {
                copy(confirmPassword = action.value, confirmPasswordError = null, error = null)
            }
            ResetPasswordAction.TogglePasswordVisibility -> edit { copy(isPasswordVisible = !isPasswordVisible) }
            ResetPasswordAction.Submit -> submit()
            ResetPasswordAction.Retry -> validate()
            ResetPasswordAction.RequestNewLink -> emit(ResetPasswordEvent.NavigateToForgotPassword)
            ResetPasswordAction.SignIn -> emit(ResetPasswordEvent.NavigateToLogin)
        }
    }

    private fun validate() {
        val token = token
        if (token == null) {
            _uiState.value = ResetPasswordUiState.Expired
            return
        }
        _uiState.value = ResetPasswordUiState.Validating
        viewModelScope.launch {
            _uiState.value = when (repository.validateToken(token)) {
                PasswordResetResult.Success -> ResetPasswordUiState.Editing()
                PasswordResetResult.InvalidToken, is PasswordResetResult.Validation -> ResetPasswordUiState.Expired
                PasswordResetResult.Network, PasswordResetResult.RateLimited ->
                    ResetPasswordUiState.LoadFailed(ResetPasswordError.Network)
                PasswordResetResult.Unknown -> ResetPasswordUiState.LoadFailed(ResetPasswordError.Unknown)
            }
        }
    }

    private fun submit() {
        val state = _uiState.value as? ResetPasswordUiState.Editing ?: return
        if (state.isSubmitting) return
        val token = token ?: return

        val newPasswordError = ResetPasswordFieldError.TooShort.takeIf { state.newPassword.length < MIN_PASSWORD_LENGTH }
        val confirmError = ResetPasswordFieldError.Mismatch
            .takeIf { newPasswordError == null && state.confirmPassword != state.newPassword }
        if (newPasswordError != null || confirmError != null) {
            _uiState.value = state.copy(newPasswordError = newPasswordError, confirmPasswordError = confirmError)
            return
        }

        _uiState.value = state.copy(isSubmitting = true, serverMessage = null, error = null)
        viewModelScope.launch {
            val result = repository.confirmReset(token, state.newPassword)
            _uiState.value = when (result) {
                PasswordResetResult.Success ->
                    ResetPasswordUiState.Done(otherDevicesSignedOut = tokenProvider.getAccessToken() != null)
                PasswordResetResult.InvalidToken -> ResetPasswordUiState.Expired
                is PasswordResetResult.Validation -> state.copy(
                    isSubmitting = false,
                    serverMessage = result.message.ifBlank { null },
                    // Blank body: still say *something* went wrong.
                    error = ResetPasswordError.Unknown.takeIf { result.message.isBlank() },
                )
                PasswordResetResult.Network, PasswordResetResult.RateLimited ->
                    state.copy(isSubmitting = false, error = ResetPasswordError.Network)
                PasswordResetResult.Unknown -> state.copy(isSubmitting = false, error = ResetPasswordError.Unknown)
            }
        }
    }

    private fun edit(transform: ResetPasswordUiState.Editing.() -> ResetPasswordUiState.Editing) {
        (_uiState.value as? ResetPasswordUiState.Editing)?.let { _uiState.value = it.transform() }
    }

    private fun emit(event: ResetPasswordEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}
