package com.tenmilelabs.chefai.mealplans.ui.create

import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import java.time.LocalDate

data class CreateMealPlanUiState(
    val currentStep: Int = 0,
    val totalSteps: Int = 3,
    // Step 1: Basics
    /**
     * Day 0 of the new plan. `null` only until the default — the day after the last planned meal,
     * see `defaultMealPlanStartDate` — has been read from the user's existing plans.
     */
    val startDate: LocalDate? = null,
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
    // Step 2: keep this plan as a reusable "Saved" template
    val saveForLater: Boolean = false,
    /** Kept when the toggle is switched off, but nothing is saved then. */
    val savedTitle: String = "",
    /** Local file the picked photo was copied to; `null` when there is none. */
    val savedImagePath: String? = null,
    val isImportingImage: Boolean = false,
    // Status
    val isSaving: Boolean = false,
) {
    /** The Preferences step's Next is blocked while a saved plan is missing its required title. */
    val canProceedFromPreferences: Boolean
        get() = !saveForLater || savedTitle.isNotBlank()
}

sealed interface WizardAction {
    data class SetStartDate(val date: LocalDate) : WizardAction
    data class SetPlanLength(val days: Int) : WizardAction
    data class SetMealType(val type: MealType) : WizardAction
    data class SetServings(val count: Int) : WizardAction
    data class ToggleDietaryRestriction(val restriction: DietaryRestriction) : WizardAction
    data class SetRecipeSource(val source: RecipeSource) : WizardAction
    data class SetMaxPrepTime(val minutes: Int?) : WizardAction
    data class SetBatchCooking(val enabled: Boolean) : WizardAction
    data class SetLeftoverFriendly(val enabled: Boolean) : WizardAction
    data class SetVarietyPreference(val preference: VarietyPreference) : WizardAction
    data class SetSaveForLater(val enabled: Boolean) : WizardAction
    data class SetSavedTitle(val title: String) : WizardAction

    /** The photo the user picked for the saved plan; `null` when the picker was dismissed. */
    data class SavedImagePicked(val uri: String?) : WizardAction
    data object RemoveSavedImage : WizardAction

    /**
     * The UI asking the screen to open the photo picker. Launching an activity is the screen's
     * job, so the ViewModel ignores it; the result comes back as [SavedImagePicked].
     */
    data object RequestSavedImagePick : WizardAction
    data object SaveMealPlan : WizardAction
}

/**
 * One-shot wizard outcomes. `overlappingPlanName` names an existing plan whose dates the new one
 * shares (`null` when none) — a non-blocking notice, since the week view shows both.
 */
sealed interface CreateMealPlanEvent {
    /** Plan created and generation completed — navigate to detail screen. */
    data class MealPlanReady(
        val mealPlanId: java.util.UUID,
        val overlappingPlanName: String? = null,
        /** The plan was created but could not also be saved for later. */
        val savedPlanFailed: Boolean = false,
    ) : CreateMealPlanEvent

    /** Plan saved locally but generation failed (offline/BE error) — navigate to detail as DRAFT. */
    data class MealPlanSavedAsDraft(
        val mealPlanId: java.util.UUID,
        val overlappingPlanName: String? = null,
        /** The plan was created but could not also be saved for later. */
        val savedPlanFailed: Boolean = false,
    ) : CreateMealPlanEvent

    data class ShowError(val message: Int) : CreateMealPlanEvent
}
