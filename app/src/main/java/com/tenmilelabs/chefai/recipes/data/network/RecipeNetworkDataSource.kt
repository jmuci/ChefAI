package com.tenmilelabs.chefai.recipes.data.network

import com.tenmilelabs.chefai.recipes.data.network.model.NetworkRecipe
import com.tenmilelabs.chefai.recipes.data.network.model.NetworkRecipeList

interface RecipeNetworkDataSource {
    suspend fun getRecipes(): NetworkRecipeList
    suspend fun getRecipe(uuid: String): NetworkRecipe
}