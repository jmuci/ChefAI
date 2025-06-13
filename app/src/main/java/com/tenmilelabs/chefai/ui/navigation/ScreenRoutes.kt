package com.tenmilelabs.chefai.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home_screen")
    object Recipes : Screen("recipes_screen")
    object MealPlans : Screen("meal_plans_screen")
}