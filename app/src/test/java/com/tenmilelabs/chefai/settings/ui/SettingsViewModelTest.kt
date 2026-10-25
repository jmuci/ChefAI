package com.tenmilelabs.chefai.settings.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.repository.FakeUserPreferencesRepository
import com.tenmilelabs.chefai.core.domain.model.RecipeLanguage
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class SettingsViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @Test
    fun `starts on as-written, so an existing library never changes under the user`() = runTest {
        val viewModel = SettingsViewModel(FakeUserPreferencesRepository())

        viewModel.uiState.test {
            assertThat(awaitItem().measurementSystem).isEqualTo(MeasurementSystem.AS_WRITTEN)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `choosing a system stores it and emits it back`() = runTest {
        val preferences = FakeUserPreferencesRepository()
        val viewModel = SettingsViewModel(preferences)

        viewModel.uiState.test {
            assertThat(awaitItem().measurementSystem).isEqualTo(MeasurementSystem.AS_WRITTEN)

            viewModel.onAction(SettingsAction.MeasurementSystemChanged(MeasurementSystem.METRIC))

            assertThat(awaitItem().measurementSystem).isEqualTo(MeasurementSystem.METRIC)
            assertThat(preferences.current).isEqualTo(MeasurementSystem.METRIC)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a stored choice is what the screen settles on`() = runTest {
        val viewModel = SettingsViewModel(FakeUserPreferencesRepository(MeasurementSystem.IMPERIAL))

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
        val viewModel = SettingsViewModel(preferences)

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
        val viewModel = SettingsViewModel(preferences)

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
}
