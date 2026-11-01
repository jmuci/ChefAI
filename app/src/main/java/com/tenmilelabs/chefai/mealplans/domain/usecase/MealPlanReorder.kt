package com.tenmilelabs.chefai.mealplans.domain.usecase

import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import java.util.UUID

/** A planned meal's position in a plan: a day row plus a slot. */
data class MealRef(val dayId: UUID, val slot: MealSlot)

/**
 * Drag-and-drop reordering of a plan's meals.
 *
 * The plan's occupied (day, slot) positions never change — only which recipe sits in which one.
 * Cooked meals are pinned: their position, recipe and cooked mark stay put, and only the
 * outstanding meals are reordered among the positions they already occupy. That keeps
 * `recipe_cook_events` (keyed by plan/day/slot/recipe) valid without having to migrate history.
 * Pure so the rule is unit-tested without Room.
 */
object MealPlanReorder {

    /**
     * Moves the outstanding meal at [from] to where the outstanding meal at [to] is, shifting the
     * meals in between by one place.
     *
     * @return the days that changed, or `null` when nothing would change: either ref is unknown,
     *   empty or cooked, or [from] equals [to].
     */
    fun move(days: List<MealPlanDay>, from: MealRef, to: MealRef): List<MealPlanDay>? {
        if (from == to) return null

        val outstanding = days
            .sortedBy { it.dayIndex }
            .flatMap { day ->
                MealSlot.entries.mapNotNull { slot ->
                    val recipeId = day.recipeIdFor(slot)
                    if (recipeId == null || day.cookedAtFor(slot) != null) {
                        null
                    } else {
                        MealRef(day.uuid, slot) to recipeId
                    }
                }
            }

        val fromIndex = outstanding.indexOfFirst { it.first == from }
        val toIndex = outstanding.indexOfFirst { it.first == to }
        if (fromIndex < 0 || toIndex < 0) return null

        val reordered = outstanding.map { it.second }.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        val recipeByRef = outstanding.map { it.first }.zip(reordered).toMap()

        return days.mapNotNull { day ->
            val lunch = recipeByRef[MealRef(day.uuid, MealSlot.LUNCH)] ?: day.lunchRecipeId
            val dinner = recipeByRef[MealRef(day.uuid, MealSlot.DINNER)] ?: day.dinnerRecipeId
            if (lunch == day.lunchRecipeId && dinner == day.dinnerRecipeId) {
                null
            } else {
                day.copy(lunchRecipeId = lunch, dinnerRecipeId = dinner)
            }
        }.takeIf { it.isNotEmpty() }
    }
}
