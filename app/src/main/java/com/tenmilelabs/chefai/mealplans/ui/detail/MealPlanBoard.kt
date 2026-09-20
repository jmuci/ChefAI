package com.tenmilelabs.chefai.mealplans.ui.detail

import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

    /** "Day 1" — used by the print document; the on-screen row shows a calendar date instead. */
    val dayLabel: String get() = "Day ${dayIndex + 1}"
}

/**
 * A meal plan's meals in day-then-slot order — the week as it is actually eaten. Cooked meals stay
 * in place, struck through, rather than being pulled into a separate pile: the Modernist detail
 * screen (16) reads top to bottom as the week's own order, not as "done" and "not done".
 *
 * Pure and Android-free so the ordering can be unit-tested directly; [MealPlanDetailViewModel]
 * only wraps this in its UI state.
 */
data class MealPlanBoard(
    val meals: List<PlannedMeal>,
) {
    val cookedCount: Int get() = meals.count { it.isCooked }
    val totalCount: Int get() = meals.size

    /** Cooked share of the plan in `0f..1f`; `0f` for a plan with nothing in it yet. */
    val progress: Float get() = if (totalCount == 0) 0f else cookedCount.toFloat() / totalCount

    companion object {
        /**
         * Flattens [mealPlan] into day-then-slot order (lunch before dinner, matching how the day
         * is eaten). Slots with no recipe assigned are skipped entirely — an unfilled lunch is not
         * a meal the user can cook or tick off.
         */
        fun from(
            mealPlan: MealPlan,
            recipeMap: Map<UUID, RecipePreview>,
        ): MealPlanBoard {
            val meals = mealPlan.days
                .sortedBy { it.dayIndex }
                .flatMap { day ->
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

            return MealPlanBoard(meals)
        }
    }
}

/**
 * The calendar date [dayIndex] lands on, treating the plan's `createdAt` (epoch millis) as day
 * zero. The domain model carries no per-day date — a plan is authored as "day 1, day 2, …" — so
 * this is a display-only derivation for the Modernist day column (16) and header date range, not a
 * stored fact. Two plans created on the same device on the same day always agree with each other.
 */
fun mealPlanDateFor(planCreatedAt: Long, dayIndex: Int): LocalDate =
    Instant.ofEpochMilli(planCreatedAt).atZone(ZoneId.systemDefault()).toLocalDate().plusDays(dayIndex.toLong())
