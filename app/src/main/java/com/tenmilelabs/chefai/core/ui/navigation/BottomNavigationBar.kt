package com.tenmilelabs.chefai.core.ui.navigation

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

/**
 * Destinations used in the [ChefAIApp] Bottom Nav Bar.
 */

enum class TopLevelDestination(
    val icon: Int,
    val appDestination: AppDestinations
) {
    HOME(
        icon = R.drawable.ic_home_black_24dp,
        appDestination = AppDestinations.HOME
    ),
    RECIPES(
        icon = R.drawable.ic_recipe_library_24dp,
        appDestination = AppDestinations.RECIPES
    ),
    MEAL_PLANS(
        icon = R.drawable.ic_chef_hat_black_24dp,
        appDestination = AppDestinations.MEAL_PLANS
    ),
}

@Composable
fun BottomNavigationBar(
    navController: NavController
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        TopLevelDestination.entries.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = currentRoute == item.appDestination.route,
                onClick = {
                    navController.navigate(item.appDestination.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = item.icon),
                        contentDescription = stringResource(item.appDestination.title),
                        modifier = Modifier.size(dimensionResource(R.dimen.nav_bar_icon_size))
                    )
                },
                label = {
                    Text(
                        stringResource(item.appDestination.title),
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unselectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    }
}

@Preview(
    uiMode = UI_MODE_NIGHT_YES,
    name = "BottomNavigationBarPreviewDark"
)
@Preview
@Composable
fun BottomNavigationBarPreview() {
    ChefAITheme {
        BottomNavigationBar(navController = NavController(LocalContext.current))
    }
}

