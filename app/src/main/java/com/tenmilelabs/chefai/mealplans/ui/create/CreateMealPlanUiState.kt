package com.tenmilelabs.chefai.mealplans.ui.create

import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference

data class CreateMealPlanUiState(
    val currentStep: Int = 0,
    val totalSteps: Int = 3,
    // Step 1: Basics
    val planLengthDays: Int = 5,
    val mealType: MealType = MealType.DINNER,
    val servingsPerMeal: Int = 2,
    // Step 2: Preferences
    val dietaryRestrictions: Set<DietaryRestriction> = emptySet(),
    val recipeSource: RecipeSource = RecipeSource.COLLECTION_ONLY,
    val maxPrepTimeMinutes: Int? = null,
    // Step 3: Advanced
    val batchCooking: Boolean = false,
    val leftoverFriendly: Boolean = false,
    val varietyPreference: VarietyPreference = VarietyPreference.MEDIUM,
    // Status
    val isSaving: Boolean = false,
)

sealed interface WizardAction {
    data class SetPlanLength(val days: Int) : WizardAction
    data class SetMealType(val type: MealType) : WizardAction
    data class SetServings(val count: Int) : WizardAction
    data class ToggleDietaryRestriction(val restriction: DietaryRestriction) : WizardAction
    data class SetRecipeSource(val source: RecipeSource) : WizardAction
    data class SetMaxPrepTime(val minutes: Int?) : WizardAction
    data class SetBatchCooking(val enabled: Boolean) : WizardAction
    data class SetLeftoverFriendly(val enabled: Boolean) : WizardAction
    data class SetVarietyPreference(val preference: VarietyPreference) : WizardAction
    data object SaveMealPlan : WizardAction
}

sealed interface CreateMealPlanEvent {
    data object MealPlanCreated : CreateMealPlanEvent
    data class ShowError(val message: Int) : CreateMealPlanEvent
}
