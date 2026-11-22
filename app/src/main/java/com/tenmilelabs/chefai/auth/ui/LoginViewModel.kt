package com.tenmilelabs.chefai.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.data.local.SecurePreferencesInterface
import com.tenmilelabs.chefai.auth.data.network.AuthHttpException
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.auth.ui.google.GoogleIdTokenResult
import com.tenmilelabs.chefai.core.data.sync.ConnectivityObserver
import com.tenmilelabs.chefai.core.ui.banner.StatusBannerController
import com.tenmilelabs.chefai.core.ui.banner.StatusBannerPlacement
import com.tenmilelabs.chefai.core.ui.banner.StatusBannerTone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/** `ErrorResponse.code` of `POST /auth/google` when the email already has a password account. */
internal const val GOOGLE_EMAIL_EXISTS = "GOOGLE_EMAIL_EXISTS"

private val SERVER_ERROR_CODES = setOf(500, 502, 503)

/** How long a login error banner stays up. */
internal val LOGIN_BANNER_DURATION = 5.seconds

/**
 * A suggestion shown in the email field's autocomplete dropdown.
 * [autofillPassword] is non-null only for debug test accounts.
 */
data class EmailSuggestion(val email: String, val autofillPassword: String? = null)

/**
 * UiState for the login screen.
 */
data class LoginUiState(
    val isLoading: Boolean = false,
    val email: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val emailSuggestions: List<EmailSuggestion> = emptyList()
)

/**
 * One-time events for the login screen.
 */
sealed interface LoginUiEvent {
    data class ShowSnackbar(val message: Int) : LoginUiEvent
    data object NavigateToHome : LoginUiEvent
    data object NavigateToRegister : LoginUiEvent

    /** [email] is whatever is typed so far (possibly blank), to prefill the forgot-password form. */
    data class NavigateToForgotPassword(val email: String) : LoginUiEvent

    /** A household invite was pending sign-in (ADR-014 §7) — resume the join instead of Home. */
    data class NavigateToAcceptInvite(val token: String) : LoginUiEvent
}

/**
 * ViewModel for the login screen.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val securePreferences: SecurePreferencesInterface,
    private val statusBanner: StatusBannerController,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {

    // Debug source set only; release gets an empty list (see DebugTestAccounts.kt).
    private val testAccounts: List<EmailSuggestion> = debugTestAccounts()

    private var savedEmail: String? = null

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<LoginUiEvent>()
    val uiEvents = _uiEvent.asSharedFlow()

    init {
        // Check current session state
        if (sessionManager.userSession.value is UserSession.Authenticated) {
            // User is already logged in, navigate to home
            viewModelScope.launch {
                _uiEvent.emit(LoginUiEvent.NavigateToHome)
            }
        } else {
            // Prefill the email used during the last successful login
            viewModelScope.launch {
                val email = securePreferences.getLastLoginEmail().first()
                savedEmail = email
                if (!email.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(email = email)
                }
            }
        }
    }

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(
            email = email,
            emailError = null,
            emailSuggestions = computeSuggestions(email)
        )
    }

    fun onEmailSuggestionSelected(suggestion: EmailSuggestion) {
        _uiState.value = _uiState.value.copy(
            email = suggestion.email,
            password = suggestion.autofillPassword ?: _uiState.value.password,
            emailSuggestions = emptyList(),
            emailError = null
        )
    }

    fun onEmailSuggestionsDismissed() {
        _uiState.value = _uiState.value.copy(emailSuggestions = emptyList())
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            passwordError = null
        )
    }

    fun onRememberMeChange(rememberMe: Boolean) {
        _uiState.value = _uiState.value.copy(rememberMe = rememberMe)
    }

    fun onPasswordVisibilityToggle() {
        _uiState.value = _uiState.value.copy(
            isPasswordVisible = !_uiState.value.isPasswordVisible
        )
    }

    fun onLoginClick() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        // Validate inputs
        if (!validateInputs(email, password)) {
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = sessionManager.login(email, password)

            _uiState.value = _uiState.value.copy(isLoading = false)

            result.fold(
                onSuccess = { _ ->
                    securePreferences.saveLastLoginEmail(email)
                    // AccountUpgradeUseCase has already completed inside SessionManager.login()
                    // (awaited, not fire-and-forget) by the time this runs, so getCurrentUserId()
                    // is stable — see SessionManager.login()'s doc and ADR-014 §7.
                    navigateAfterSignIn()
                },
                onFailure = { exception ->
                    Timber.e(exception, "Login failed")
                    handleLoginError(exception)
                }
            )
        }
    }

    /**
     * The picker's outcome, forwarded by the screen (the Activity-bound request itself lives in the
     * UI layer, see [GoogleIdTokenResult]). Success continues like a password login; cancelling is
     * silent; everything else is a message.
     */
    fun onGoogleResult(result: GoogleIdTokenResult) {
        when (result) {
            GoogleIdTokenResult.Cancelled -> Unit
            GoogleIdTokenResult.NoGoogleAccount -> viewModelScope.launch {
                _uiEvent.emit(LoginUiEvent.ShowSnackbar(R.string.error_google_no_account))
            }
            is GoogleIdTokenResult.Failure -> viewModelScope.launch {
                // The cause is a Credential Manager exception; it carries no token.
                Timber.e(result.cause, "Google credential request failed")
                _uiEvent.emit(LoginUiEvent.ShowSnackbar(R.string.error_google_sign_in_failed))
            }
            is GoogleIdTokenResult.Success -> viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val signIn = sessionManager.loginWithGoogle(result.idToken)

                _uiState.value = _uiState.value.copy(isLoading = false)

                signIn.fold(
                    onSuccess = { navigateAfterSignIn() },
                    onFailure = { exception ->
                        Timber.e(exception, "Google sign-in failed")
                        handleGoogleError(exception)
                    },
                )
            }
        }
    }

    /** Pending-invite aware success navigation, shared by password and Google sign-in. */
    private suspend fun navigateAfterSignIn() {
        val pendingInviteToken = securePreferences.getPendingInviteToken().first()
        if (pendingInviteToken != null) {
            _uiEvent.emit(LoginUiEvent.NavigateToAcceptInvite(pendingInviteToken))
        } else {
            _uiEvent.emit(LoginUiEvent.NavigateToHome)
        }
    }

    private suspend fun handleGoogleError(exception: Throwable) {
        when {
            exception is IOException -> reportUnreachableBackend()
            exception is AuthHttpException && exception.code == GOOGLE_EMAIL_EXISTS ->
                _uiEvent.emit(LoginUiEvent.ShowSnackbar(R.string.error_google_email_exists))
            exception is AuthHttpException && exception.statusCode in SERVER_ERROR_CODES -> reportUnreachableBackend()
            // INVALID_GOOGLE_TOKEN, an older backend's 404, anything else: field errors make no
            // sense for Google, so it is always the generic message.
            else -> _uiEvent.emit(LoginUiEvent.ShowSnackbar(R.string.error_google_sign_in_failed))
        }
    }

    private suspend fun handleLoginError(exception: Throwable) {
        when (exception) {
            // Couldn't reach the backend at all (no route, refused, timed out, …). Whether that's
            // the phone or the server is what the user needs to know, so ask the connectivity
            // monitor rather than guessing from the exception type: an UnknownHostException, for
            // one, is what both a dead Wi-Fi and a DNS outage on our side look like.
            is IOException -> reportUnreachableBackend()
            // HTTP errors - might need field-specific or general error
            is AuthHttpException -> {
                when (exception.statusCode) {
                    400, 401, 404 -> {
                        // Bad Request, Unauthorized, Not Found - show as field errors.
                        // A status whose message names neither field (400's does not) still has to
                        // reach the user, or the spinner just stops and nothing is said.
                        val errorMessage = getHttpErrorMessage(exception.statusCode)
                        val emailError = errorMessage.takeIf { it.contains("email") }
                        val passwordError = errorMessage.takeIf { it.contains("password") }
                        if (emailError == null && passwordError == null) {
                            _uiEvent.emit(LoginUiEvent.ShowSnackbar(R.string.error_login_failed))
                        } else {
                            _uiState.value = _uiState.value.copy(
                                emailError = emailError,
                                passwordError = passwordError
                            )
                        }
                    }
                    500, 502, 503 -> reportUnreachableBackend()
                    else -> {
                        // Other HTTP errors
                        _uiEvent.emit(
                            LoginUiEvent.ShowSnackbar(
                                R.string.error_login_failed
                            )
                        )
                    }
                }
            }
            // Other unknown errors
            else -> {
                _uiEvent.emit(
                    LoginUiEvent.ShowSnackbar(
                        R.string.error_login_failed
                    )
                )
            }
        }
    }

    /**
     * Banner, not snackbar: the yellow "offline" warning when the phone has no network, else red.
     * Login has no bottom nav to surface from, so it drops down from the top and leaves quickly.
     */
    private fun reportUnreachableBackend() {
        val (message, tone) = if (connectivity.isOnline.value) {
            R.string.status_banner_server_error to StatusBannerTone.ERROR
        } else {
            R.string.status_banner_offline to StatusBannerTone.WARNING
        }
        statusBanner.show(
            message = message,
            tone = tone,
            displayDuration = LOGIN_BANNER_DURATION,
            placement = StatusBannerPlacement.TOP,
        )
    }

    fun onForgotPasswordClick() {
        viewModelScope.launch {
            _uiEvent.emit(LoginUiEvent.NavigateToForgotPassword(_uiState.value.email.trim()))
        }
    }

    fun onCreateAccountClick() {
        viewModelScope.launch {
            _uiEvent.emit(LoginUiEvent.NavigateToRegister)
        }
    }

    private fun computeSuggestions(input: String): List<EmailSuggestion> {
        if (input.isBlank()) return emptyList()
        val result = mutableListOf<EmailSuggestion>()
        savedEmail?.let { saved ->
            if (saved.contains(input, ignoreCase = true)) {
                result.add(EmailSuggestion(saved))
            }
        }
        testAccounts
            .filter { it.email.contains(input, ignoreCase = true) }
            .filterNot { it.email == savedEmail }
            .forEach { result.add(it) }
        return result
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true
        var emailError: String? = null
        var passwordError: String? = null

        if (email.isBlank()) {
            emailError = "Email is required"
            isValid = false
        } else if (!isValidEmail(email)) {
            emailError = "Invalid email format"
            isValid = false
        }

        if (password.isBlank()) {
            passwordError = "Password is required"
            isValid = false
        } else if (password.length < 8) {
            passwordError = "Password must be at least 8 characters"
            isValid = false
        }

        _uiState.value = _uiState.value.copy(
            emailError = emailError,
            passwordError = passwordError
        )

        return isValid
    }

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return emailRegex.matches(email)
    }

    private fun getHttpErrorMessage(statusCode: Int): String {
        return when (statusCode) {
            400 -> "Invalid input format"
            401 -> "Invalid email or password"
            404 -> "User not found. Please check your email."
            else -> "Login failed. Please try again."
        }
    }
}
