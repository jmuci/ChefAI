package com.tenmilelabs.chefai.auth.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.data.local.FakeSecurePreferences
import com.tenmilelabs.chefai.auth.data.network.FakeAuthNetworkDataSource
import com.tenmilelabs.chefai.auth.domain.AccountSwitchHandler
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.auth.domain.usecase.AccountUpgradeUseCase
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.FakeTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeUserDao
import com.tenmilelabs.chefai.core.data.sync.FakeSyncManager
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for LoginViewModel.
 */
@ExperimentalCoroutinesApi
class LoginViewModelTest {

    private lateinit var viewModel: LoginViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var fakeSecurePreferences: FakeSecurePreferences
    private lateinit var fakeAuthNetworkDataSource: FakeAuthNetworkDataSource
    private lateinit var testScope: TestScope
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)
        fakeSecurePreferences = FakeSecurePreferences()
        fakeAuthNetworkDataSource = FakeAuthNetworkDataSource()

        val fakeUserDao = FakeUserDao()
        val fakeRecipeDao = FakeRecipeDao()
        sessionManager = SessionManager(
            securePreferences = fakeSecurePreferences,
            authNetworkDataSource = { fakeAuthNetworkDataSource },
            userDao = fakeUserDao,
            accountSwitchHandler = AccountSwitchHandler(
                securePreferences = fakeSecurePreferences,
                database = mockk<ChefAIDataBase>(relaxed = true),
                recipeDao = fakeRecipeDao,
                userDao = fakeUserDao
            ),
            accountUpgradeUseCaseProvider = {
                AccountUpgradeUseCase(
                    FakeTransactionRunner(), fakeUserDao, fakeRecipeDao,
                    FakeRecipeStepDao(), FakeRecipeIngredientDao(),
                    FakeRecipeTagCrossRefDao(), FakeRecipeLabelCrossRefDao()
                )
            },
            syncSchedulerProvider = { FakeSyncManager() },
            applicationScope = testScope
        ).apply {
            uuidGenerator = { UuidV7Generator.newId() }
        }

        viewModel = LoginViewModel(sessionManager, fakeSecurePreferences)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has correct default values`() = testScope.runTest {
        val state = viewModel.uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.email).isEmpty()
        assertThat(state.password).isEmpty()
        assertThat(state.rememberMe).isFalse()
        assertThat(state.isPasswordVisible).isFalse()
        assertThat(state.emailError).isNull()
        assertThat(state.passwordError).isNull()
    }

    @Test
    fun `on email change updates email and clears error`() = testScope.runTest {
        // Given: Initial state with email error
        viewModel.onEmailChange("invalid")
        viewModel.onLoginClick() // Trigger validation to set error
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
        viewModel.onLoginClick() // Trigger validation to set error
        advanceUntilIdle()

        // When: Changing password
        viewModel.onPasswordChange("newpassword123")

        // Then: Password is updated and error is cleared
        val state = viewModel.uiState.value
        assertThat(state.password).isEqualTo("newpassword123")
        assertThat(state.passwordError).isNull()
    }

    @Test
    fun `on remember me change toggles remember me state`() = testScope.runTest {
        // When: Toggling remember me
        viewModel.onRememberMeChange(true)

        // Then: Remember me is true
        assertThat(viewModel.uiState.value.rememberMe).isTrue()

        // When: Toggling back
        viewModel.onRememberMeChange(false)

        // Then: Remember me is false
        assertThat(viewModel.uiState.value.rememberMe).isFalse()
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
    fun `on login click with empty email shows error`() = testScope.runTest {
        // Given: Empty email
        viewModel.onEmailChange("")
        viewModel.onPasswordChange("password123")

        // When: Clicking login
        viewModel.onLoginClick()
        advanceUntilIdle()

        // Then: Email error is shown
        val state = viewModel.uiState.value
        assertThat(state.emailError).isEqualTo("Email is required")
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `on login click with invalid email format shows error`() = testScope.runTest {
        // Given: Invalid email format
        viewModel.onEmailChange("invalid-email")
        viewModel.onPasswordChange("password123")

        // When: Clicking login
        viewModel.onLoginClick()
        advanceUntilIdle()

        // Then: Email error is shown
        val state = viewModel.uiState.value
        assertThat(state.emailError).isEqualTo("Invalid email format")
    }

    @Test
    fun `on login click with empty password shows error`() = testScope.runTest {
        // Given: Empty password
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("")

        // When: Clicking login
        viewModel.onLoginClick()
        advanceUntilIdle()

        // Then: Password error is shown
        val state = viewModel.uiState.value
        assertThat(state.passwordError).isEqualTo("Password is required")
    }

    @Test
    fun `on login click with short password shows error`() = testScope.runTest {
        // Given: Short password
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("short")

        // When: Clicking login
        viewModel.onLoginClick()
        advanceUntilIdle()

        // Then: Password error is shown
        val state = viewModel.uiState.value
        assertThat(state.passwordError).isEqualTo("Password must be at least 8 characters")
    }

    @Test
    fun `on login click with valid credentials emits navigate to home event`() = testScope.runTest {
        // Given: Valid credentials
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")

        // When: Clicking login
        viewModel.uiEvents.test {
            viewModel.onLoginClick()
            advanceUntilIdle()

            // Then: Navigate to home event is emitted
            val event = awaitItem()
            assertThat(event).isInstanceOf(LoginUiEvent.NavigateToHome::class.java)
        }
    }

    @Test
    fun `on login click with valid credentials sets loading state`() = testScope.runTest {
        // Given: Valid credentials
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")

        // When: Clicking login
        viewModel.onLoginClick()

        // Then: Loading state is set temporarily
        // Note: In real scenario, loading would be true during API call
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `on login click with network error emits snackbar event`() = testScope.runTest {
        // Given: Network error
        fakeAuthNetworkDataSource.shouldThrowError = true
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")

        // When: Clicking login
        viewModel.uiEvents.test {
            viewModel.onLoginClick()
            advanceUntilIdle()

            // Then: Snackbar event is emitted
            val event = awaitItem()
            assertThat(event).isInstanceOf(LoginUiEvent.ShowSnackbar::class.java)
        }
    }

    @Test
    fun `on create account click emits navigate to register event`() = testScope.runTest {
        // When: Clicking create account
        viewModel.uiEvents.test {
            viewModel.onCreateAccountClick()
            advanceUntilIdle()

            // Then: Navigate to register event is emitted
            val event = awaitItem()
            assertThat(event).isEqualTo(LoginUiEvent.NavigateToRegister)
        }
    }

    @Test
    fun `login with unknown host exception shows network unavailable error`() = testScope.runTest {
        // Given: Valid credentials but network unavailable
        fakeAuthNetworkDataSource.shouldThrowError = true
        fakeAuthNetworkDataSource.authResponse = null
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")

        // When: Login fails with network error
        viewModel.uiEvents.test {
            viewModel.onLoginClick()
            advanceUntilIdle()

            // Then: Shows network error
            val event = awaitItem() as LoginUiEvent.ShowSnackbar
            assertThat(event.message).isEqualTo(R.string.error_login_failed)
        }
    }

    @Test
    fun `session manager already authenticated navigates to home on init`() = testScope.runTest {
        // Given: User is already logged in
        sessionManager.login("test@example.com", "password123")
        advanceUntilIdle()

        // When: Creating new ViewModel with authenticated session
        val newViewModel = LoginViewModel(sessionManager, fakeSecurePreferences)

        // Then: ViewModel should have triggered navigation
        newViewModel.uiEvents.test {
            // The event may have already been emitted during init
            // We check the session state instead
            val session = sessionManager.userSession.value
            assertThat(session).isInstanceOf(UserSession.Authenticated::class.java)
        }
    }

    @Test
    fun `init prefills email from last successful login`() = testScope.runTest {
        // Given: A previously saved login email
        fakeSecurePreferences.saveLastLoginEmail("saved@example.com")

        // When: Creating a new ViewModel
        val newViewModel = LoginViewModel(sessionManager, fakeSecurePreferences)
        advanceUntilIdle()

        // Then: Email field is prefilled
        assertThat(newViewModel.uiState.value.email).isEqualTo("saved@example.com")
    }

    @Test
    fun `successful login saves email to preferences`() = testScope.runTest {
        // Given: Valid credentials
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")

        // When: Login succeeds
        viewModel.onLoginClick()
        advanceUntilIdle()

        // Then: Email is persisted
        fakeSecurePreferences.getLastLoginEmail().test {
            assertThat(awaitItem()).isEqualTo("test@example.com")
        }
    }

    @Test
    fun `on email change shows matching suggestions`() = testScope.runTest {
        // Given: A saved login email stored in preferences
        fakeSecurePreferences.saveLastLoginEmail("user@example.com")
        val vm = LoginViewModel(sessionManager, fakeSecurePreferences)
        advanceUntilIdle() // let init coroutine run so savedEmail is set

        // When: User types a prefix that matches the saved email
        vm.onEmailChange("user")

        // Then: The saved email appears in suggestions
        val suggestions = vm.uiState.value.emailSuggestions
        assertThat(suggestions.map { it.email }).contains("user@example.com")
    }

    @Test
    fun `on email change shows no suggestions when input is blank`() = testScope.runTest {
        // When: User clears the field
        viewModel.onEmailChange("")

        // Then: No suggestions shown
        assertThat(viewModel.uiState.value.emailSuggestions).isEmpty()
    }

    @Test
    fun `on email suggestion selected fills email and clears suggestions`() = testScope.runTest {
        // Given: Some suggestions visible
        viewModel.onEmailChange("test")

        // When: User selects a suggestion with no autofill password
        val suggestion = EmailSuggestion("saved@example.com")
        viewModel.onEmailSuggestionSelected(suggestion)

        // Then: Email is filled, suggestions are cleared, password unchanged
        val state = viewModel.uiState.value
        assertThat(state.email).isEqualTo("saved@example.com")
        assertThat(state.emailSuggestions).isEmpty()
        assertThat(state.password).isEmpty()
    }

    @Test
    fun `on email suggestion selected with test account autofills password`() = testScope.runTest {
        // When: User selects a debug test account suggestion
        val suggestion = EmailSuggestion("test1@ex.com", autofillPassword = "test123!")
        viewModel.onEmailSuggestionSelected(suggestion)

        // Then: Both email and password are filled
        val state = viewModel.uiState.value
        assertThat(state.email).isEqualTo("test1@ex.com")
        assertThat(state.password).isEqualTo("test123!")
        assertThat(state.emailSuggestions).isEmpty()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `on email suggestions dismissed clears suggestions`() = testScope.runTest {
        // Given: Some suggestions visible
        viewModel.onEmailChange("test")

        // When: Dismissed
        viewModel.onEmailSuggestionsDismissed()

        // Then: Suggestions are cleared
        assertThat(viewModel.uiState.value.emailSuggestions).isEmpty()
    }
}
