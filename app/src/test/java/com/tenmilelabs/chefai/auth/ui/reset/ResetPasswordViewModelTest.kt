package com.tenmilelabs.chefai.auth.ui.reset

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.domain.TokenProvider
import com.tenmilelabs.chefai.auth.domain.model.PasswordResetResult
import com.tenmilelabs.chefai.auth.domain.repository.FakePasswordResetRepository
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResetPasswordViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainRule = MainCoroutineRule(dispatcher)

    private val repository = FakePasswordResetRepository()
    private var accessToken: String? = null
    private val tokenProvider = object : TokenProvider {
        override fun getAccessToken(): String? = accessToken
    }

    private fun viewModel(token: String? = "tok") = ResetPasswordViewModel(
        savedStateHandle = SavedStateHandle(
            if (token == null) emptyMap() else mapOf(AppDestinationArgs.RESET_TOKEN_ARG to token)
        ),
        repository = repository,
        tokenProvider = tokenProvider,
    )

    private val ResetPasswordViewModel.state get() = uiState.value

    private fun ResetPasswordViewModel.fill(new: String, confirm: String = new) {
        onAction(ResetPasswordAction.NewPasswordChanged(new))
        onAction(ResetPasswordAction.ConfirmPasswordChanged(confirm))
    }

    @Test
    fun `validates the token on entry, then shows the form`() = runTest(dispatcher) {
        repository.validateGate = CompletableDeferred()
        val vm = viewModel()
        runCurrent()
        assertThat(vm.state).isEqualTo(ResetPasswordUiState.Validating)

        repository.validateGate?.complete(Unit)
        runCurrent()

        assertThat(vm.state).isEqualTo(ResetPasswordUiState.Editing())
        assertThat(repository.validateCalls).containsExactly("tok")
    }

    @Test
    fun `an invalid, expired or missing token shows Expired`() = runTest(dispatcher) {
        repository.validateResult = PasswordResetResult.InvalidToken
        val rejected = viewModel()
        runCurrent()
        assertThat(rejected.state).isEqualTo(ResetPasswordUiState.Expired)

        val missing = viewModel(token = null)
        runCurrent()
        assertThat(missing.state).isEqualTo(ResetPasswordUiState.Expired)
        assertThat(repository.validateCalls).hasSize(1) // no token, no call
    }

    @Test
    fun `a failed token check is retryable`() = runTest(dispatcher) {
        repository.validateResult = PasswordResetResult.Network
        val vm = viewModel()
        runCurrent()
        assertThat(vm.state).isEqualTo(ResetPasswordUiState.LoadFailed(ResetPasswordError.Network))

        repository.validateResult = PasswordResetResult.Success
        vm.onAction(ResetPasswordAction.Retry)
        runCurrent()

        assertThat(vm.state).isEqualTo(ResetPasswordUiState.Editing())
    }

    @Test
    fun `an unknown failure on validate is reported as Unknown`() = runTest(dispatcher) {
        repository.validateResult = PasswordResetResult.Unknown
        val vm = viewModel()
        runCurrent()

        assertThat(vm.state).isEqualTo(ResetPasswordUiState.LoadFailed(ResetPasswordError.Unknown))
    }

    @Test
    fun `a short password is rejected inline without calling the server`() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.fill("short")

        vm.onAction(ResetPasswordAction.Submit)
        runCurrent()

        assertThat((vm.state as ResetPasswordUiState.Editing).newPasswordError)
            .isEqualTo(ResetPasswordFieldError.TooShort)
        assertThat(repository.confirmCalls).isEmpty()
    }

    @Test
    fun `mismatched passwords are rejected inline without calling the server`() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.fill("hunter22!", "hunter22?")

        vm.onAction(ResetPasswordAction.Submit)
        runCurrent()

        assertThat((vm.state as ResetPasswordUiState.Editing).confirmPasswordError)
            .isEqualTo(ResetPasswordFieldError.Mismatch)
        assertThat(repository.confirmCalls).isEmpty()
    }

    @Test
    fun `editing a field clears its error`() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.fill("short")
        vm.onAction(ResetPasswordAction.Submit)

        vm.onAction(ResetPasswordAction.NewPasswordChanged("longer-now"))

        assertThat((vm.state as ResetPasswordUiState.Editing).newPasswordError).isNull()
    }

    @Test
    fun `the visibility toggle flips`() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()

        vm.onAction(ResetPasswordAction.TogglePasswordVisibility)

        assertThat((vm.state as ResetPasswordUiState.Editing).isPasswordVisible).isTrue()
    }

    @Test
    fun `a valid submit goes Submitting then Done, anonymous`() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.fill("hunter22!")
        repository.confirmGate = CompletableDeferred()

        vm.onAction(ResetPasswordAction.Submit)
        runCurrent()
        assertThat((vm.state as ResetPasswordUiState.Editing).isSubmitting).isTrue()

        repository.confirmGate?.complete(Unit)
        runCurrent()
        assertThat(vm.state).isEqualTo(ResetPasswordUiState.Done(otherDevicesSignedOut = false))
        assertThat(repository.confirmCalls).containsExactly("tok" to "hunter22!")
    }

    @Test
    fun `done mentions other devices when the user is signed in here`() = runTest(dispatcher) {
        accessToken = "jwt"
        val vm = viewModel()
        runCurrent()
        vm.fill("hunter22!")

        vm.onAction(ResetPasswordAction.Submit)
        runCurrent()

        assertThat(vm.state).isEqualTo(ResetPasswordUiState.Done(otherDevicesSignedOut = true))
    }

    @Test
    fun `a double tap submits once`() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.fill("hunter22!")
        repository.confirmGate = CompletableDeferred()

        vm.onAction(ResetPasswordAction.Submit)
        vm.onAction(ResetPasswordAction.Submit)
        runCurrent()

        assertThat(repository.confirmCalls).hasSize(1)
    }

    @Test
    fun `the server's 400 message is shown on the field and the form stays editable`() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.fill("hunter22!")
        repository.confirmResult = PasswordResetResult.Validation("Password is too common")

        vm.onAction(ResetPasswordAction.Submit)
        runCurrent()

        val editing = vm.state as ResetPasswordUiState.Editing
        assertThat(editing.serverMessage).isEqualTo("Password is too common")
        assertThat(editing.isSubmitting).isFalse()
    }

    @Test
    fun `410 on confirm becomes Expired`() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.fill("hunter22!")
        repository.confirmResult = PasswordResetResult.InvalidToken

        vm.onAction(ResetPasswordAction.Submit)
        runCurrent()

        assertThat(vm.state).isEqualTo(ResetPasswordUiState.Expired)
    }

    @Test
    fun `a network failure on confirm keeps the form and the typed passwords`() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.fill("hunter22!")
        repository.confirmResult = PasswordResetResult.Network

        vm.onAction(ResetPasswordAction.Submit)
        runCurrent()

        val editing = vm.state as ResetPasswordUiState.Editing
        assertThat(editing.error).isEqualTo(ResetPasswordError.Network)
        assertThat(editing.newPassword).isEqualTo("hunter22!")
        assertThat(editing.isSubmitting).isFalse()
    }

    @Test
    fun `request new link and sign in emit their navigation events`() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.events.test {
            vm.onAction(ResetPasswordAction.RequestNewLink)
            runCurrent()
            assertThat(awaitItem()).isEqualTo(ResetPasswordEvent.NavigateToForgotPassword)
            vm.onAction(ResetPasswordAction.SignIn)
            runCurrent()
            assertThat(awaitItem()).isEqualTo(ResetPasswordEvent.NavigateToLogin)
        }
    }
}
