package com.tenmilelabs.chefai.core.ui.navigation

import android.app.Application
import android.os.Build
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tab highlighting ([owningTab]) and tab taps ([selectTab]) against a graph that mirrors the flat
 * tab wiring in `ChefAINavGraph` (same routes, no composable content).
 */
@Config(application = Application::class, sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
@RunWith(RobolectricTestRunner::class)
class BottomNavigationBarTest {

    private val navController = NavHostController(RuntimeEnvironment.getApplication()).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = AppDestinations.HOME.route) {
            TopLevelDestination.entries.forEach { tab -> composable(tab.appDestination.route) {} }
            composable(AppDestinations.RECIPE_DETAILS.route) {}
            composable(AppDestinations.HOME_RECIPE_DETAIL.route) {}
            composable(AppDestinations.SEARCH_RECIPE_DETAIL.route) {}
            composable(AppDestinations.MEAL_PLAN_DETAIL.route) {}
            composable(AppDestinations.MEAL_PLAN_RECIPE_DETAIL.route) {}
            composable(AppDestinations.SETTINGS.route) {}
        }
    }
    private val navActions = NavigationActions(navController)

    private val currentRoute: String?
        get() = navController.currentBackStackEntry?.destination?.route

    @Test
    fun `no graph selects no tab`() {
        val bare = NavHostController(RuntimeEnvironment.getApplication())

        assertThat(bare.owningTab()).isNull()
    }

    @Test
    fun `the start destination selects Home`() {
        assertThat(navController.owningTab()).isEqualTo(TopLevelDestination.HOME)
    }

    @Test
    fun `each tab root selects only that tab`() {
        TopLevelDestination.entries.forEach { tab ->
            navController.selectTab(tab)

            assertThat(currentRoute).isEqualTo(tab.appDestination.route)
            assertThat(navController.owningTab()).isEqualTo(tab)
        }
    }

    @Test
    fun `a plan created from a recipe highlights Recipes, the stack it lives in`() {
        // Given Recipes tab -> recipe -> (wizard, popped) -> plan detail
        navController.selectTab(TopLevelDestination.RECIPES)
        navActions.navigateToRecipeDetail(UUID.randomUUID())
        navActions.navigateToMealPlanDetail(UUID.randomUUID())

        // Then the plan detail belongs to the Recipes tab, not Meal Plans
        assertThat(currentRoute).isEqualTo(AppDestinations.MEAL_PLAN_DETAIL.route)
        assertThat(navController.owningTab()).isEqualTo(TopLevelDestination.RECIPES)
    }

    @Test
    fun `re-tapping the selected tab pops to its root`() {
        // Given the Recipes stack ends in a plan detail
        navController.selectTab(TopLevelDestination.RECIPES)
        navActions.navigateToRecipeDetail(UUID.randomUUID())
        navActions.navigateToMealPlanDetail(UUID.randomUUID())

        // When Recipes is tapped again
        navController.selectTab(TopLevelDestination.RECIPES)

        // Then the Recipes list shows, and Back leaves the tab rather than revisiting the plan
        assertThat(currentRoute).isEqualTo(AppDestinations.RECIPES.route)
        navController.popBackStack()
        assertThat(currentRoute).isEqualTo(AppDestinations.HOME.route)
    }

    @Test
    fun `switching away and back restores the deep stack, and a second tap pops it`() {
        // Given the Recipes stack ends in a plan detail
        navController.selectTab(TopLevelDestination.RECIPES)
        navActions.navigateToRecipeDetail(UUID.randomUUID())
        navActions.navigateToMealPlanDetail(UUID.randomUUID())

        // When the user visits Meal Plans, the Meal Plans list shows — not the Recipes stack's plan
        navController.selectTab(TopLevelDestination.MEAL_PLANS)
        assertThat(currentRoute).isEqualTo(AppDestinations.MEAL_PLANS.route)
        assertThat(navController.owningTab()).isEqualTo(TopLevelDestination.MEAL_PLANS)

        // When they come back to Recipes, its stack is restored where they left it
        navController.selectTab(TopLevelDestination.RECIPES)
        assertThat(currentRoute).isEqualTo(AppDestinations.MEAL_PLAN_DETAIL.route)
        assertThat(navController.owningTab()).isEqualTo(TopLevelDestination.RECIPES)

        // And tapping Recipes again pops to its root
        navController.selectTab(TopLevelDestination.RECIPES)
        assertThat(currentRoute).isEqualTo(AppDestinations.RECIPES.route)
    }

    @Test
    fun `a recipe opened from a meal plan keeps Meal Plans selected, and re-tap pops to the list`() {
        navController.selectTab(TopLevelDestination.MEAL_PLANS)
        navActions.navigateToMealPlanDetail(UUID.randomUUID())
        navActions.navigateToMealPlanRecipeDetail(UUID.randomUUID(), UUID.randomUUID(), MealSlot.DINNER)

        assertThat(navController.owningTab()).isEqualTo(TopLevelDestination.MEAL_PLANS)

        navController.selectTab(TopLevelDestination.MEAL_PLANS)
        assertThat(currentRoute).isEqualTo(AppDestinations.MEAL_PLANS.route)
    }

    @Test
    fun `re-tapping Home pops a recipe opened from Home`() {
        navActions.navigateToHomeRecipeDetail(UUID.randomUUID())
        assertThat(navController.owningTab()).isEqualTo(TopLevelDestination.HOME)

        navController.selectTab(TopLevelDestination.HOME)

        assertThat(currentRoute).isEqualTo(AppDestinations.HOME.route)
    }

    @Test
    fun `a non-tab screen opened inside a tab keeps that tab selected`() {
        navController.selectTab(TopLevelDestination.SEARCH)
        navActions.navigateToSearchRecipeDetail(UUID.randomUUID())
        navActions.navigateToSettings()

        assertThat(navController.owningTab()).isEqualTo(TopLevelDestination.SEARCH)
    }

    @Test
    fun `re-tapping a tab already at its root changes nothing`() {
        navController.selectTab(TopLevelDestination.SEARCH)

        navController.selectTab(TopLevelDestination.SEARCH)

        assertThat(currentRoute).isEqualTo(AppDestinations.SEARCH.route)
        navController.popBackStack()
        assertThat(currentRoute).isEqualTo(AppDestinations.HOME.route)
    }
}
