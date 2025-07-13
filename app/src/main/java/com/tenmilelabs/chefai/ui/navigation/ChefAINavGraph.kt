package com.tenmilelabs.chefai.ui.navigation

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import com.tenmilelabs.chefai.ui.home.HomeScreen
import com.tenmilelabs.chefai.ui.mealplans.MealPlansScreen
import com.tenmilelabs.chefai.ui.recipes.RecipesScreen

/**
 * App destinations associated with screens users can navigate to.
 */
enum class AppDestinations(
    val title: String,
    val route: String
) {
    HOME(
        title = "Home",
        route = Screen.Home.route
    ),
    MEAL_PLANS(
        title = "Meal Plans",
        route = Screen.MealPlans.route
    ),
    RECIPES(
        title = "Recipes",
        route = Screen.Recipes.route
    ),
    RECIPE_DETAILS(
        title = "Recipe Details",
        route = Screen.RecipeDetails.route
    ),
    SETTINGS(
        title = "Settings",
        route = Screen.Settings.route
    ),
}

@Composable
fun ChefAINavGraph(modifier: Modifier) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val graph = navController.createGraph(startDestination = Screen.Home.route) {
        composable(route = Screen.Home.route) {
            HomeScreen()
        }
        composable(route = Screen.Recipes.route) {
            RecipesScreen(snackbarHostState = snackbarHostState)
        }
        composable(route = Screen.MealPlans.route) {
            MealPlansScreen()
        }
    }
    Scaffold(
        modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {ChefAITopAppBar(navController)},
        bottomBar = { BottomNavigationBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            graph = graph,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Preview(
    uiMode = UI_MODE_NIGHT_YES,
    name = "DefaultPreviewDark",
    showBackground = true
)
@Preview(
    uiMode = UI_MODE_NIGHT_NO,
    name = "DefaultPreviewLight",
    showBackground = true
)
@Composable
fun ChefAINavGraphPreview() {
    ChefAINavGraph(Modifier.fillMaxSize())
}