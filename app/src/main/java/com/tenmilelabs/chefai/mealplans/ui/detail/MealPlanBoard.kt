package com.tenmilelabs.chefai.mealplans.ui.detail

import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import java.util.UUID

/** One planned meal — a day plus a slot — and the recipe filling it. */
data class PlannedMeal(
    val dayId: UUID,
    val dayIndex: Int,
    val slot: MealSlot,
    val recipeId: UUID,
    /** `null` when the recipe is not on this device, e.g. it failed to sync down. */
    val recipe: RecipePreview?,
    val cookedAt: Long?,
) {
    val isCooked: Boolean get() = cookedAt != null

    /** "Day 1", used as the section heading and on cooked rows for provenance. */
    val dayLabel: String get() = "Day ${dayIndex + 1}"
}

/** A day's still-to-cook meals, grouped under one heading. */
data class DaySection(
    val dayIndex: Int,
    val label: String,
    val meals: List<PlannedMeal>,
)

/**
 * A meal plan split into what is left to cook and what is already done.
 *
 * Pure and Android-free so the grouping rules can be unit-tested directly;
 * [MealPlanDetailViewModel] only wraps this in its UI state.
 */
data class MealPlanBoard(
    /** Days that still have something left to cook, in plan order. */
    val upcoming: List<DaySection>,
    /** Every cooked meal, most recently cooked first. Rendered dimmed at the bottom. */
    val cooked: List<PlannedMeal>,
) {
    val cookedCount: Int get() = cooked.size
    val totalCount: Int get() = cooked.size + upcoming.sumOf { it.meals.size }

    /** Cooked share of the plan in `0f..1f`; `0f` for a plan with nothing in it yet. */
    val progress: Float get() = if (totalCount == 0) 0f else cookedCount.toFloat() / totalCount

    companion object {
        /**
         * Splits [mealPlan] into a day-grouped outstanding list and a flat cooked list.
         *
         * A day drops out of [upcoming] once all of its meals are cooked, so the top of the screen
         * always shows only outstanding work. Slots with no recipe assigned are skipped entirely —
         * an unfilled lunch is not a meal the user can cook or tick off.
         */
        fun from(
            mealPlan: MealPlan,
            recipeMap: Map<UUID, RecipePreview>,
        ): MealPlanBoard {
            val meals = mealPlan.days
                .sortedBy { it.dayIndex }
                .flatMap { day ->
                    // Lunch before dinner, matching how the day is eaten.
                    MealSlot.entries.mapNotNull { slot ->
                        val recipeId = day.recipeIdFor(slot) ?: return@mapNotNull null
                        PlannedMeal(
                            dayId = day.uuid,
                            dayIndex = day.dayIndex,
                            slot = slot,
                            recipeId = recipeId,
                            recipe = recipeMap[recipeId],
                            cookedAt = day.cookedAtFor(slot),
                        )
                    }
                }

            val (cooked, outstanding) = meals.partition { it.isCooked }

            return MealPlanBoard(
                upcoming = outstanding
                    .groupBy { it.dayIndex }
                    .toSortedMap()
                    .map { (dayIndex, dayMeals) ->
                        DaySection(
                            dayIndex = dayIndex,
                            label = "Day ${dayIndex + 1}",
                            meals = dayMeals,
                        )
                    },
                cooked = cooked.sortedByDescending { it.cookedAt },
            )
        }
    }
}
