package com.tenmilelabs.chefai.core.ui.navigation

import android.app.Application
import android.os.Build
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.navigation.compose.navigation
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Back-stack behaviour of [NavigationActions.navigateToCreatedMealPlan], against a graph that
 * mirrors the wizard wiring in `ChefAINavGraph` (same routes, no composable content).
 */
@Config(application = Application::class, sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
@RunWith(RobolectricTestRunner::class)
class MealPlanWizardNavigationTest {

    private val recipeDetailsPattern =
        "${ScreenBaseRoutes.RECIPE_DETAILS}/{${AppDestinationArgs.RECIPE_ID_ARG}}"

    private val navController = NavHostController(RuntimeEnvironment.getApplication()).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = ScreenBaseRoutes.MEAL_PLANS) {
            composable(ScreenBaseRoutes.MEAL_PLANS) {}
            composable(recipeDetailsPattern) {}
            composable(AppDestinations.MEAL_PLAN_DETAIL.route) {}
            navigation(
                startDestination = ScreenBaseRoutes.MEAL_PLAN_WIZARD_BASICS,
                route = ScreenBaseRoutes.MEAL_PLAN_WIZARD_GRAPH,
                arguments = listOf(
                    navArgument(AppDestinationArgs.RECIPE_ID_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                composable(ScreenBaseRoutes.MEAL_PLAN_WIZARD_BASICS) {}
                composable(ScreenBaseRoutes.MEAL_PLAN_WIZARD_PREFERENCES) {}
                composable(ScreenBaseRoutes.MEAL_PLAN_WIZARD_ADVANCED) {}
            }
        }
    }
    private val navActions = NavigationActions(navController)

    @Test
    fun `wizard launched from a recipe is gone after the plan is created`() {
        // Given the wizard was opened from a recipe's details screen, and the user reached step 3
        val recipeId = UUID.randomUUID()
        navController.navigate("${ScreenBaseRoutes.RECIPE_DETAILS}/$recipeId")
        navActions.navigateToMealPlanWizard(recipeId)
        navController.navigate(ScreenBaseRoutes.MEAL_PLAN_WIZARD_PREFERENCES)
        navController.navigate(ScreenBaseRoutes.MEAL_PLAN_WIZARD_ADVANCED)

        // When the plan is created
        navActions.navigateToCreatedMealPlan(UUID.randomUUID())

        // Then Back from the plan detail lands on the recipe details screen
        assertThat(navController.currentBackStackEntry?.destination?.route)
            .isEqualTo(AppDestinations.MEAL_PLAN_DETAIL.route)
        navController.popBackStack()
        assertThat(navController.currentBackStackEntry?.destination?.route)
            .isEqualTo(recipeDetailsPattern)
    }

    @Test
    fun `wizard launched from the plans list is gone after the plan is created`() {
        // Given the wizard was opened from the Meal Plans tab, and the user reached step 3
        navActions.navigateToMealPlanWizard()
        navController.navigate(ScreenBaseRoutes.MEAL_PLAN_WIZARD_PREFERENCES)
        navController.navigate(ScreenBaseRoutes.MEAL_PLAN_WIZARD_ADVANCED)

        // When the plan is created
        navActions.navigateToCreatedMealPlan(UUID.randomUUID())

        // Then Back from the plan detail lands on the plans list
        navController.popBackStack()
        assertThat(navController.currentBackStackEntry?.destination?.route)
            .isEqualTo(ScreenBaseRoutes.MEAL_PLANS)
    }
}
