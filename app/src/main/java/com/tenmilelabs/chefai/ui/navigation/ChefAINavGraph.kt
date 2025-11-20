package com.tenmilelabs.chefai.ui.navigation

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.ui.createrecipe.CreateRecipeScreen
import com.tenmilelabs.chefai.ui.home.HomeScreen
import com.tenmilelabs.chefai.ui.mealplans.MealPlansScreen
import com.tenmilelabs.chefai.ui.recipeDetails.RecipeDetailsScreen
import com.tenmilelabs.chefai.ui.recipes.RecipesScreen
import timber.log.Timber

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
        composable(route = AppDestinations.CREATE_RECIPE.route) {
            CreateRecipeScreen(
                onNavigateBack = { navController.popBackStack() },
                onRecipeCreated = { navController.popBackStack() },
                snackbarHostState= snackbarHostState
            )
        }
    }

    var titleRes by rememberSaveable { mutableIntStateOf(R.string.app_name) }
    var isTopLevelDestination by rememberSaveable { mutableStateOf(false) }
    var currentRoute by rememberSaveable { mutableStateOf(AppDestinations.HOME.route) }

    @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
    var isFabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    // Handle back press to collapse FAB menu
    BackHandler(enabled = isFabMenuExpanded) {
        isFabMenuExpanded = false
    }

    // Add destination listener only once and properly dispose of it
    DisposableEffect(navController) {
        val listener =
            NavController.OnDestinationChangedListener { _: NavController, destination: NavDestination, _ ->
                val newRoute = destination.route ?: AppDestinations.HOME.route

                titleRes = AppDestinations.entries
                    .filter { it.route == destination.route }
                    .map { it.title }.firstOrNull() ?: R.string.app_name

                isTopLevelDestination =
                    TopLevelDestination.entries.any { it.appDestination.route == destination.route }

                // Only collapse menu and update route if the route actually changed
                if (currentRoute != newRoute) {
                    Timber.d( "Route changed from $currentRoute to $newRoute")
                    currentRoute = newRoute
                    isFabMenuExpanded = false
                }
            }

        navController.addOnDestinationChangedListener(listener)

        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    Scaffold(
        modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChefAITopAppBar(
                titleRes,
                onNavigationClick = if (!isTopLevelDestination) {
                    { navController.popBackStack() }
                } else {
                    null
                }
            )
        },
        bottomBar = { BottomNavigationBar(navController) },
        floatingActionButton = {
            // Only show FAB on Recipes screen
            if (currentRoute == AppDestinations.RECIPES.route) {
                FloatingActionButtonMenu(
                    expanded = isFabMenuExpanded,
                    onExpandedChange = {
                        isFabMenuExpanded = !isFabMenuExpanded
                    },
                    onCreateRecipeClick = {
                        isFabMenuExpanded = false
                        navActions.navigateToCreateRecipe()
                    },
                    onImportRecipeClick = {
                        isFabMenuExpanded = false
                        // TODO: Navigate to import recipe screen
                        // navActions.navigateToImportRecipe()
                    }
                )
            }
        }
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
