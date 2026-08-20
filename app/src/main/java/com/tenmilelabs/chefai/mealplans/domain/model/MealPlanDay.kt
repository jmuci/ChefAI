package com.tenmilelabs.chefai.mealplans.domain.model

import java.util.UUID

/**
 * One day of a [MealPlan], holding at most one lunch and one dinner.
 *
 * `*CookedAt` is the epoch-millis timestamp the user marked that slot cooked, or `null` while it is
 * still outstanding. It is per-slot rather than per-recipe so that the same recipe planned twice in
 * a week is tracked twice, and so that "cooked this week" resets naturally with each new plan.
 */
data class MealPlanDay(
    val uuid: UUID,
    val dayIndex: Int,
    val dinnerRecipeId: UUID?,
    val lunchRecipeId: UUID?,
    val dinnerCookedAt: Long? = null,
    val lunchCookedAt: Long? = null,
) {
    /** The recipe filling [slot], or `null` if the plan leaves that meal empty. */
    fun recipeIdFor(slot: MealSlot): UUID? = when (slot) {
        MealSlot.LUNCH -> lunchRecipeId
        MealSlot.DINNER -> dinnerRecipeId
    }

    /** When [slot] was marked cooked, or `null` if it still is not. */
    fun cookedAtFor(slot: MealSlot): Long? = when (slot) {
        MealSlot.LUNCH -> lunchCookedAt
        MealSlot.DINNER -> dinnerCookedAt
    }
}
