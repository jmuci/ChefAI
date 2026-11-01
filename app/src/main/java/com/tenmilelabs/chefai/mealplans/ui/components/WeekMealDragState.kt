package com.tenmilelabs.chefai.mealplans.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.tenmilelabs.chefai.mealplans.domain.week.PlannedWeekMeal
import kotlin.math.abs

/** Identifies a meal in the week: a plan's (day, slot) is unique, but two plans can share a date. */
private fun PlannedWeekMeal.dragKey(): String = "$planId-$dayId-$slot"

/**
 * Hoisted drag state for the week view. Meals report their root-space bounds; while a drag is in
 * flight the state keeps [target] — the valid meal nearest the dragged row's centre — so the UI can
 * highlight it, and [drop] commits to it.
 *
 * Only outstanding meals of the *same plan* are valid drop targets: reordering is per plan (see
 * [com.tenmilelabs.chefai.mealplans.domain.usecase.MealPlanReorder]), and dropping onto another
 * plan's meal or an empty day has no defined meaning.
 */
@Stable
class WeekMealDragState {
    var dragged: PlannedWeekMeal? by mutableStateOf(null)
        private set
    var offsetY: Float by mutableFloatStateOf(0f)
        private set

    /** Where the dragged meal would land if released now, or `null` if nowhere valid. */
    var target: PlannedWeekMeal? by mutableStateOf(null)
        private set

    private val bounds = mutableMapOf<String, Rect>()
    private val meals = mutableMapOf<String, PlannedWeekMeal>()

    fun isDragged(meal: PlannedWeekMeal): Boolean = dragged?.dragKey() == meal.dragKey()

    fun isTarget(meal: PlannedWeekMeal): Boolean = target?.dragKey() == meal.dragKey()

    /**
     * Called on every composition, not just on layout: `onGloballyPositioned` does not fire when a
     * meal's cooked mark or recipe changes in place, and a stale copy would keep a now-cooked meal
     * as a drop target.
     */
    fun register(meal: PlannedWeekMeal) {
        meals[meal.dragKey()] = meal
    }

    fun reportBounds(meal: PlannedWeekMeal, rect: Rect) {
        bounds[meal.dragKey()] = rect
    }

    fun forget(meal: PlannedWeekMeal) {
        bounds.remove(meal.dragKey())
        meals.remove(meal.dragKey())
    }

    fun start(meal: PlannedWeekMeal) {
        dragged = meal
        offsetY = 0f
        target = null
    }

    /** @return `true` when the hovered drop target changed, so the caller can tick a haptic. */
    fun drag(deltaY: Float): Boolean {
        offsetY += deltaY
        val previous = target
        target = resolveTarget()
        return target?.dragKey() != previous?.dragKey()
    }

    fun cancel() {
        dragged = null
        offsetY = 0f
        target = null
    }

    /** Ends the drag and returns the meal it was dropped on, or `null` if there is no valid target. */
    fun drop(): PlannedWeekMeal? {
        val result = target
        cancel()
        return result
    }

    /**
     * The same plan's previous ([step] = -1) or next ([step] = 1) outstanding meal in week order —
     * the accessibility alternative to dragging. Rows are stacked top to bottom in date order, so
     * ordering by laid-out position is week order.
     */
    fun neighbour(meal: PlannedWeekMeal, step: Int): PlannedWeekMeal? {
        val ordered = candidatesFor(meal, includeSelf = true)
            .sortedWith(compareBy({ bounds.getValue(it.dragKey()).top }, { it.slot.ordinal }))
        val index = ordered.indexOfFirst { it.dragKey() == meal.dragKey() }
        if (index < 0) return null
        return ordered.getOrNull(index + step)
    }

    /**
     * Nearest valid meal to the dragged row's centre. Days sit apart (padding, rules, empty days),
     * so strict containment would make most drop positions dead zones; instead any candidate within
     * half a row of the centre counts, and the row's own original slot cancels the move.
     */
    private fun resolveTarget(): PlannedWeekMeal? {
        val source = dragged ?: return null
        val start = bounds[source.dragKey()] ?: return null
        val centreY = start.center.y + offsetY
        if (centreY in start.top..start.bottom) return null
        val slack = start.height / 2f
        return candidatesFor(source, includeSelf = false)
            .map { it to bounds.getValue(it.dragKey()) }
            .filter { (_, rect) -> centreY in (rect.top - slack)..(rect.bottom + slack) }
            .minByOrNull { (_, rect) -> abs(rect.center.y - centreY) }
            ?.first
    }

    private fun candidatesFor(source: PlannedWeekMeal, includeSelf: Boolean): List<PlannedWeekMeal> =
        meals.values.filter { candidate ->
            candidate.planId == source.planId &&
                !candidate.isCooked &&
                bounds.containsKey(candidate.dragKey()) &&
                (includeSelf || candidate.dragKey() != source.dragKey())
        }
}

@Composable
fun rememberWeekMealDragState(): WeekMealDragState =
    remember { WeekMealDragState() }
