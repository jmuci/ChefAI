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
    /** True when user has fewer than [MIN_COLLECTION_RECIPES] — COLLECTION_ONLY is disabled. */
    val collectionTooSmall: Boolean = false,
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
    /** Plan created and generation completed — navigate to detail screen. */
    data class MealPlanReady(val mealPlanId: java.util.UUID) : CreateMealPlanEvent

    /** Plan saved locally but generation failed (offline/BE error) — navigate to detail as DRAFT. */
    data class MealPlanSavedAsDraft(val mealPlanId: java.util.UUID) : CreateMealPlanEvent

    data class ShowError(val message: Int) : CreateMealPlanEvent
}
