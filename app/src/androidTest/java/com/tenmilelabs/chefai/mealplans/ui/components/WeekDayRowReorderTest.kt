package com.tenmilelabs.chefai.mealplans.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.week.PlannedWeekMeal
import com.tenmilelabs.chefai.mealplans.domain.week.WeekDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/**
 * The week view's accessibility alternative to dragging: TalkBack's "Move earlier/later" custom
 * actions must target the same plan's neighbouring outstanding meal, skipping cooked ones.
 */
class WeekDayRowReorderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val planId = UUID.randomUUID()

    private fun meal(cooked: Boolean = false) = PlannedWeekMeal(
        planId = planId,
        planName = "plan",
        dayId = UUID.randomUUID(),
        slot = MealSlot.DINNER,
        recipeId = UUID.randomUUID(),
        recipe = null,
        isCooked = cooked,
    )

    @Test
    fun moveActions_targetSamePlanNeighbours_skippingCookedMeals() {
        // Given — three days: outstanding, cooked, outstanding
        val days = listOf(meal(), meal(cooked = true), meal()).mapIndexed { i, meal ->
            WeekDay(date = LocalDate.of(2026, 11, 2 + i), meals = listOf(meal))
        }
        val moves = mutableListOf<Pair<PlannedWeekMeal, PlannedWeekMeal>>()
        composeTestRule.setContent {
            ChefAITheme {
                val dragState = rememberWeekMealDragState()
                Column {
                    days.forEach { day ->
                        WeekDayRow(
                            day = day,
                            isToday = false,
                            onMealClick = {},
                            onAddMeal = {},
                            dragState = dragState,
                            onMoveMeal = { from, to -> moves += from to to },
                        )
                    }
                }
            }
        }
        val first = days[0].meals.single()
        val last = days[2].meals.single()

        // When — every row shows the same "recipe unavailable" title, so collect the rows that
        // expose custom actions in tree order rather than addressing them by text
        val rows = composeTestRule.onNode(isRoot()).fetchSemanticsNode().customActionsInOrder()

        // Then — the cooked middle meal exposes no move actions at all
        assertEquals(2, rows.size)
        composeTestRule.runOnIdle {
            assertTrue(rows[0].invoke("Move later"))
            assertTrue(rows[1].invoke("Move earlier"))
            // Nothing earlier than the first outstanding meal
            assertFalse(rows[0].invoke("Move earlier"))
        }
        assertEquals(listOf(first to last, last to first), moves)
    }

    private fun SemanticsNode.customActionsInOrder(): List<List<CustomAccessibilityAction>> = buildList {
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsActions.CustomActions)?.let(::add)
            node.children.forEach(::walk)
        }
        walk(this@customActionsInOrder)
    }

    private fun List<CustomAccessibilityAction>.invoke(label: String): Boolean =
        first { it.label == label }.action()
}
