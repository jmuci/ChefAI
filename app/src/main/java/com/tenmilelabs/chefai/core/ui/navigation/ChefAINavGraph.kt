package com.tenmilelabs.chefai.core.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import androidx.window.core.layout.WindowSizeClass
import androidx.navigation.navArgument
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.ui.LoginScreen
import com.tenmilelabs.chefai.auth.ui.RegisterScreen
import com.tenmilelabs.chefai.home.ui.HomeScreen
import com.tenmilelabs.chefai.mealplans.ui.MealPlansScreen
import com.tenmilelabs.chefai.mealplans.ui.detail.MealPlanDetailScreen
import com.tenmilelabs.chefai.mealplans.ui.create.CreateMealPlanEvent
import com.tenmilelabs.chefai.mealplans.ui.create.CreateMealPlanViewModel
import com.tenmilelabs.chefai.mealplans.ui.create.WizardAdvancedScreen
import com.tenmilelabs.chefai.mealplans.ui.create.WizardBasicsScreen
import com.tenmilelabs.chefai.mealplans.ui.create.WizardPreferencesScreen
import com.tenmilelabs.chefai.recipes.ui.RecipesScreen
import com.tenmilelabs.chefai.recipes.ui.details.RecipeDetailsScreen
import com.tenmilelabs.chefai.recipes.ui.editor.RecipeEditorScreen
import com.tenmilelabs.chefai.recipes.ui.urlimport.ImportRecipeRoute
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
    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

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
            MealPlansScreen(
                onCreateMealPlan = { navActions.navigateToMealPlanWizard() },
                onMealPlanClick = { mealPlanId -> navActions.navigateToMealPlanDetail(mealPlanId) },
                snackbarHostState = snackbarHostState,
            )
        }
        composable(route = AppDestinations.MEAL_PLAN_DETAIL.route) {
            MealPlanDetailScreen(
                onRecipeClick = { recipeId -> navActions.navigateToMealPlanRecipeDetail(recipeId) },
                snackbarHostState = snackbarHostState,
            )
        }
        composable(route = AppDestinations.MEAL_PLAN_RECIPE_DETAIL.route) {
            RecipeDetailsScreen(snackbarHostState = snackbarHostState)
        }
        navigation(
            startDestination = ScreenBaseRoutes.MEAL_PLAN_WIZARD_BASICS,
            route = ScreenBaseRoutes.MEAL_PLAN_WIZARD,
        ) {
            composable(ScreenBaseRoutes.MEAL_PLAN_WIZARD_BASICS) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(ScreenBaseRoutes.MEAL_PLAN_WIZARD)
                }
                val wizardViewModel: CreateMealPlanViewModel = hiltViewModel(parentEntry)
                WizardBasicsScreen(
                    viewModel = wizardViewModel,
                    onNext = {
                        navController.navigate(ScreenBaseRoutes.MEAL_PLAN_WIZARD_PREFERENCES)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ScreenBaseRoutes.MEAL_PLAN_WIZARD_PREFERENCES) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(ScreenBaseRoutes.MEAL_PLAN_WIZARD)
                }
                val wizardViewModel: CreateMealPlanViewModel = hiltViewModel(parentEntry)
                WizardPreferencesScreen(
                    viewModel = wizardViewModel,
                    onNext = {
                        navController.navigate(ScreenBaseRoutes.MEAL_PLAN_WIZARD_ADVANCED)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ScreenBaseRoutes.MEAL_PLAN_WIZARD_ADVANCED) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(ScreenBaseRoutes.MEAL_PLAN_WIZARD)
                }
                val wizardViewModel: CreateMealPlanViewModel = hiltViewModel(parentEntry)

                LaunchedEffect(Unit) {
                    wizardViewModel.uiEvents.collect { event ->
                        when (event) {
                            is CreateMealPlanEvent.MealPlanReady -> {
                                // Generation succeeded — go straight to the detail screen
                                navController.popBackStack(
                                    route = AppDestinations.MEAL_PLANS.route,
                                    inclusive = false,
                                )
                                navActions.navigateToMealPlanDetail(event.mealPlanId)
                            }
                            is CreateMealPlanEvent.MealPlanSavedAsDraft -> {
                                // Offline / BE error — show detail as DRAFT with Generate button
                                navController.popBackStack(
                                    route = AppDestinations.MEAL_PLANS.route,
                                    inclusive = false,
                                )
                                navActions.navigateToMealPlanDetail(event.mealPlanId)
                            }
                            is CreateMealPlanEvent.ShowError -> {
                                snackbarHostState.showSnackbar("Failed to create meal plan")
                            }
                        }
                    }
                }

                WizardAdvancedScreen(
                    viewModel = wizardViewModel,
                    onDone = { /* handled by event collection above */ },
                    onBack = { navController.popBackStack() },
                )
            }
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
                },
                navArgument(AppDestinationArgs.DRAFT_ID_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            RecipeEditorScreen(
                onNavigateBack = { navController.popBackStack() },
                snackbarHostState = snackbarHostState,
            )
        }
        composable(route = AppDestinations.IMPORT_RECIPE.route) {
            ImportRecipeRoute(
                onNavigateToEditorWithDraft = { draftId -> navActions.navigateToEditorWithDraft(draftId) },
                onNavigateToManualEditor = { navActions.navigateToCreateRecipe() },
                onNavigateBack = { navController.popBackStack() },
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

    val isExpanded = currentWindowAdaptiveInfo()
        .windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

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

                val wizardRoutes = setOf(
                    ScreenBaseRoutes.MEAL_PLAN_WIZARD_BASICS,
                    ScreenBaseRoutes.MEAL_PLAN_WIZARD_PREFERENCES,
                    ScreenBaseRoutes.MEAL_PLAN_WIZARD_ADVANCED,
                )
                titleRes = if (destination.route in wizardRoutes) {
                    R.string.app_dest_title_meal_plan_wizard
                } else {
                    AppDestinations.entries
                        .filter { it.route == destination.route }
                        .map { it.title }.firstOrNull() ?: R.string.app_name
                }

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
                        {
                            // Route through the system back dispatcher rather than
                            // popping directly, so screens with their own BackHandler
                            // (e.g. RecipeEditorScreen's unsaved-changes check) get a
                            // chance to intercept before the back stack is popped.
                            backPressedDispatcher?.onBackPressed()
                                ?: navController.popBackStack()
                        }
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
            val wizardRoutes = setOf(
                ScreenBaseRoutes.MEAL_PLAN_WIZARD_BASICS,
                ScreenBaseRoutes.MEAL_PLAN_WIZARD_PREFERENCES,
                ScreenBaseRoutes.MEAL_PLAN_WIZARD_ADVANCED,
            )
            val hideNav = currentRoute == AppDestinations.LOGIN.route ||
                currentRoute == AppDestinations.REGISTER.route ||
                currentRoute == AppDestinations.IMPORT_RECIPE.route ||
                currentRoute in wizardRoutes
            if (!hideNav && !isExpanded) {
                BottomNavigationBar(navController)
            }
        },
        floatingActionButton = {
            when (currentRoute) {
                AppDestinations.RECIPES.route -> {
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
                            navActions.navigateToImportRecipe()
                        }
                    )
                }
                AppDestinations.MEAL_PLANS.route -> {
                    FloatingActionButton(
                        onClick = { navActions.navigateToMealPlanWizard() },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create meal plan",
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val wizardRoutes = setOf(
            ScreenBaseRoutes.MEAL_PLAN_WIZARD_BASICS,
            ScreenBaseRoutes.MEAL_PLAN_WIZARD_PREFERENCES,
            ScreenBaseRoutes.MEAL_PLAN_WIZARD_ADVANCED,
        )
        val hideNav = currentRoute == AppDestinations.LOGIN.route ||
            currentRoute == AppDestinations.REGISTER.route ||
            currentRoute == AppDestinations.IMPORT_RECIPE.route ||
            currentRoute in wizardRoutes

        Row(modifier = Modifier.padding(innerPadding)) {
            if (isExpanded && !hideNav) {
                NavigationRailBar(navController)
            }
            NavHost(
                navController = navController,
                graph = graph,
                modifier = Modifier.weight(1f)
            )
        }
    }

}



// Preview not available - ChefAINavGraph requires SessionManager injection from Hilt
// which can't be easily mocked in Composable previews
