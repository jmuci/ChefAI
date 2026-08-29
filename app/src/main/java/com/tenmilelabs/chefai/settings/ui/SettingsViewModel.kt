package com.tenmilelabs.chefai.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.core.domain.repository.UserPreferencesRepository
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the settings screen renders. */
data class SettingsUiState(
    val measurementSystem: MeasurementSystem = MeasurementSystem.DEFAULT,
)

/** Everything the user can do on the settings screen. */
sealed interface SettingsAction {
    data class MeasurementSystemChanged(val system: MeasurementSystem) : SettingsAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = userPreferencesRepository.measurementSystem
        .map { SettingsUiState(measurementSystem = it) }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = SettingsUiState(),
        )

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.MeasurementSystemChanged -> viewModelScope.launch {
                userPreferencesRepository.setMeasurementSystem(action.system)
            }
        }
    }
}
