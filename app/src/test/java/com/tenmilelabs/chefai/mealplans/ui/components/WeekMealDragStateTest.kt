package com.tenmilelabs.chefai.mealplans.ui.components

import androidx.compose.ui.geometry.Rect
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.week.PlannedWeekMeal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WeekMealDragStateTest {

    private val planId = UUID.randomUUID()
    private val state = WeekMealDragState()

    /** Rows 40px tall with a 30px gap between days, like the padded week rows. */
    private fun meal(dayIndex: Int, cooked: Boolean = false, plan: UUID = planId): PlannedWeekMeal {
        val meal = PlannedWeekMeal(
            planId = plan,
            planName = "plan",
            dayId = UUID.randomUUID(),
            slot = MealSlot.DINNER,
            recipeId = UUID.randomUUID(),
            recipe = null,
            isCooked = cooked,
        )
        val top = dayIndex * 70f
        state.register(meal)
        state.reportBounds(meal, Rect(0f, top, 300f, top + 40f))
        return meal
    }

    @Test
    fun `drop in the gap between days lands on the nearest meal`() {
        // Given
        val monday = meal(0)
        val tuesday = meal(1)

        // When — centre moves from 20 to 65: past Monday's bottom, short of Tuesday's top (70)
        state.start(monday)
        state.drag(45f)

        // Then
        assertEquals(tuesday, state.drop())
    }

    @Test
    fun `drop over its own slot is no move`() {
        val monday = meal(0)
        meal(1)

        state.start(monday)
        state.drag(10f)

        assertNull(state.drop())
    }

    @Test
    fun `cooked meals and other plans' meals are never targets`() {
        val monday = meal(0)
        meal(1, cooked = true)
        meal(2, plan = UUID.randomUUID())

        state.start(monday)
        state.drag(70f)
        assertNull(state.target)
        state.drag(70f)
        assertNull(state.drop())
    }

    @Test
    fun `a meal cooked after it was laid out stops being a target`() {
        val monday = meal(0)
        val tuesday = meal(1)
        state.register(tuesday.copy(isCooked = true))

        state.start(monday)
        state.drag(70f)

        assertNull(state.drop())
    }

    @Test
    fun `drag reports target changes so the UI can tick a haptic`() {
        val monday = meal(0)
        meal(1)

        state.start(monday)
        assertTrue(state.drag(70f))
        assertEquals(false, state.drag(1f))
    }

    @Test
    fun `neighbour skips cooked meals and other plans`() {
        val monday = meal(0)
        meal(1, cooked = true)
        meal(2, plan = UUID.randomUUID())
        val thursday = meal(3)

        assertEquals(thursday, state.neighbour(monday, 1))
        assertEquals(monday, state.neighbour(thursday, -1))
        assertNull(state.neighbour(monday, -1))
    }
}
