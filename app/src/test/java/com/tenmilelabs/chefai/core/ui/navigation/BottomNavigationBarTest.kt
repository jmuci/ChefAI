package com.tenmilelabs.chefai.core.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavigationBarTest {

    @Test
    fun `null route selects no tab`() {
        TopLevelDestination.entries.forEach { tab ->
            assertFalse(isRouteInSection(null, tab))
        }
    }

    @Test
    fun `unmapped route selects no tab`() {
        val unmappedRoute = ScreenBaseRoutes.RECIPE_EDITOR

        TopLevelDestination.entries.forEach { tab ->
            assertFalse(isRouteInSection(unmappedRoute, tab))
        }
    }

    @Test
    fun `each tab's own route selects only that tab`() {
        TopLevelDestination.entries.forEach { tab ->
            val selected = TopLevelDestination.entries.filter {
                isRouteInSection(tab.appDestination.route, it)
            }
            assertEquals("route ${tab.appDestination.route}", listOf(tab), selected)
        }
    }

    @Test
    fun `home recipe detail keeps Home selected`() {
        assertSelectsOnly(TopLevelDestination.HOME, "${ScreenBaseRoutes.HOME_RECIPE_DETAIL}/abc-123")
    }

    @Test
    fun `search recipe detail keeps Search selected`() {
        assertSelectsOnly(TopLevelDestination.SEARCH, "${ScreenBaseRoutes.SEARCH_RECIPE_DETAIL}/abc-123")
    }

    @Test
    fun `recipe details from Recipes tab keeps Recipes selected`() {
        assertSelectsOnly(TopLevelDestination.RECIPES, "${ScreenBaseRoutes.RECIPE_DETAILS}/abc-123")
    }

    @Test
    fun `meal plan detail keeps Meal Plans selected`() {
        assertSelectsOnly(TopLevelDestination.MEAL_PLANS, "${ScreenBaseRoutes.MEAL_PLAN_DETAIL}/plan-1")
    }

    @Test
    fun `recipe opened from a meal plan keeps Meal Plans selected`() {
        assertSelectsOnly(
            TopLevelDestination.MEAL_PLANS,
            "${ScreenBaseRoutes.MEAL_PLAN_RECIPE_DETAIL}/abc-123/day-1/BREAKFAST",
        )
    }

    /** Asserts [route] selects [expected] and no other tab — a route can never highlight two tabs. */
    private fun assertSelectsOnly(expected: TopLevelDestination, route: String) {
        val selected = TopLevelDestination.entries.filter { isRouteInSection(route, it) }
        assertEquals("route $route", listOf(expected), selected)
    }

    @Test
    fun `every known recipe-detail route selects exactly one tab`() {
        val routesByExpectedTab = mapOf(
            "${ScreenBaseRoutes.HOME_RECIPE_DETAIL}/abc-123" to TopLevelDestination.HOME,
            "${ScreenBaseRoutes.SEARCH_RECIPE_DETAIL}/abc-123" to TopLevelDestination.SEARCH,
            "${ScreenBaseRoutes.RECIPE_DETAILS}/abc-123" to TopLevelDestination.RECIPES,
            "${ScreenBaseRoutes.MEAL_PLAN_RECIPE_DETAIL}/abc-123/day-1/DINNER" to TopLevelDestination.MEAL_PLANS,
        )

        routesByExpectedTab.forEach { (route, expectedTab) ->
            val matches = TopLevelDestination.entries.count { isRouteInSection(route, it) }
            assertTrue("route $route should select exactly one tab, matched $matches", matches == 1)
        }
    }
}
