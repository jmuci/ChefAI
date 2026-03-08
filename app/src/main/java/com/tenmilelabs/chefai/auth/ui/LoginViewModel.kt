package com.tenmilelabs.chefai.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.BuildConfig
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.data.local.SecurePreferencesInterface
import com.tenmilelabs.chefai.auth.data.network.AuthHttpException
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
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
}

/**
 * ViewModel for the login screen.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val securePreferences: SecurePreferencesInterface
) : ViewModel() {

    private val testAccounts: List<EmailSuggestion> = if (BuildConfig.DEBUG) {
        listOf(
            EmailSuggestion("test1@ex.com", "test123!"),
            EmailSuggestion("test2@ex.com", "test123!"),
            EmailSuggestion("test3@ex.com", "test123!")
        )
    } else {
        emptyList()
    }

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
                    _uiEvent.emit(LoginUiEvent.NavigateToHome)
                },
                onFailure = { exception ->
                    Timber.e(exception, "Login failed")
                    handleLoginError(exception)
                }
            )
        }
    }

    private suspend fun handleLoginError(exception: Throwable) {
        when (exception) {
            // Network connectivity errors - show snackbar
            is UnknownHostException -> {
                _uiEvent.emit(
                    LoginUiEvent.ShowSnackbar(
                        R.string.error_network_unavailable
                    )
                )
            }
            is ConnectException -> {
                _uiEvent.emit(
                    LoginUiEvent.ShowSnackbar(
                        R.string.error_network_unavailable
                    )
                )
            }
            is SocketTimeoutException -> {
                _uiEvent.emit(
                    LoginUiEvent.ShowSnackbar(
                        R.string.error_connection_timeout
                    )
                )
            }
            is IOException -> {
                _uiEvent.emit(
                    LoginUiEvent.ShowSnackbar(
                        R.string.error_network_error
                    )
                )
            }
            // HTTP errors - might need field-specific or general error
            is AuthHttpException -> {
                when (exception.statusCode) {
                    400, 401, 404 -> {
                        // Bad Request, Unauthorized, Not Found - show as field errors
                        val errorMessage = getHttpErrorMessage(exception.statusCode)
                        _uiState.value = _uiState.value.copy(
                            emailError = if (errorMessage.contains("email")) errorMessage else null,
                            passwordError = if (errorMessage.contains("password")) errorMessage else null
                        )
                    }
                    500, 502, 503 -> {
                        // Server errors - show as snackbar
                        _uiEvent.emit(
                            LoginUiEvent.ShowSnackbar(
                                R.string.error_server_error
                            )
                        )
                    }
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
