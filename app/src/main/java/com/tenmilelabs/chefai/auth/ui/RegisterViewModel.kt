package com.tenmilelabs.chefai.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.data.local.SecurePreferencesInterface
import com.tenmilelabs.chefai.auth.data.network.AuthHttpException
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.ui.google.GoogleIdTokenResult
import com.tenmilelabs.chefai.auth.ui.google.googleIdTokenEmail
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
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * UiState for the registration screen.
 */
data class RegisterUiState(
    val isLoading: Boolean = false,
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val usernameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null
)

/**
 * One-time events for the registration screen.
 */
sealed interface RegisterUiEvent {
    data class ShowSnackbar(val message: Int) : RegisterUiEvent
    data class ShowSnackbarText(val message: String) : RegisterUiEvent
    data object NavigateToHome : RegisterUiEvent
    data object NavigateToLogin : RegisterUiEvent

    /** A household invite was pending sign-up (ADR-014 §7) — resume the join instead of Home. */
    data class NavigateToAcceptInvite(val token: String) : RegisterUiEvent
}

/**
 * ViewModel for the registration screen.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val securePreferences: SecurePreferencesInterface,
    private val statusBanner: StatusBannerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<RegisterUiEvent>()
    val uiEvents = _uiEvent.asSharedFlow()

    fun onUsernameChange(username: String) {
        _uiState.value = _uiState.value.copy(
            username = username,
            usernameError = null
        )
    }

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(
            email = email,
            emailError = null
        )
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            passwordError = null,
            confirmPasswordError = if (_uiState.value.confirmPassword.isNotBlank() && 
                _uiState.value.confirmPassword != password) "Passwords do not match" else null
        )
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(
            confirmPassword = confirmPassword,
            confirmPasswordError = if (_uiState.value.password.isNotBlank() && 
                confirmPassword != _uiState.value.password) "Passwords do not match" else null
        )
    }

    fun onPasswordVisibilityToggle() {
        _uiState.value = _uiState.value.copy(
            isPasswordVisible = !_uiState.value.isPasswordVisible
        )
    }

    fun onConfirmPasswordVisibilityToggle() {
        _uiState.value = _uiState.value.copy(
            isConfirmPasswordVisible = !_uiState.value.isConfirmPasswordVisible
        )
    }

    fun onRegisterClick() {
        if (!validateInputs()) {
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = sessionManager.register(
                username = _uiState.value.username.trim(),
                email = _uiState.value.email.trim(),
                password = _uiState.value.password
            )

            _uiState.value = _uiState.value.copy(isLoading = false)

            result.fold(
                onSuccess = { _ ->
                    // AccountUpgradeUseCase has already completed inside SessionManager.register()
                    // (awaited, not fire-and-forget) by the time this runs, so getCurrentUserId()
                    // is stable — see SessionManager.register()'s doc and ADR-014 §7.
                    val pendingInviteToken = securePreferences.getPendingInviteToken().first()
                    if (pendingInviteToken != null) {
                        _uiEvent.emit(RegisterUiEvent.NavigateToAcceptInvite(pendingInviteToken))
                    } else {
                        _uiEvent.emit(RegisterUiEvent.NavigateToHome)
                    }
                },
                onFailure = { exception ->
                    Timber.e(exception, "Registration failed")
                    handleRegistrationError(exception)
                }
            )
        }
    }

    /**
     * The picker's outcome, forwarded by the screen. For Google, registering and logging in are the
     * same call. A `GOOGLE_EMAIL_EXISTS` answer sends the user to Login with the email prefilled:
     * the explanation goes on the app-wide banner, which survives the navigation (a snackbar
     * collected by this screen would not).
     */
    fun onGoogleResult(result: GoogleIdTokenResult) {
        when (result) {
            GoogleIdTokenResult.Cancelled -> Unit
            GoogleIdTokenResult.NoGoogleAccount -> viewModelScope.launch {
                _uiEvent.emit(RegisterUiEvent.ShowSnackbar(R.string.error_google_no_account))
            }
            is GoogleIdTokenResult.Failure -> viewModelScope.launch {
                // The cause is a Credential Manager exception; it carries no token.
                Timber.e(result.cause, "Google credential request failed")
                _uiEvent.emit(RegisterUiEvent.ShowSnackbar(R.string.error_google_sign_in_failed))
            }
            is GoogleIdTokenResult.Success -> viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val signIn = sessionManager.loginWithGoogle(result.idToken)

                _uiState.value = _uiState.value.copy(isLoading = false)

                signIn.fold(
                    onSuccess = { _ ->
                        val pendingInviteToken = securePreferences.getPendingInviteToken().first()
                        if (pendingInviteToken != null) {
                            _uiEvent.emit(RegisterUiEvent.NavigateToAcceptInvite(pendingInviteToken))
                        } else {
                            _uiEvent.emit(RegisterUiEvent.NavigateToHome)
                        }
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Google sign-in failed")
                        handleGoogleError(exception, result.idToken)
                    },
                )
            }
        }
    }

    private suspend fun handleGoogleError(exception: Throwable, idToken: String) {
        when {
            // The IOException cases keep their specific wording.
            exception is IOException -> handleRegistrationError(exception)
            exception is AuthHttpException && exception.code == GOOGLE_EMAIL_EXISTS -> {
                googleIdTokenEmail(idToken)?.let { securePreferences.saveLastLoginEmail(it) }
                statusBanner.show(
                    message = R.string.error_google_email_exists,
                    tone = StatusBannerTone.WARNING,
                    displayDuration = 8.seconds,
                    placement = StatusBannerPlacement.TOP,
                )
                _uiEvent.emit(RegisterUiEvent.NavigateToLogin)
            }
            exception is AuthHttpException && exception.statusCode in setOf(500, 502, 503) ->
                _uiEvent.emit(RegisterUiEvent.ShowSnackbar(R.string.error_server_error))
            else -> _uiEvent.emit(RegisterUiEvent.ShowSnackbar(R.string.error_google_sign_in_failed))
        }
    }

    private suspend fun handleRegistrationError(exception: Throwable) {
        when (exception) {
            // Network connectivity errors - show snackbar
            is UnknownHostException -> {
                _uiEvent.emit(
                    RegisterUiEvent.ShowSnackbar(
                        R.string.error_network_unavailable
                    )
                )
            }
            is ConnectException -> {
                _uiEvent.emit(
                    RegisterUiEvent.ShowSnackbar(
                        R.string.error_network_unavailable
                    )
                )
            }
            is SocketTimeoutException -> {
                _uiEvent.emit(
                    RegisterUiEvent.ShowSnackbar(
                        R.string.error_connection_timeout
                    )
                )
            }
            is IOException -> {
                _uiEvent.emit(
                    RegisterUiEvent.ShowSnackbar(
                        R.string.error_network_error
                    )
                )
            }
            // HTTP errors
            is AuthHttpException -> {
                when (exception.statusCode) {
                    400, 409 -> {
                        // Bad Request, Conflict - use actual backend message
                        val errorMessage = exception.message
                        val fieldMatched = setErrorField(errorMessage)
                        if (!fieldMatched) {
                            _uiEvent.emit(
                                RegisterUiEvent.ShowSnackbarText(errorMessage)
                            )
                        }
                    }
                    500, 502, 503 -> {
                        // Server errors - show as snackbar
                        _uiEvent.emit(
                            RegisterUiEvent.ShowSnackbar(
                                R.string.error_server_error
                            )
                        )
                    }
                    else -> {
                        // Other HTTP errors
                        _uiEvent.emit(
                            RegisterUiEvent.ShowSnackbar(
                                R.string.error_registration_failed
                            )
                        )
                    }
                }
            }
            // Other unknown errors
            else -> {
                _uiEvent.emit(
                    RegisterUiEvent.ShowSnackbar(
                        R.string.error_registration_failed
                    )
                )
            }
        }
    }

    fun onLoginClick() {
        viewModelScope.launch {
            _uiEvent.emit(RegisterUiEvent.NavigateToLogin)
        }
    }

    private fun validateInputs(): Boolean {
        val username = _uiState.value.username.trim()
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        val confirmPassword = _uiState.value.confirmPassword

        var isValid = true
        var usernameError: String? = null
        var emailError: String? = null
        var passwordError: String? = null
        var confirmPasswordError: String? = null

        // Validate username
        if (username.isBlank()) {
            usernameError = "Username is required"
            isValid = false
        } else if (username.length < 3) {
            usernameError = "Username must be at least 3 characters"
            isValid = false
        } else if (!isValidUsername(username)) {
            usernameError = "Username can only contain letters, numbers, dots, hyphens, and underscores"
            isValid = false
        }

        // Validate email
        if (email.isBlank()) {
            emailError = "Email is required"
            isValid = false
        } else if (!isValidEmail(email)) {
            emailError = "Invalid email format"
            isValid = false
        }

        // Validate password
        if (password.isBlank()) {
            passwordError = "Password is required"
            isValid = false
        } else if (password.length < 8) {
            passwordError = "Password must be at least 8 characters"
            isValid = false
        } else if (!containsLettersAndNumbers(password)) {
            passwordError = "Password must contain both letters and numbers"
            isValid = false
        }

        // Validate confirm password
        if (confirmPassword.isBlank()) {
            confirmPasswordError = "Please confirm your password"
            isValid = false
        } else if (confirmPassword != password) {
            confirmPasswordError = "Passwords do not match"
            isValid = false
        }

        _uiState.value = _uiState.value.copy(
            usernameError = usernameError,
            emailError = emailError,
            passwordError = passwordError,
            confirmPasswordError = confirmPasswordError
        )

        return isValid
    }

    private fun setErrorField(errorMessage: String): Boolean {
        val lowerMessage = errorMessage.lowercase()
        val usernameError = if (lowerMessage.contains("username")) errorMessage else null
        val emailError = if (lowerMessage.contains("email")) errorMessage else null
        val passwordError = if (lowerMessage.contains("password")) errorMessage else null
        val matched = usernameError != null || emailError != null || passwordError != null
        _uiState.value = _uiState.value.copy(
            usernameError = usernameError,
            emailError = emailError,
            passwordError = passwordError
        )
        return matched
    }

    private fun isValidUsername(username: String): Boolean {
        val usernameRegex = Regex("^[a-zA-Z0-9._-]+$")
        return usernameRegex.matches(username)
    }

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return emailRegex.matches(email)
    }

    private fun containsLettersAndNumbers(password: String): Boolean {
        val hasLetter = password.any { it.isLetter() }
        val hasNumber = password.any { it.isDigit() }
        return hasLetter && hasNumber
    }

}