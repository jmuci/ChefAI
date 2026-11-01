package com.tenmilelabs.chefai.mealplans.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tenmilelabs.chefai.mealplans.domain.week.PlannedWeekMeal

/** Identifies a meal in the week: a plan's (day, slot) is unique, but two plans can share a date. */
private fun PlannedWeekMeal.dragKey(): String = "$planId-$dayId-$slot"

/**
 * Hoisted drag state for the week view. Meals report their root-space bounds; a drag is resolved
 * on release by finding which other meal the dragged row's centre ended up over.
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

    private val bounds = mutableMapOf<String, Rect>()
    private val meals = mutableMapOf<String, PlannedWeekMeal>()

    fun isDragged(meal: PlannedWeekMeal): Boolean = dragged?.dragKey() == meal.dragKey()

    fun reportBounds(meal: PlannedWeekMeal, rect: Rect) {
        bounds[meal.dragKey()] = rect
        meals[meal.dragKey()] = meal
    }

    fun forget(meal: PlannedWeekMeal) {
        bounds.remove(meal.dragKey())
        meals.remove(meal.dragKey())
    }

    fun start(meal: PlannedWeekMeal) {
        dragged = meal
        offsetY = 0f
    }

    fun drag(deltaY: Float) {
        offsetY += deltaY
    }

    fun cancel() {
        dragged = null
        offsetY = 0f
    }

    /** Ends the drag and returns the meal it was dropped on, or `null` if there is no valid target. */
    fun drop(): PlannedWeekMeal? {
        val source = dragged ?: return null
        val start = bounds[source.dragKey()]
        val dy = offsetY
        cancel()
        start ?: return null
        val centreY = start.center.y + dy
        val centreX = start.center.x
        return meals.values.firstOrNull { candidate ->
            candidate.planId == source.planId &&
                !candidate.isCooked &&
                candidate.dragKey() != source.dragKey() &&
                bounds[candidate.dragKey()]?.contains(Offset(centreX, centreY)) == true
        }
    }
}

@Composable
fun rememberWeekMealDragState(): WeekMealDragState =
    remember { WeekMealDragState() }
