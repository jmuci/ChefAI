package com.tenmilelabs.chefai.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import com.tenmilelabs.chefai.ui.home.HomeScreen
import com.tenmilelabs.chefai.ui.mealplans.MealPlansScreen
import com.tenmilelabs.chefai.ui.recipes.RecipesScreen

@Composable
fun ChefAINavGraph(modifier: Modifier) {
    val navController = rememberNavController()
    val graph = navController.createGraph(startDestination = Screen.Home.route) {
        composable(route = Screen.Home.route) {
            HomeScreen()
        }
        composable(route = Screen.Recipes.route) {
            RecipesScreen()
        }
        composable(route = Screen.MealPlans.route) {
            MealPlansScreen()
        }
    }
    Scaffold(
        modifier,
        bottomBar = { BottomNavigationBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            graph = graph,
            modifier = Modifier.padding(innerPadding)
        )
    }
}


@Preview
@Composable
fun ChefAINavGraphPreview() {
    ChefAINavGraph(Modifier.fillMaxSize())
}