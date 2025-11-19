package com.tenmilelabs.chefai.ui.navigation

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
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
                        // TODO: Navigate to create recipe screen
                        // navActions.navigateToCreateRecipe()
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

@Composable
fun FloatingActionButtonMenu(
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onCreateRecipeClick: () -> Unit,
    onImportRecipeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animate the rotation of the main FAB icon
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        label = "FAB rotation"
    )

    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Menu items with animation
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Import Recipe option
                    FabMenuItem(
                        onClick = onImportRecipeClick,
                        icon = Icons.Default.Download,
                        label = "Import Recipe"
                    )

                    // Create Recipe option
                    FabMenuItem(
                        onClick = onCreateRecipeClick,
                        icon = Icons.Default.Create,
                        label = "Create Recipe"
                    )
                }
            }

            // Main FAB
            FloatingActionButton(
                onClick = {
                    onExpandedChange()
                },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (expanded) "Close menu" else "Open menu",
                    modifier = Modifier.rotate(rotation)
                )
            }
        }
    }
}

@Composable
fun FabMenuItem(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Small FAB
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
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

@Preview(showBackground = true, name = "FAB Menu Collapsed")
@Composable
fun FloatingActionButtonMenuCollapsedPreview() {
    var expanded by remember { mutableStateOf(false) }
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButtonMenu(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                onCreateRecipeClick = {},
                onImportRecipeClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "FAB Menu Expanded")
@Composable
fun FloatingActionButtonMenuExpandedPreview() {
    var expanded by remember { mutableStateOf(true) }
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButtonMenu(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                onCreateRecipeClick = {},
                onImportRecipeClick = {}
            )
        }
    }
}
