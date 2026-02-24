package com.tenmilelabs.chefai.auth.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.data.local.FakeSecurePreferences
import com.tenmilelabs.chefai.auth.data.network.AuthHttpException
import com.tenmilelabs.chefai.auth.data.network.FakeAuthNetworkDataSource
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeUserDao
import java.util.UUID
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for RegisterViewModel.
 */
@ExperimentalCoroutinesApi
class RegisterViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: RegisterViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var fakeSecurePreferences: FakeSecurePreferences
    private lateinit var fakeAuthNetworkDataSource: FakeAuthNetworkDataSource
    private lateinit var testScope: TestScope
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        testScope = TestScope(testDispatcher)
        fakeSecurePreferences = FakeSecurePreferences()
        fakeAuthNetworkDataSource = FakeAuthNetworkDataSource()

        sessionManager = SessionManager(
            securePreferences = fakeSecurePreferences,
            authNetworkDataSource = { fakeAuthNetworkDataSource },
            userDao = FakeUserDao(),
            applicationScope = testScope
        ).apply {
            uuidGenerator = { UUID.randomUUID() }
        }

        viewModel = RegisterViewModel(sessionManager)
    }

    @Test
    fun `initial state has correct default values`() = testScope.runTest {
        val state = viewModel.uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.username).isEmpty()
        assertThat(state.email).isEmpty()
        assertThat(state.password).isEmpty()
        assertThat(state.confirmPassword).isEmpty()
        assertThat(state.isPasswordVisible).isFalse()
        assertThat(state.isConfirmPasswordVisible).isFalse()
        assertThat(state.usernameError).isNull()
        assertThat(state.emailError).isNull()
        assertThat(state.passwordError).isNull()
        assertThat(state.confirmPasswordError).isNull()
    }

    @Test
    fun `on username change updates username and clears error`() = testScope.runTest {
        // Given: Initial state with username error
        viewModel.onUsernameChange("ab")
        viewModel.onRegisterClick() // Trigger validation to set error
        advanceUntilIdle()

        // When: Changing username
        viewModel.onUsernameChange("newusername")

        // Then: Username is updated and error is cleared
        val state = viewModel.uiState.value
        assertThat(state.username).isEqualTo("newusername")
        assertThat(state.usernameError).isNull()
    }

    @Test
    fun `on email change updates email and clears error`() = testScope.runTest {
        // Given: Initial state with email error
        viewModel.onEmailChange("invalid")
        viewModel.onRegisterClick() // Trigger validation to set error
        advanceUntilIdle()

        // When: Changing email
        viewModel.onEmailChange("new@example.com")

        // Then: Email is updated and error is cleared
        val state = viewModel.uiState.value
        assertThat(state.email).isEqualTo("new@example.com")
        assertThat(state.emailError).isNull()
    }

    @Test
    fun `on password change updates password and clears error`() = testScope.runTest {
        // Given: Initial state with password error
        viewModel.onPasswordChange("short")
        viewModel.onRegisterClick() // Trigger validation to set error
        advanceUntilIdle()

        // When: Changing password
        viewModel.onPasswordChange("newpassword123")

        // Then: Password is updated and error is cleared
        val state = viewModel.uiState.value
        assertThat(state.password).isEqualTo("newpassword123")
        assertThat(state.passwordError).isNull()
    }

    @Test
    fun `on password change with non matching confirm password shows error`() = testScope.runTest {
        // Given: Confirm password is set
        viewModel.onConfirmPasswordChange("password123")

        // When: Changing password to different value
        viewModel.onPasswordChange("differentpass123")

        // Then: Confirm password error is shown
        val state = viewModel.uiState.value
        assertThat(state.confirmPasswordError).isEqualTo("Passwords do not match")
    }

    @Test
    fun `on confirm password change updates confirm password and clears error`() = testScope.runTest {
        // Given: Password is set
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("wrong")
        advanceUntilIdle()

        // When: Changing confirm password to match
        viewModel.onConfirmPasswordChange("password123")

        // Then: Confirm password is updated and error is cleared
        val state = viewModel.uiState.value
        assertThat(state.confirmPassword).isEqualTo("password123")
        assertThat(state.confirmPasswordError).isNull()
    }

    @Test
    fun `on confirm password change with non matching password shows error`() = testScope.runTest {
        // Given: Password is set
        viewModel.onPasswordChange("password123")

        // When: Changing confirm password to different value
        viewModel.onConfirmPasswordChange("differentpass123")

        // Then: Confirm password error is shown
        val state = viewModel.uiState.value
        assertThat(state.confirmPasswordError).isEqualTo("Passwords do not match")
    }

    @Test
    fun `on password visibility toggle changes visibility state`() = testScope.runTest {
        // When: Toggling password visibility
        viewModel.onPasswordVisibilityToggle()

        // Then: Password is visible
        assertThat(viewModel.uiState.value.isPasswordVisible).isTrue()

        // When: Toggling back
        viewModel.onPasswordVisibilityToggle()

        // Then: Password is hidden
        assertThat(viewModel.uiState.value.isPasswordVisible).isFalse()
    }

    @Test
    fun `on confirm password visibility toggle changes visibility state`() = testScope.runTest {
        // When: Toggling confirm password visibility
        viewModel.onConfirmPasswordVisibilityToggle()

        // Then: Confirm password is visible
        assertThat(viewModel.uiState.value.isConfirmPasswordVisible).isTrue()

        // When: Toggling back
        viewModel.onConfirmPasswordVisibilityToggle()

        // Then: Confirm password is hidden
        assertThat(viewModel.uiState.value.isConfirmPasswordVisible).isFalse()
    }

    @Test
    fun `on register click with empty username shows error`() = testScope.runTest {
        // Given: Empty username
        viewModel.onUsernameChange("")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")

        // When: Clicking register
        viewModel.onRegisterClick()
        advanceUntilIdle()

        // Then: Username error is shown
        val state = viewModel.uiState.value
        assertThat(state.usernameError).isEqualTo("Username is required")
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `on register click with short username shows error`() = testScope.runTest {
        // Given: Short username
        viewModel.onUsernameChange("ab")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")

        // When: Clicking register
        viewModel.onRegisterClick()
        advanceUntilIdle()

        // Then: Username error is shown
        val state = viewModel.uiState.value
        assertThat(state.usernameError).isEqualTo("Username must be at least 3 characters")
    }

    @Test
    fun `on register click with empty email shows error`() = testScope.runTest {
        // Given: Empty email
        viewModel.onUsernameChange("testuser")
        viewModel.onEmailChange("")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")

        // When: Clicking register
        viewModel.onRegisterClick()
        advanceUntilIdle()

        // Then: Email error is shown
        val state = viewModel.uiState.value
        assertThat(state.emailError).isEqualTo("Email is required")
    }

    @Test
    fun `on register click with invalid email format shows error`() = testScope.runTest {
        // Given: Invalid email format
        viewModel.onUsernameChange("testuser")
        viewModel.onEmailChange("invalid-email")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")

        // When: Clicking register
        viewModel.onRegisterClick()
        advanceUntilIdle()

        // Then: Email error is shown
        val state = viewModel.uiState.value
        assertThat(state.emailError).isEqualTo("Invalid email format")
    }

    @Test
    fun `on register click with empty password shows error`() = testScope.runTest {
        // Given: Empty password
        viewModel.onUsernameChange("testuser")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("")
        viewModel.onConfirmPasswordChange("")

        // When: Clicking register
        viewModel.onRegisterClick()
        advanceUntilIdle()

        // Then: Password error is shown
        val state = viewModel.uiState.value
        assertThat(state.passwordError).isEqualTo("Password is required")
    }

    @Test
    fun `on register click with short password shows error`() = testScope.runTest {
        // Given: Short password
        viewModel.onUsernameChange("testuser")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("short")
        viewModel.onConfirmPasswordChange("short")

        // When: Clicking register
        viewModel.onRegisterClick()
        advanceUntilIdle()

        // Then: Password error is shown
        val state = viewModel.uiState.value
        assertThat(state.passwordError).isEqualTo("Password must be at least 8 characters")
    }

    @Test
    fun `on register click with password without numbers shows error`() = testScope.runTest {
        // Given: Password without numbers
        viewModel.onUsernameChange("testuser")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("passwordonly")
        viewModel.onConfirmPasswordChange("passwordonly")

        // When: Clicking register
        viewModel.onRegisterClick()
        advanceUntilIdle()

        // Then: Password error is shown
        val state = viewModel.uiState.value
        assertThat(state.passwordError).isEqualTo("Password must contain both letters and numbers")
    }

    @Test
    fun `on register click with password without letters shows error`() = testScope.runTest {
        // Given: Password without letters
        viewModel.onUsernameChange("testuser")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("12345678")
        viewModel.onConfirmPasswordChange("12345678")

        // When: Clicking register
        viewModel.onRegisterClick()
        advanceUntilIdle()

        // Then: Password error is shown
        val state = viewModel.uiState.value
        assertThat(state.passwordError).isEqualTo("Password must contain both letters and numbers")
    }

    @Test
    fun `on register click with empty confirm password shows error`() = testScope.runTest {
        // Given: Empty confirm password
        viewModel.onUsernameChange("testuser")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("")

        // When: Clicking register
        viewModel.onRegisterClick()
        advanceUntilIdle()

        // Then: Confirm password error is shown
        val state = viewModel.uiState.value
        assertThat(state.confirmPasswordError).isEqualTo("Please confirm your password")
    }

    @Test
    fun `on register click with non matching passwords shows error`() = testScope.runTest {
        // Given: Non-matching passwords
        viewModel.onUsernameChange("testuser")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("different123")

        // When: Clicking register
        viewModel.onRegisterClick()
        advanceUntilIdle()

        // Then: Confirm password error is shown
        val state = viewModel.uiState.value
        assertThat(state.confirmPasswordError).isEqualTo("Passwords do not match")
    }

    @Test
    fun `on register click with valid inputs emits navigate to home event`() = testScope.runTest {
        // Given: Valid inputs
        viewModel.onUsernameChange("testuser")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")

        // When: Clicking register
        viewModel.uiEvents.test {
            viewModel.onRegisterClick()
            advanceUntilIdle()

            // Then: Navigate to home event is emitted
            val event = awaitItem()
            assertThat(event).isInstanceOf(RegisterUiEvent.NavigateToHome::class.java)
        }
    }

    @Test
    fun `on register click with valid inputs sets loading state`() = testScope.runTest {
        // Given: Valid inputs
        viewModel.onUsernameChange("testuser")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")

        // When: Clicking register
        viewModel.onRegisterClick()

        // Then: Loading state is set temporarily
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `on register click with network error emits snackbar event`() = testScope.runTest {
        // Given: Network error
        fakeAuthNetworkDataSource.shouldThrowError = true
        viewModel.onUsernameChange("testuser")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")

        // When: Clicking register
        viewModel.uiEvents.test {
            viewModel.onRegisterClick()
            advanceUntilIdle()

            // Then: Snackbar event is emitted
            val event = awaitItem()
            assertThat(event).isInstanceOf(RegisterUiEvent.ShowSnackbar::class.java)
        }
    }

    @Test
    fun `on login click emits navigate to login event`() = testScope.runTest {
        // When: Clicking login
        viewModel.uiEvents.test {
            viewModel.onLoginClick()
            advanceUntilIdle()

            // Then: Navigate to login event is emitted
            val event = awaitItem()
            assertThat(event).isEqualTo(RegisterUiEvent.NavigateToLogin)
        }
    }

    @Test
    fun `on register click with username containing spaces shows error`() = testScope.runTest {
        // Given: Username with spaces
        viewModel.onUsernameChange("test user")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")

        // When: Clicking register
        viewModel.onRegisterClick()
        advanceUntilIdle()

        // Then: Username error is shown
        val state = viewModel.uiState.value
        assertThat(state.usernameError).isEqualTo("Username can only contain letters, numbers, dots, hyphens, and underscores")
    }

    @Test
    fun `on register click with username containing special characters shows error`() = testScope.runTest {
        // Given: Username with special characters
        viewModel.onUsernameChange("user@name!")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")

        // When: Clicking register
        viewModel.onRegisterClick()
        advanceUntilIdle()

        // Then: Username error is shown
        val state = viewModel.uiState.value
        assertThat(state.usernameError).isEqualTo("Username can only contain letters, numbers, dots, hyphens, and underscores")
    }

    @Test
    fun `on register click with valid username characters succeeds`() = testScope.runTest {
        // Given: Username with valid characters (letters, numbers, dots, hyphens, underscores)
        viewModel.onUsernameChange("test.user-name_123")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")

        // When: Clicking register
        viewModel.uiEvents.test {
            viewModel.onRegisterClick()
            advanceUntilIdle()

            // Then: Registration succeeds (navigate to home)
            val event = awaitItem()
            assertThat(event).isInstanceOf(RegisterUiEvent.NavigateToHome::class.java)
        }
    }

    @Test
    fun `on register click with http 400 validation error shows snackbar with backend message`() = testScope.runTest {
        // Given: Backend returns HTTP 400 with validation message that doesn't match a field
        fakeAuthNetworkDataSource.shouldThrowError = true
        fakeAuthNetworkDataSource.errorToThrow = AuthHttpException(
            message = "Invalid input data",
            statusCode = 400
        )
        viewModel.onUsernameChange("testuser")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")

        // When: Clicking register
        viewModel.uiEvents.test {
            viewModel.onRegisterClick()
            advanceUntilIdle()

            // Then: Snackbar with backend message is shown
            val event = awaitItem()
            assertThat(event).isInstanceOf(RegisterUiEvent.ShowSnackbarText::class.java)
            assertThat((event as RegisterUiEvent.ShowSnackbarText).message)
                .isEqualTo("Invalid input data")
        }
    }

    @Test
    fun `on register click with http 400 username error shows field error`() = testScope.runTest {
        // Given: Backend returns HTTP 400 with username-specific message
        fakeAuthNetworkDataSource.shouldThrowError = true
        fakeAuthNetworkDataSource.errorToThrow = AuthHttpException(
            message = "Username already taken",
            statusCode = 400
        )
        viewModel.onUsernameChange("testuser")
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onConfirmPasswordChange("password123")

        // When: Clicking register
        viewModel.onRegisterClick()
        advanceUntilIdle()

        // Then: Username field error is set
        val state = viewModel.uiState.value
        assertThat(state.usernameError).isEqualTo("Username already taken")
    }
}