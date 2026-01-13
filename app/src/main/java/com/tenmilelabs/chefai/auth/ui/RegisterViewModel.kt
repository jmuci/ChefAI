package com.tenmilelabs.chefai.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.data.network.AuthHttpException
import com.tenmilelabs.chefai.auth.domain.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

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
    data object NavigateToHome : RegisterUiEvent
    data object NavigateToLogin : RegisterUiEvent
}

/**
 * ViewModel for the registration screen.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val sessionManager: SessionManager
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
                onSuccess = { user ->
                    _uiEvent.emit(RegisterUiEvent.NavigateToHome)
                },
                onFailure = { exception ->
                    Timber.e(exception, "Registration failed")
                    handleRegistrationError(exception)
                }
            )
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
                        // Bad Request, Conflict - show as field errors
                        val errorMessage = getHttpErrorMessage(exception.statusCode)
                        setErrorField(errorMessage)
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

    private fun setErrorField(errorMessage: String) {
        val currentUrl = _uiState.value.email.lowercase()
        _uiState.value = _uiState.value.copy(
            usernameError = if (errorMessage.contains("username")) errorMessage else null,
            emailError = if (errorMessage.contains("email")) errorMessage else null,
            passwordError = if (errorMessage.contains("password")) errorMessage else null
        )
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

    private fun getHttpErrorMessage(statusCode: Int): String {
        return when (statusCode) {
            400 -> "Invalid input data"
            409 -> "Email already registered"
            else -> "Registration failed. Please try again."
        }
    }
}