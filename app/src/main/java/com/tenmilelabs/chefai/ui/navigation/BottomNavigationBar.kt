package com.tenmilelabs.chefai.ui.navigation

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import com.tenmilelabs.chefai.ui.theme.FaintGreen
import com.tenmilelabs.chefai.ui.theme.MdThemeOnPrimary
import com.tenmilelabs.chefai.ui.theme.MdThemePrimary
import com.tenmilelabs.chefai.ui.theme.MediumGreen
import com.tenmilelabs.chefai.R


data class NavigationItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)

@Composable
fun BottomNavigationBar(
    navController: NavController
) {
    val selectedNavigationIndex = rememberSaveable {
        mutableIntStateOf(0)
    }

    val navigationItems = listOf(
        NavigationItem(
            title = "Home",
            icon = Icons.Filled.Home,
            route = Screen.Home.route
        ),
        NavigationItem(
            title = "Recipes",
            icon = Icons.AutoMirrored.Filled.LibraryBooks,
            route = Screen.Recipes.route
        ),
        NavigationItem(
            title = "Meal Plans",
            //icon = painterResource(id = R.drawable.ic_chef_hat_black_24),
            icon = ImageVector.vectorResource(id = R.drawable.ic_chef_hat_black_24dp),
            //icon = Icons.Filled.QuestionMark,
            route = Screen.MealPlans.route
        ),
    )
    NavigationBar(
        containerColor = MdThemePrimary,
    ) {
        navigationItems.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedNavigationIndex.intValue == index,
                onClick = {
                    selectedNavigationIndex.intValue = index
                    navController.navigate(item.route)
                },
                icon = {
                    Icon(imageVector = item.icon, contentDescription = item.title)
                },
                label = {
                    Text(
                        item.title,
                        color = if (index == selectedNavigationIndex.intValue)
                            MdThemeOnPrimary
                        else FaintGreen
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MediumGreen,
                    indicatorColor = MdThemeOnPrimary,
                    unselectedIconColor = FaintGreen,
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
    BottomNavigationBar(navController = NavController(LocalContext.current))
}

