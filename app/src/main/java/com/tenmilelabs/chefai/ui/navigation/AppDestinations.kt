package com.tenmilelabs.chefai.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home_screen")
    object Recipes : Screen("recipes_screen")
    object MealPlans : Screen("meal_plans_screen")
    object RecipeDetails: Screen("recipe_details_screen")
    object Settings : Screen("settings_screen")
}