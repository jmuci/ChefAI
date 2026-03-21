package com.tenmilelabs.chefai.core.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.ui.LoginScreen
import com.tenmilelabs.chefai.auth.ui.RegisterScreen
import com.tenmilelabs.chefai.home.ui.HomeScreen
import com.tenmilelabs.chefai.mealplans.ui.MealPlansScreen
import com.tenmilelabs.chefai.recipes.ui.RecipesScreen
import com.tenmilelabs.chefai.recipes.ui.details.RecipeDetailsScreen
import com.tenmilelabs.chefai.recipes.ui.editor.RecipeEditorScreen
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

    // Home is always the start destination (anonymous-first model).
    // Login/Register are available via profile menu or settings.
    val startDestination = AppDestinations.HOME.route

    val graph = navController.createGraph(startDestination = startDestination) {
        composable(route = AppDestinations.HOME.route) {
            HomeScreen(
                snackbarHostState = snackbarHostState,
                onRecipeClick = { recipeUuid ->
                    navActions.navigateToRecipeDetail(recipeUuid)
                },
            )
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
            RecipeDetailsScreen(
                snackbarHostState = snackbarHostState,
                onEditClick = { recipeId ->
                    navActions.navigateToEditRecipe(recipeId)
                },
            )
        }
        composable(
            route = AppDestinations.RECIPE_EDITOR.route,
            arguments = listOf(
                navArgument(AppDestinationArgs.RECIPE_ID_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) {
            RecipeEditorScreen(
                onNavigateBack = { navController.popBackStack() },
                snackbarHostState = snackbarHostState,
            )
        }
        composable(route = AppDestinations.LOGIN.route) {
            LoginScreen(
                snackbarHostState = snackbarHostState,
                onNavigateToHome = { navActions.navigateToHome() },
                onNavigateToRegister = { navActions.navigateToRegister() }
            )
        }
        composable(route = AppDestinations.REGISTER.route) {
            RegisterScreen(
                snackbarHostState = snackbarHostState,
                onNavigateToHome = { navActions.navigateToHome() },
                onNavigateToLogin = { navActions.navigateToLogin() }
            )
        }
    }

    var titleRes by rememberSaveable { mutableIntStateOf(R.string.app_name) }
    var isTopLevelDestination by rememberSaveable { mutableStateOf(false) }
    var currentRoute by rememberSaveable { mutableStateOf(startDestination) }

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
            // Don't show top bar on login or register screens
            if (currentRoute != AppDestinations.LOGIN.route && currentRoute != AppDestinations.REGISTER.route) {
                ChefAITopAppBar(
                    titleRes,
                    onNavigationClick = if (!isTopLevelDestination) {
                        { navController.popBackStack() }
                    } else {
                        null
                    },
                    onLogout = {},
                    onLogin = {
                        navActions.navigateToLogin()
                    }
                )
            }
        },
        bottomBar = {
            // Don't show bottom bar on login or register screens
            if (currentRoute != AppDestinations.LOGIN.route && currentRoute != AppDestinations.REGISTER.route) {
                BottomNavigationBar(navController)
            }
        },
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

}



// Preview not available - ChefAINavGraph requires SessionManager injection from Hilt
// which can't be easily mocked in Composable previews
