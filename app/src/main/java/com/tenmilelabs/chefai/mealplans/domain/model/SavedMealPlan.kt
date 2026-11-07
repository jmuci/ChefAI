package com.tenmilelabs.chefai.mealplans.domain.model

import java.util.UUID

/**
 * A meal plan the user chose to keep as a reusable template ("Saved" tab).
 *
 * A snapshot, not a view: it is its own row, independent of the dated [MealPlan] it was created
 * from, and later edits to that plan do not flow into it. [days] carry no cooked marks because a
 * template has no history.
 */
data class SavedMealPlan(
    val uuid: UUID,
    val userId: UUID,
    /** Required, trimmed, 1..[MAX_TITLE_LENGTH] chars. */
    val title: String,
    /** Local file path of the user's photo. Device-only — never synced. */
    val heroImagePath: String?,
    val preferences: MealPlanPreferences,
    val days: List<SavedMealPlanDay>,
    /** The dated plan this was saved from. Informational only: no foreign key, may be gone. */
    val sourceMealPlanId: UUID?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val MAX_TITLE_LENGTH: Int = 60
    }
}

/** One day of a [SavedMealPlan]'s lineup. Recipe ids are not foreign keys; they may dangle. */
data class SavedMealPlanDay(
    val dayIndex: Int,
    val lunchRecipeId: UUID?,
    val dinnerRecipeId: UUID?,
)
