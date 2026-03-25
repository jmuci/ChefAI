package com.tenmilelabs.chefai.mealplans.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import javax.inject.Inject

@HiltViewModel
class CreateMealPlanViewModel @Inject constructor(
    private val mealPlanRepository: MealPlanRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateMealPlanUiState())
    val uiState: StateFlow<CreateMealPlanUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<CreateMealPlanEvent>()
    val uiEvents: SharedFlow<CreateMealPlanEvent> = _uiEvent.asSharedFlow()

    fun onAction(action: WizardAction) {
        when (action) {
            is WizardAction.SetPlanLength -> _uiState.update { it.copy(planLengthDays = action.days) }
            is WizardAction.SetMealType -> _uiState.update { it.copy(mealType = action.type) }
            is WizardAction.SetServings -> _uiState.update { it.copy(servingsPerMeal = action.count.coerceIn(1, 12)) }
            is WizardAction.ToggleDietaryRestriction -> toggleDietaryRestriction(action.restriction)
            is WizardAction.SetRecipeSource -> _uiState.update { it.copy(recipeSource = action.source) }
            is WizardAction.SetMaxPrepTime -> _uiState.update { it.copy(maxPrepTimeMinutes = action.minutes) }
            is WizardAction.SetBatchCooking -> _uiState.update { it.copy(batchCooking = action.enabled) }
            is WizardAction.SetLeftoverFriendly -> _uiState.update { it.copy(leftoverFriendly = action.enabled) }
            is WizardAction.SetVarietyPreference -> _uiState.update { it.copy(varietyPreference = action.preference) }
            is WizardAction.SaveMealPlan -> saveMealPlan()
        }
    }

    private fun toggleDietaryRestriction(restriction: DietaryRestriction) {
        _uiState.update { state ->
            val current = state.dietaryRestrictions
            val updated = if (restriction == DietaryRestriction.NONE) {
                // Selecting "None" clears all others
                if (DietaryRestriction.NONE in current) emptySet() else setOf(DietaryRestriction.NONE)
            } else {
                // Selecting a specific restriction removes NONE
                val withoutNone = current - DietaryRestriction.NONE
                if (restriction in withoutNone) withoutNone - restriction else withoutNone + restriction
            }
            state.copy(dietaryRestrictions = updated)
        }
    }

    private fun saveMealPlan() {
        val userId = sessionManager.getCurrentUserId() ?: return
        val state = _uiState.value
        if (state.isSaving) return

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val mealPlan = MealPlan(
                    uuid = UuidV7Generator.newId(),
                    userId = userId,
                    name = "${state.planLengthDays}-day meal plan",
                    preferences = MealPlanPreferences(
                        planLengthDays = state.planLengthDays,
                        mealType = state.mealType,
                        dietaryRestrictions = state.dietaryRestrictions,
                        recipeSource = state.recipeSource,
                        maxPrepTimeMinutes = state.maxPrepTimeMinutes,
                        servingsPerMeal = state.servingsPerMeal,
                        batchCooking = state.batchCooking,
                        leftoverFriendly = state.leftoverFriendly,
                        varietyPreference = state.varietyPreference,
                    ),
                    status = MealPlanStatus.DRAFT,
                    createdAt = now,
                    updatedAt = now,
                    days = emptyList(),
                )
                mealPlanRepository.createMealPlan(mealPlan)
                _uiEvent.emit(CreateMealPlanEvent.MealPlanCreated)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiEvent.emit(CreateMealPlanEvent.ShowError(R.string.meal_plan_save_error))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
