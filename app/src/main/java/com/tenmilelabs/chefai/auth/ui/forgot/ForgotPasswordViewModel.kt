package com.tenmilelabs.chefai.auth.ui.forgot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.auth.domain.model.PasswordResetResult
import com.tenmilelabs.chefai.auth.domain.repository.PasswordResetRepository
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/** How long "Resend" stays disabled after a link was requested. */
internal const val RESEND_COOLDOWN_SECONDS = 60

private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

enum class ForgotPasswordError { InvalidEmail, RateLimited, Network, Unknown }

sealed interface ForgotPasswordUiState {
    data class Editing(val email: String, val error: ForgotPasswordError? = null) : ForgotPasswordUiState

    data class Submitting(val email: String) : ForgotPasswordUiState

    /**
     * The same screen whether or not the account exists — the server doesn't say, and neither do we.
     * [resendSecondsLeft] is `0` once "Resend" is allowed; [error] is a failed *resend*.
     */
    data class Sent(
        val email: String,
        val resendSecondsLeft: Int,
        val isResending: Boolean = false,
        val error: ForgotPasswordError? = null,
    ) : ForgotPasswordUiState
}

sealed interface ForgotPasswordAction {
    data class EmailChanged(val email: String) : ForgotPasswordAction
    data object Submit : ForgotPasswordAction
    data object Resend : ForgotPasswordAction
    data object BackToSignIn : ForgotPasswordAction
}

sealed interface ForgotPasswordEvent {
    data object NavigateToLogin : ForgotPasswordEvent
}

/**
 * The cooldown is counted down with `delay` inside [viewModelScope] rather than in the composable,
 * so a test drives it with the test scheduler's virtual time.
 */
@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PasswordResetRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ForgotPasswordUiState>(
        ForgotPasswordUiState.Editing(email = savedStateHandle.get<String>(AppDestinationArgs.EMAIL_ARG).orEmpty())
    )
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ForgotPasswordEvent>()
    val events: SharedFlow<ForgotPasswordEvent> = _events.asSharedFlow()

    private var cooldownJob: Job? = null

    fun onAction(action: ForgotPasswordAction) {
        when (action) {
            is ForgotPasswordAction.EmailChanged -> onEmailChanged(action.email)
            ForgotPasswordAction.Submit -> submit()
            ForgotPasswordAction.Resend -> resend()
            ForgotPasswordAction.BackToSignIn -> viewModelScope.launch {
                _events.emit(ForgotPasswordEvent.NavigateToLogin)
            }
        }
    }

    private fun onEmailChanged(email: String) {
        if (_uiState.value is ForgotPasswordUiState.Editing) {
            _uiState.value = ForgotPasswordUiState.Editing(email)
        }
    }

    private fun submit() {
        val state = _uiState.value as? ForgotPasswordUiState.Editing ?: return
        val email = state.email.trim()
        if (!EMAIL_REGEX.matches(email)) {
            _uiState.value = state.copy(error = ForgotPasswordError.InvalidEmail)
            return
        }
        _uiState.value = ForgotPasswordUiState.Submitting(email)
        viewModelScope.launch {
            val error = repository.requestReset(email).toError()
            if (error == null) {
                startCooldown(email)
            } else {
                _uiState.value = ForgotPasswordUiState.Editing(email, error)
            }
        }
    }

    private fun resend() {
        val state = _uiState.value as? ForgotPasswordUiState.Sent ?: return
        if (state.resendSecondsLeft > 0 || state.isResending) return
        _uiState.value = state.copy(isResending = true, error = null)
        viewModelScope.launch {
            val error = repository.requestReset(state.email).toError()
            if (error == null) {
                startCooldown(state.email)
            } else {
                _uiState.value = state.copy(isResending = false, error = error)
            }
        }
    }

    private fun startCooldown(email: String) {
        cooldownJob?.cancel()
        _uiState.value = ForgotPasswordUiState.Sent(email, resendSecondsLeft = RESEND_COOLDOWN_SECONDS)
        cooldownJob = viewModelScope.launch {
            for (left in RESEND_COOLDOWN_SECONDS - 1 downTo 0) {
                delay(1.seconds)
                (_uiState.value as? ForgotPasswordUiState.Sent)?.let {
                    _uiState.value = it.copy(resendSecondsLeft = left)
                }
            }
        }
    }

    private fun PasswordResetResult.toError(): ForgotPasswordError? = when (this) {
        PasswordResetResult.Success -> null
        PasswordResetResult.RateLimited -> ForgotPasswordError.RateLimited
        PasswordResetResult.Network -> ForgotPasswordError.Network
        is PasswordResetResult.Validation -> ForgotPasswordError.InvalidEmail
        // InvalidToken can't come from the request endpoint; treat it like any other surprise.
        PasswordResetResult.InvalidToken, PasswordResetResult.Unknown -> ForgotPasswordError.Unknown
    }
}
