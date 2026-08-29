package com.tenmilelabs.chefai.settings.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.repository.FakeUserPreferencesRepository
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
}
