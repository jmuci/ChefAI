package com.tenmilelabs.chefai.mealplans.ui.detail

import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.week.dateOf
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
 * One row of the detail screen: a planned meal, or a gap the plan expects a meal in ("Add recipe").
 * Rows of the same [dayIndex] are drawn as one day — the date once, the slots stacked beside it.
 */
sealed interface BoardSlot {
    val dayIndex: Int
    val slot: MealSlot

    data class Filled(val meal: PlannedMeal) : BoardSlot {
        override val dayIndex: Int get() = meal.dayIndex
        override val slot: MealSlot get() = meal.slot
    }

    data class Open(override val dayIndex: Int, override val slot: MealSlot) : BoardSlot
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
    /**
     * Every row the screen draws, filled or open, in day-then-slot order. Defaults to just the
     * [meals]; [from] adds the gaps.
     */
    val slots: List<BoardSlot> = meals.map { BoardSlot.Filled(it) },
) {
    val cookedCount: Int get() = meals.count { it.isCooked }
    val totalCount: Int get() = meals.size

    /** Cooked share of the plan in `0f..1f`; `0f` for a plan with nothing in it yet. */
    val progress: Float get() = if (totalCount == 0) 0f else cookedCount.toFloat() / totalCount

    companion object {
        /**
         * Flattens [mealPlan] into day-then-slot order (lunch before dinner, matching how the day
         * is eaten). Slots with no recipe assigned are left out of [meals] — an unfilled lunch is
         * not a meal the user can cook or tick off — but appear in [slots] as [BoardSlot.Open]
         * when the plan's meal type expects them, so a removed meal leaves a gap to refill.
         *
         * A plan with no days at all has never been generated: it gets no gaps, so the screen can
         * offer generation instead of a week of empty slots.
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

            if (mealPlan.days.isEmpty()) return MealPlanBoard(meals)

            val expected = mealPlan.preferences.mealType.slots
            val mealsByPosition = meals.associateBy { it.dayIndex to it.slot }
            val lastDayIndex = maxOf(
                mealPlan.preferences.planLengthDays - 1,
                mealPlan.days.maxOf { it.dayIndex },
            )
            val slots = (0..lastDayIndex).flatMap { dayIndex ->
                MealSlot.entries.mapNotNull { slot ->
                    val meal = mealsByPosition[dayIndex to slot]
                    when {
                        meal != null -> BoardSlot.Filled(meal)
                        slot in expected -> BoardSlot.Open(dayIndex, slot)
                        else -> null
                    }
                }
            }
            return MealPlanBoard(meals, slots)
        }
    }
}

/**
 * The calendar date [dayIndex] lands on, in the device's zone, for the Modernist day column (16)
 * and header date range. Same rule as the week view — see [dateOf].
 */
fun mealPlanDateFor(mealPlan: MealPlan, dayIndex: Int): LocalDate =
    mealPlan.dateOf(dayIndex, ZoneId.systemDefault())
