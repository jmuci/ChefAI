package com.tenmilelabs.chefai.settings.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.AccountDeletionPreview
import com.tenmilelabs.chefai.auth.domain.model.AccountDeletionPreviewResult
import com.tenmilelabs.chefai.auth.domain.model.DeleteAccountResult
import com.tenmilelabs.chefai.auth.domain.model.PublicRecipeDisposition
import com.tenmilelabs.chefai.auth.domain.repository.FakeAccountRepository
import com.tenmilelabs.chefai.auth.domain.usecase.DeleteAccountUseCase
import com.tenmilelabs.chefai.core.data.repository.FakeUserPreferencesRepository
import com.tenmilelabs.chefai.core.domain.model.RecipeLanguage
import com.tenmilelabs.chefai.core.domain.model.WeekStart
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import com.tenmilelabs.chefai.core.testutil.createTestSessionManager
import com.tenmilelabs.chefai.core.testutil.createTestSessionManagerWithAuthSource
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class SettingsViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val accountRepository = FakeAccountRepository()

    /** Anonymous by default, which is what every preferences test runs under. */
    private fun settingsViewModel(
        preferences: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
        sessionManager: SessionManager = createTestSessionManager(),
    ) = SettingsViewModel(
        userPreferencesRepository = preferences,
        sessionManager = sessionManager,
        accountRepository = accountRepository,
        deleteAccount = DeleteAccountUseCase(accountRepository, sessionManager, mainCoroutineRule.testDispatcher),
    )

    private suspend fun signedInViewModel(): SettingsViewModel {
        val sessionManager = createTestSessionManagerWithAuthSource().first
        sessionManager.login("cook@example.com", "hunter2")
        return settingsViewModel(sessionManager = sessionManager)
    }

    /** Keeps [SettingsViewModel.uiState] hot, so `.value` reflects every action straight away. */
    private fun TestScope.collect(viewModel: SettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private val SettingsViewModel.deleteState get() = uiState.value.deleteAccount

    private fun preview(publicRecipeCount: Int = 0) = AccountDeletionPreview(
        publicRecipeCount = publicRecipeCount,
        privateRecipeCount = 3,
        ownsHousehold = false,
    )

    /** Opens the dialog on [preview] and types a password. */
    private fun SettingsViewModel.openDialog(preview: AccountDeletionPreview) {
        accountRepository.previewResult = AccountDeletionPreviewResult.Success(preview)
        onAction(SettingsAction.DeleteAccountClicked)
        onAction(SettingsAction.DeleteAccountPasswordChanged("hunter2"))
    }

    @Test
    fun `starts on as-written, so an existing library never changes under the user`() = runTest {
        val viewModel = settingsViewModel(FakeUserPreferencesRepository())

        viewModel.uiState.test {
            assertThat(awaitItem().measurementSystem).isEqualTo(MeasurementSystem.AS_WRITTEN)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `choosing a system stores it and emits it back`() = runTest {
        val preferences = FakeUserPreferencesRepository()
        val viewModel = settingsViewModel(preferences)

        viewModel.uiState.test {
            assertThat(awaitItem().measurementSystem).isEqualTo(MeasurementSystem.AS_WRITTEN)

            viewModel.onAction(SettingsAction.MeasurementSystemChanged(MeasurementSystem.METRIC))

            assertThat(awaitItem().measurementSystem).isEqualTo(MeasurementSystem.METRIC)
            assertThat(preferences.current).isEqualTo(MeasurementSystem.METRIC)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `choosing a week start stores it and emits it back`() = runTest {
        val preferences = FakeUserPreferencesRepository(initialWeekStart = WeekStart.MONDAY)
        val viewModel = settingsViewModel(preferences)

        viewModel.uiState.test {
            assertThat(awaitItem().weekStart).isEqualTo(WeekStart.MONDAY)

            viewModel.onAction(SettingsAction.WeekStartChanged(WeekStart.SUNDAY))

            assertThat(awaitItem().weekStart).isEqualTo(WeekStart.SUNDAY)
            assertThat(preferences.currentWeekStart).isEqualTo(WeekStart.SUNDAY)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a stored choice is what the screen settles on`() = runTest {
        val viewModel = settingsViewModel(FakeUserPreferencesRepository(MeasurementSystem.IMPERIAL))

        viewModel.uiState.test {
            // The StateFlow is seeded with the default before the repository flow is collected,
            // so the stored value may arrive as a second emission.
            var latest = awaitItem().measurementSystem
            if (latest != MeasurementSystem.IMPERIAL) latest = awaitItem().measurementSystem
            assertThat(latest).isEqualTo(MeasurementSystem.IMPERIAL)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `choosing a fallback recipe language stores it and emits it back`() = runTest {
        val preferences = FakeUserPreferencesRepository()
        val viewModel = settingsViewModel(preferences)

        viewModel.uiState.test {
            assertThat(awaitItem().fallbackRecipeLanguage).isEqualTo(RecipeLanguage.ENGLISH)

            viewModel.onAction(SettingsAction.FallbackRecipeLanguageChanged(RecipeLanguage.SPANISH))

            assertThat(awaitItem().fallbackRecipeLanguage).isEqualTo(RecipeLanguage.SPANISH)
            assertThat(preferences.currentFallbackLanguage).isEqualTo(RecipeLanguage.SPANISH)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing one setting leaves the other alone`() = runTest {
        val preferences = FakeUserPreferencesRepository(initialFallbackLanguage = RecipeLanguage.SPANISH)
        val viewModel = settingsViewModel(preferences)

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.fallbackRecipeLanguage != RecipeLanguage.SPANISH) state = awaitItem()

            viewModel.onAction(SettingsAction.MeasurementSystemChanged(MeasurementSystem.METRIC))

            state = awaitItem()
            assertThat(state.measurementSystem).isEqualTo(MeasurementSystem.METRIC)
            assertThat(state.fallbackRecipeLanguage).isEqualTo(RecipeLanguage.SPANISH)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== delete account ====================

    @Test
    fun `an anonymous session has no delete-account section`() = runTest {
        val viewModel = settingsViewModel()
        collect(viewModel)

        assertThat(viewModel.deleteState).isEqualTo(DeleteAccountUiState.Unavailable)
    }

    @Test
    fun `a signed-in session shows the section, idle`() = runTest {
        val viewModel = signedInViewModel()
        collect(viewModel)

        assertThat(viewModel.deleteState).isEqualTo(DeleteAccountUiState.Idle)
    }

    @Test
    fun `tapping the button fetches the preview and opens the dialog with no choice preselected`() = runTest {
        val viewModel = signedInViewModel()
        val preview = preview(publicRecipeCount = 2)
        accountRepository.previewResult = AccountDeletionPreviewResult.Success(preview)

        viewModel.uiState.test {
            skipItems(1)
            viewModel.onAction(SettingsAction.DeleteAccountClicked)

            assertThat(expectMostRecentItem().deleteAccount)
                .isEqualTo(DeleteAccountUiState.Confirming(preview = preview, publicRecipes = null))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed preview is shown under the button`() = runTest {
        val viewModel = signedInViewModel()
        collect(viewModel)

        accountRepository.previewResult = AccountDeletionPreviewResult.Failed
        viewModel.onAction(SettingsAction.DeleteAccountClicked)
        assertThat(viewModel.deleteState)
            .isEqualTo(DeleteAccountUiState.PreviewFailed(DeleteAccountError.PREVIEW_FAILED))

        accountRepository.previewResult = AccountDeletionPreviewResult.SessionExpired
        viewModel.onAction(SettingsAction.DeleteAccountClicked)
        assertThat(viewModel.deleteState)
            .isEqualTo(DeleteAccountUiState.PreviewFailed(DeleteAccountError.SESSION_EXPIRED))
    }

    @Test
    fun `confirming with no public recipes deletes, signs out and emits AccountDeleted`() = runTest {
        val viewModel = signedInViewModel()
        collect(viewModel)
        viewModel.openDialog(preview(publicRecipeCount = 0))

        viewModel.effects.test {
            viewModel.onAction(SettingsAction.DeleteAccountConfirmed)

            assertThat(awaitItem()).isEqualTo(SettingsEffect.AccountDeleted)
        }
        assertThat(accountRepository.deleteCalls).containsExactly("hunter2" to null)
        // Anonymous again, so the section is gone.
        assertThat(viewModel.deleteState).isEqualTo(DeleteAccountUiState.Unavailable)
    }

    @Test
    fun `public recipes need a choice before the server is called`() = runTest {
        val viewModel = signedInViewModel()
        collect(viewModel)
        viewModel.openDialog(preview(publicRecipeCount = 2))

        viewModel.onAction(SettingsAction.DeleteAccountConfirmed)

        val state = viewModel.deleteState as DeleteAccountUiState.Confirming
        assertThat(state.error).isEqualTo(DeleteAccountError.PUBLIC_RECIPE_CHOICE_MISSING)
        assertThat(state.isSubmitting).isFalse()
        assertThat(accountRepository.deleteCalls).isEmpty()

        viewModel.onAction(SettingsAction.PublicRecipesChoiceChanged(PublicRecipeDisposition.DELETE))
        assertThat((viewModel.deleteState as DeleteAccountUiState.Confirming).error).isNull()

        viewModel.onAction(SettingsAction.DeleteAccountConfirmed)
        assertThat(accountRepository.deleteCalls).containsExactly("hunter2" to PublicRecipeDisposition.DELETE)
    }

    @Test
    fun `a wrong password stays in the dialog with an inline error that editing clears`() = runTest {
        val viewModel = signedInViewModel()
        collect(viewModel)
        accountRepository.deleteResult = DeleteAccountResult.WrongPassword
        viewModel.openDialog(preview())

        viewModel.onAction(SettingsAction.DeleteAccountConfirmed)

        val state = viewModel.deleteState as DeleteAccountUiState.Confirming
        assertThat(state.error).isEqualTo(DeleteAccountError.WRONG_PASSWORD)
        assertThat(state.isSubmitting).isFalse()
        assertThat(state.password).isEqualTo("hunter2")

        viewModel.onAction(SettingsAction.DeleteAccountPasswordChanged("hunter3"))
        assertThat((viewModel.deleteState as DeleteAccountUiState.Confirming).error).isNull()
    }

    @Test
    fun `public recipes the preview missed bring the choice up with the server's count`() = runTest {
        val viewModel = signedInViewModel()
        collect(viewModel)
        accountRepository.deleteResult = DeleteAccountResult.PublicRecipeChoiceRequired(publicRecipeCount = 1)
        viewModel.openDialog(preview(publicRecipeCount = 0))

        viewModel.onAction(SettingsAction.DeleteAccountConfirmed)

        val state = viewModel.deleteState as DeleteAccountUiState.Confirming
        assertThat(state.preview.publicRecipeCount).isEqualTo(1)
        assertThat(state.preview.requiresPublicRecipeChoice).isTrue()
        assertThat(state.publicRecipes).isNull()
        assertThat(state.error).isEqualTo(DeleteAccountError.PUBLIC_RECIPES_CHANGED)
    }

    @Test
    fun `server failures map to their own messages`() = runTest {
        val viewModel = signedInViewModel()
        collect(viewModel)
        viewModel.openDialog(preview())
        val cases = mapOf(
            DeleteAccountResult.Conflict to DeleteAccountError.CONFLICT,
            DeleteAccountResult.RateLimited to DeleteAccountError.RATE_LIMITED,
            DeleteAccountResult.SessionExpired to DeleteAccountError.SESSION_EXPIRED,
            DeleteAccountResult.Failed to DeleteAccountError.GENERIC,
        )

        for ((result, expected) in cases) {
            accountRepository.deleteResult = result
            viewModel.onAction(SettingsAction.DeleteAccountConfirmed)
            assertThat((viewModel.deleteState as DeleteAccountUiState.Confirming).error).isEqualTo(expected)
        }
    }

    @Test
    fun `the dialog can't be dismissed mid-request, and resets to idle otherwise`() = runTest {
        val viewModel = signedInViewModel()
        collect(viewModel)
        val gate = CompletableDeferred<Unit>()
        accountRepository.deleteGate = gate
        accountRepository.deleteResult = DeleteAccountResult.Conflict
        viewModel.openDialog(preview())

        viewModel.onAction(SettingsAction.DeleteAccountConfirmed)
        assertThat((viewModel.deleteState as DeleteAccountUiState.Confirming).isSubmitting).isTrue()
        viewModel.onAction(SettingsAction.DeleteAccountDismissed)
        assertThat(viewModel.deleteState).isInstanceOf(DeleteAccountUiState.Confirming::class.java)

        gate.complete(Unit)
        viewModel.onAction(SettingsAction.DeleteAccountDismissed)
        assertThat(viewModel.deleteState).isEqualTo(DeleteAccountUiState.Idle)
    }

    @Test
    fun `confirming with a blank password does nothing`() = runTest {
        val viewModel = signedInViewModel()
        collect(viewModel)
        viewModel.openDialog(preview())
        viewModel.onAction(SettingsAction.DeleteAccountPasswordChanged("  "))

        viewModel.onAction(SettingsAction.DeleteAccountConfirmed)

        assertThat(accountRepository.deleteCalls).isEmpty()
    }
}
