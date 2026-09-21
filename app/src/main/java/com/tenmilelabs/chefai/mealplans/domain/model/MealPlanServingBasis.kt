package com.tenmilelabs.chefai.mealplans.domain.model

/**
 * Whose week the Meal Plans screen is showing — the "Just me" / "Family" toggle in its header.
 *
 * It picks two things at once, which is what makes it a basis rather than a filter: which plans the
 * week is projected from ([JUST_ME] personal plans, [FAMILY] household-shared ones — see ADR-014),
 * and how many servings the grocery aggregation shops for.
 *
 * The servings half **overrides** `MealPlanPreferences.servingsPerMeal` at display time and is
 * never written back — the same posture ADR-013 takes toward unit conversion: a way of reading the
 * plan, not an edit to it. See ADR-015 Decision 5.
 */
enum class MealPlanServingBasis {
    JUST_ME,
    FAMILY,
    ;

    /**
     * Servings per meal to aggregate the grocery list for.
     *
     * @param householdSize the cached household's member count; irrelevant to [JUST_ME], and a
     *   household of none cannot be shopped for, so it floors at one.
     */
    fun servingsPerMeal(householdSize: Int): Int = when (this) {
        JUST_ME -> 1
        FAMILY -> householdSize.coerceAtLeast(1)
    }

    companion object {
        val DEFAULT: MealPlanServingBasis = JUST_ME

        /** Tolerant of an unknown or absent stored name, which reads back as [DEFAULT]. */
        fun fromName(name: String?): MealPlanServingBasis =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
