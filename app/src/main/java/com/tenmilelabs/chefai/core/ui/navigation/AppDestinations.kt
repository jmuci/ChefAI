package com.tenmilelabs.chefai.core.ui.navigation

import androidx.annotation.StringRes
import androidx.navigation.NavHostController
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs.MEAL_PLAN_ID_ARG
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs.RECIPE_ID_ARG
import com.tenmilelabs.chefai.core.ui.navigation.ScreenBaseRoutes.RECIPE_DETAILS
import java.util.UUID

/**
 * Screens used in [AppDestinations].
 */
internal object ScreenBaseRoutes {
    const val HOME = "home_screen"
    const val MEAL_PLANS = "meal_plans_screen"
    const val RECIPES = "recipes_screen"
    const val RECIPE_DETAILS = "recipe_details_screen"
    const val CREATE_RECIPE = "create_recipe_screen"
    const val SETTINGS = "settings_screen"
    const val LOGIN = "login_screen"
    const val REGISTER = "register_screen"
    const val MEAL_PLAN_DETAIL = "meal_plan_detail"
    const val MEAL_PLAN_RECIPE_DETAIL = "meal_plan_recipe_detail"
    const val MEAL_PLAN_WIZARD = "meal_plan_wizard"
    const val MEAL_PLAN_WIZARD_BASICS = "meal_plan_wizard_basics"
    const val MEAL_PLAN_WIZARD_PREFERENCES = "meal_plan_wizard_preferences"
    const val MEAL_PLAN_WIZARD_ADVANCED = "meal_plan_wizard_advanced"
}

/**
 * Arguments used in [AppDestinations]
 */
object AppDestinationArgs {
    const val RECIPE_ID_ARG = "recipeUuid"
    const val MEAL_PLAN_ID_ARG = "mealPlanId"
}

/**
 * Destinations used in the [com.tenmilelabs.chefai.MainActivity].
 */
enum class AppDestinations(
    @param:StringRes val title: Int,
    val route: String,
) {
    HOME(R.string.app_dest_title_home, ScreenBaseRoutes.HOME),
    MEAL_PLANS(R.string.app_dest_title_meal_plans, ScreenBaseRoutes.MEAL_PLANS),
    RECIPES(R.string.app_dest_title_recipes, ScreenBaseRoutes.RECIPES),
    RECIPE_DETAILS(
        R.string.app_dest_title_recipe_details,
        "${ScreenBaseRoutes.RECIPE_DETAILS}/{$RECIPE_ID_ARG}"
    ),
    CREATE_RECIPE(R.string.app_dest_title_create_recipe, ScreenBaseRoutes.CREATE_RECIPE),
    MEAL_PLAN_DETAIL(
        R.string.app_dest_title_meal_plan_detail,
        "${ScreenBaseRoutes.MEAL_PLAN_DETAIL}/{$MEAL_PLAN_ID_ARG}"
    ),
    MEAL_PLAN_RECIPE_DETAIL(
        R.string.app_dest_title_recipe_details,
        "${ScreenBaseRoutes.MEAL_PLAN_RECIPE_DETAIL}/{$RECIPE_ID_ARG}"
    ),
    MEAL_PLAN_WIZARD(R.string.app_dest_title_meal_plan_wizard, ScreenBaseRoutes.MEAL_PLAN_WIZARD),
    SETTINGS(R.string.app_dest_title_settings, ScreenBaseRoutes.SETTINGS),
    LOGIN(R.string.app_dest_title_login, ScreenBaseRoutes.LOGIN),
    REGISTER(R.string.app_dest_title_register, ScreenBaseRoutes.REGISTER),
}


/**
 * Models the navigation actions in the app.
 */
class NavigationActions(private val navController: NavHostController) {
    fun navigateToRecipeDetail(recipeId: UUID) {
        navController.navigate("$RECIPE_DETAILS/$recipeId")
    }

    fun navigateToCreateRecipe() {
        navController.navigate(ScreenBaseRoutes.CREATE_RECIPE)
    }

    fun navigateToLogin() {
        navController.navigate(ScreenBaseRoutes.LOGIN) {
            launchSingleTop = true
        }
    }

    fun navigateToRegister() {
        navController.navigate(ScreenBaseRoutes.REGISTER)
    }

    fun navigateToMealPlanDetail(mealPlanId: UUID) {
        navController.navigate("${ScreenBaseRoutes.MEAL_PLAN_DETAIL}/$mealPlanId")
    }

    fun navigateToMealPlanRecipeDetail(recipeId: UUID) {
        navController.navigate("${ScreenBaseRoutes.MEAL_PLAN_RECIPE_DETAIL}/$recipeId")
    }

    fun navigateToMealPlanWizard() {
        navController.navigate(ScreenBaseRoutes.MEAL_PLAN_WIZARD)
    }

    fun navigateToHome() {
        navController.navigate(ScreenBaseRoutes.HOME) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }
}
