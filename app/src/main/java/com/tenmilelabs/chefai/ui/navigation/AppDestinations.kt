package com.tenmilelabs.chefai.ui.navigation

import androidx.annotation.StringRes
import androidx.navigation.NavHostController
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.ui.navigation.AppDestinationArgs.RECIPE_ID_ARG
import com.tenmilelabs.chefai.ui.navigation.ScreenBaseRoutes.RECIPE_DETAILS

/**
 * Screens used in [AppDestinations].
 */
private object ScreenBaseRoutes {
    const val HOME = "home_screen"
    const val MEAL_PLANS = "meal_plans_screen"
    const val RECIPES = "recipes_screen"
    const val RECIPE_DETAILS = "recipe_details_screen"
    const val SETTINGS = "settings_screen"
}

/**
 * Arguments used in [AppDestinations]
 */
object AppDestinationArgs {
    const val RECIPE_ID_ARG = "recipeUuid"
}

/**
 * Destinations used in the [com.tenmilelabs.chefai.MainActivity].
 */
enum class AppDestinations(
    @StringRes val title: Int,
    val route: String,
) {
    HOME(R.string.app_dest_title_home, ScreenBaseRoutes.HOME),
    MEAL_PLANS(R.string.app_dest_title_meal_plans, ScreenBaseRoutes.MEAL_PLANS),
    RECIPES(R.string.app_dest_title_recipes, ScreenBaseRoutes.RECIPES),
    RECIPE_DETAILS(
        R.string.app_dest_title_recipe_details,
        "${ScreenBaseRoutes.RECIPE_DETAILS}/{$RECIPE_ID_ARG}"
    ),
    SETTINGS(R.string.app_dest_title_settings, ScreenBaseRoutes.SETTINGS),
}


/**
 * Models the navigation actions in the app.
 */
class NavigationActions(private val navController: NavHostController) {
    fun navigateToRecipeDetail(recipeId: String) {
        navController.navigate("$RECIPE_DETAILS/$recipeId")
    }

}
