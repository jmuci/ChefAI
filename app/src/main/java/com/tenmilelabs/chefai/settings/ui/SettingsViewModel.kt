package com.tenmilelabs.chefai.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.core.domain.model.RecipeLanguage
import com.tenmilelabs.chefai.core.domain.model.WeekStart
import com.tenmilelabs.chefai.core.domain.repository.UserPreferencesRepository
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the settings screen renders. */
data class SettingsUiState(
    val measurementSystem: MeasurementSystem = MeasurementSystem.DEFAULT,
    val fallbackRecipeLanguage: RecipeLanguage = RecipeLanguage.DEFAULT,
    val weekStart: WeekStart = WeekStart.MONDAY,
)

/** Everything the user can do on the settings screen. */
sealed interface SettingsAction {
    data class MeasurementSystemChanged(val system: MeasurementSystem) : SettingsAction
    data class FallbackRecipeLanguageChanged(val language: RecipeLanguage) : SettingsAction
    data class WeekStartChanged(val weekStart: WeekStart) : SettingsAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesRepository.measurementSystem,
        userPreferencesRepository.fallbackRecipeLanguage,
        userPreferencesRepository.weekStart,
    ) { measurementSystem, fallbackRecipeLanguage, weekStart ->
        SettingsUiState(
            measurementSystem = measurementSystem,
            fallbackRecipeLanguage = fallbackRecipeLanguage,
            weekStart = weekStart,
        )
    }
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
            is SettingsAction.FallbackRecipeLanguageChanged -> viewModelScope.launch {
                userPreferencesRepository.setFallbackRecipeLanguage(action.language)
            }

            is SettingsAction.WeekStartChanged -> viewModelScope.launch {
                userPreferencesRepository.setWeekStart(action.weekStart)
            }
        }
    }
}
