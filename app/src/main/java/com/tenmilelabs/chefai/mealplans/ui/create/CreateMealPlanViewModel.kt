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
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.usecase.GenerateMealPlanUseCase
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject

@HiltViewModel
class CreateMealPlanViewModel @Inject constructor(
    private val mealPlanRepository: MealPlanRepository,
    private val sessionManager: SessionManager,
    private val recipesRepository: RecipesRepository,
    private val generateMealPlanUseCase: GenerateMealPlanUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateMealPlanUiState())
    val uiState: StateFlow<CreateMealPlanUiState> = _uiState.asStateFlow()

    // Buffered Channel: generation takes seconds, and an event emitted into a rendezvous
    // SharedFlow while the collector is being re-created (rotation) was dropped — leaving the user
    // on the wizard with the plan already saved, one tap away from a duplicate.
    private val _uiEvent = Channel<CreateMealPlanEvent>(Channel.BUFFERED)
    val uiEvents: Flow<CreateMealPlanEvent> = _uiEvent.receiveAsFlow()

    init {
        loadRecipeCount()
    }

    private fun loadRecipeCount() {
        val userId = sessionManager.getCurrentUserId() ?: return
        viewModelScope.launch {
            val count = recipesRepository.getRecipeCountForUser(userId)
            val tooSmall = count < MIN_COLLECTION_RECIPES
            _uiState.update {
                it.copy(
                    collectionTooSmall = tooSmall,
                    // Auto-switch to INCLUDE_PUBLIC when collection is too small
                    recipeSource = if (tooSmall) RecipeSource.INCLUDE_PUBLIC else it.recipeSource,
                )
            }
        }
    }

    fun onAction(action: WizardAction) {
        when (action) {
            is WizardAction.SetPlanLength -> _uiState.update { it.copy(planLengthDays = action.days) }
            is WizardAction.SetMealType -> _uiState.update { it.copy(mealType = action.type) }
            is WizardAction.SetServings -> _uiState.update { it.copy(servingsPerMeal = action.count.coerceIn(1, 12)) }
            is WizardAction.ToggleDietaryRestriction -> toggleDietaryRestriction(action.restriction)
            is WizardAction.SetRecipeSource -> {
                // Guard: don't allow COLLECTION_ONLY when collection is too small
                if (action.source == RecipeSource.COLLECTION_ONLY && _uiState.value.collectionTooSmall) return
                _uiState.update { it.copy(recipeSource = action.source) }
            }
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

        val preferences = MealPlanPreferences(
            planLengthDays = state.planLengthDays,
            mealType = state.mealType,
            dietaryRestrictions = state.dietaryRestrictions,
            recipeSource = state.recipeSource,
            maxPrepTimeMinutes = state.maxPrepTimeMinutes,
            servingsPerMeal = state.servingsPerMeal,
            batchCooking = state.batchCooking,
            leftoverFriendly = state.leftoverFriendly,
            varietyPreference = state.varietyPreference,
        )

        viewModelScope.launch {
            val mealPlanId: UUID
            try {
                val now = System.currentTimeMillis()
                mealPlanId = UuidV7Generator.newId()
                val mealPlan = MealPlan(
                    uuid = mealPlanId,
                    userId = userId,
                    name = "${state.planLengthDays}-day meal plan",
                    preferences = preferences,
                    status = MealPlanStatus.DRAFT,
                    createdAt = now,
                    updatedAt = now,
                    days = emptyList(),
                )
                mealPlanRepository.createMealPlan(mealPlan)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isSaving = false) }
                _uiEvent.send(CreateMealPlanEvent.ShowError(R.string.meal_plan_save_error))
                return@launch
            }

            // Plan saved locally — now attempt immediate generation, then DRAFT so the user can
            // retry from the detail screen. Which generation path runs depends on session type and
            // recipeSource: see GenerateMealPlanUseCase's doc.
            // The plan is already saved; a failure here (e.g. the local fallback's Room reads)
            // just leaves it as a draft the detail screen can retry, rather than crashing.
            val filled = try {
                generateMealPlanUseCase(mealPlanId, preferences)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Meal plan generation failed for $mealPlanId")
                false
            }

            _uiState.update { it.copy(isSaving = false) }
            _uiEvent.send(
                if (filled) {
                    CreateMealPlanEvent.MealPlanReady(mealPlanId)
                } else {
                    CreateMealPlanEvent.MealPlanSavedAsDraft(mealPlanId)
                }
            )
        }
    }

    companion object {
        internal const val MIN_COLLECTION_RECIPES = 20
    }
}
