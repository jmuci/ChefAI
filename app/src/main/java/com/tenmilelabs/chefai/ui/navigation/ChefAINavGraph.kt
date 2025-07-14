package com.tenmilelabs.chefai.ui.navigation

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.ui.home.HomeScreen
import com.tenmilelabs.chefai.ui.mealplans.MealPlansScreen
import com.tenmilelabs.chefai.ui.recipeDetails.RecipeDetailsScreen
import com.tenmilelabs.chefai.ui.recipes.RecipesScreen

@Composable
fun ChefAINavGraph(
    modifier: Modifier,
    navController: NavHostController = rememberNavController(),
    navActions: NavigationActions = remember(navController) {
        NavigationActions(navController)
    }
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val userMessages = remember { mutableListOf<Int>() } //TODO list or single message?

    val graph = navController.createGraph(startDestination = AppDestinations.HOME.route) {
        composable(route = AppDestinations.HOME.route) {
            HomeScreen()
        }
        composable(route = AppDestinations.RECIPES.route) {
            RecipesScreen(
                snackbarHostState = snackbarHostState,
                onRecipeCardClick = { recipeUid ->
                    navActions.navigateToRecipeDetail(recipeUid)
                }
            )
        }
        composable(route = AppDestinations.MEAL_PLANS.route) {
            MealPlansScreen()
        }
        composable(
            route = AppDestinations.RECIPE_DETAILS.route,
        ) {
            RecipeDetailsScreen(snackbarHostState = snackbarHostState)

        }
    }

    var titleRes by rememberSaveable { mutableIntStateOf(R.string.app_name) }

    navController.addOnDestinationChangedListener { _, destination, _ ->
        // TODO make the mapping programmatic by looking for destination route in AppDestinations
        when (destination.route) {
            AppDestinations.HOME.route -> titleRes = R.string.app_dest_title_home
            AppDestinations.RECIPES.route -> titleRes = R.string.app_dest_title_recipes
            AppDestinations.MEAL_PLANS.route -> titleRes = R.string.app_dest_title_meal_plans
            AppDestinations.RECIPE_DETAILS.route -> titleRes = R.string.app_dest_title_recipe_details
        }
    }

    Scaffold(
        modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { ChefAITopAppBar(titleRes) }, // TODO pass back nav click for non top level destinations
        bottomBar = { BottomNavigationBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            graph = graph,
            modifier = Modifier.padding(innerPadding)
        )
    }

    // Check for user messages to display on the screen
    userMessages.map { message ->
        val snackbarText = stringResource(message)
        LaunchedEffect(snackbarHostState, message, snackbarText) {
            snackbarHostState.showSnackbar(
                message = snackbarText,
                duration = SnackbarDuration.Short
            )
            userMessages.remove(message) // Remove it from the list after displaying it
        }
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