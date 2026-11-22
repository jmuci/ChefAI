package com.tenmilelabs.chefai.auth.ui.forgot

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.domain.model.PasswordResetResult
import com.tenmilelabs.chefai.auth.domain.repository.FakePasswordResetRepository
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainRule = MainCoroutineRule(dispatcher)

    private val repository = FakePasswordResetRepository()

    private fun viewModel(prefill: String? = null) = ForgotPasswordViewModel(
        savedStateHandle = SavedStateHandle(
            if (prefill == null) emptyMap() else mapOf(AppDestinationArgs.EMAIL_ARG to prefill)
        ),
        repository = repository,
    )

    private val ForgotPasswordViewModel.state get() = uiState.value

    @Test
    fun `starts empty, or prefilled with the email typed on login`() {
        assertThat(viewModel().state).isEqualTo(ForgotPasswordUiState.Editing(""))
        assertThat(viewModel("a@b.com").state).isEqualTo(ForgotPasswordUiState.Editing("a@b.com"))
    }

    @Test
    fun `a malformed email is rejected client-side without calling the server`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(ForgotPasswordAction.EmailChanged("not-an-email"))

        vm.onAction(ForgotPasswordAction.Submit)
        runCurrent()

        assertThat(vm.state)
            .isEqualTo(ForgotPasswordUiState.Editing("not-an-email", ForgotPasswordError.InvalidEmail))
        assertThat(repository.requestCalls).isEmpty()
    }

    @Test
    fun `editing the email clears the error`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(ForgotPasswordAction.EmailChanged("bad"))
        vm.onAction(ForgotPasswordAction.Submit)

        vm.onAction(ForgotPasswordAction.EmailChanged("bad@"))

        assertThat(vm.state).isEqualTo(ForgotPasswordUiState.Editing("bad@"))
    }

    @Test
    fun `submit goes Editing to Submitting to Sent with the full cooldown`() = runTest(dispatcher) {
        repository.requestGate = CompletableDeferred()
        val vm = viewModel()
        vm.onAction(ForgotPasswordAction.EmailChanged(" a@b.com "))

        vm.onAction(ForgotPasswordAction.Submit)
        runCurrent()
        assertThat(vm.state).isEqualTo(ForgotPasswordUiState.Submitting("a@b.com"))

        repository.requestGate?.complete(Unit)
        runCurrent()
        assertThat(vm.state).isEqualTo(ForgotPasswordUiState.Sent("a@b.com", RESEND_COOLDOWN_SECONDS))
        assertThat(repository.requestCalls).containsExactly("a@b.com")
    }

    @Test
    fun `the sent state is the same whatever the server knows about the account`() = runTest(dispatcher) {
        // The server answers 202 for unknown accounts too, so the client has nothing to branch on.
        val vm = viewModel("ghost@nowhere.com")

        vm.onAction(ForgotPasswordAction.Submit)
        runCurrent()

        assertThat(vm.state).isInstanceOf(ForgotPasswordUiState.Sent::class.java)
    }

    @Test
    fun `failures return to Editing with the matching error`() = runTest(dispatcher) {
        val cases = mapOf(
            PasswordResetResult.RateLimited to ForgotPasswordError.RateLimited,
            PasswordResetResult.Network to ForgotPasswordError.Network,
            PasswordResetResult.Unknown to ForgotPasswordError.Unknown,
            PasswordResetResult.Validation("bad") to ForgotPasswordError.InvalidEmail,
        )
        for ((result, error) in cases) {
            repository.requestResult = result
            val vm = viewModel("a@b.com")

            vm.onAction(ForgotPasswordAction.Submit)
            runCurrent()

            assertThat(vm.state).isEqualTo(ForgotPasswordUiState.Editing("a@b.com", error))
        }
    }

    @Test
    fun `resend is disabled for 60 seconds and then works`() = runTest(dispatcher) {
        val vm = viewModel("a@b.com")
        vm.onAction(ForgotPasswordAction.Submit)
        runCurrent()

        advanceTimeBy(30_000)
        runCurrent()
        assertThat((vm.state as ForgotPasswordUiState.Sent).resendSecondsLeft).isEqualTo(30)
        vm.onAction(ForgotPasswordAction.Resend)
        runCurrent()
        assertThat(repository.requestCalls).hasSize(1) // ignored while cooling down

        advanceTimeBy(30_000)
        runCurrent()
        assertThat((vm.state as ForgotPasswordUiState.Sent).resendSecondsLeft).isEqualTo(0)

        vm.onAction(ForgotPasswordAction.Resend)
        runCurrent()
        assertThat(repository.requestCalls).hasSize(2)
        // …and a fresh cooldown starts.
        assertThat(vm.state).isEqualTo(ForgotPasswordUiState.Sent("a@b.com", RESEND_COOLDOWN_SECONDS))
    }

    @Test
    fun `a failed resend keeps the sent state, shows the error and can be retried`() = runTest(dispatcher) {
        val vm = viewModel("a@b.com")
        vm.onAction(ForgotPasswordAction.Submit)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        repository.requestResult = PasswordResetResult.RateLimited

        vm.onAction(ForgotPasswordAction.Resend)
        runCurrent()

        val sent = vm.state as ForgotPasswordUiState.Sent
        assertThat(sent.error).isEqualTo(ForgotPasswordError.RateLimited)
        assertThat(sent.isResending).isFalse()
        assertThat(sent.resendSecondsLeft).isEqualTo(0)
    }

    @Test
    fun `back to sign in navigates to login`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.events.test {
            vm.onAction(ForgotPasswordAction.BackToSignIn)
            runCurrent()
            assertThat(awaitItem()).isEqualTo(ForgotPasswordEvent.NavigateToLogin)
        }
    }
}
