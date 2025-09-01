package com.tenmilelabs.chefai.data.source.network

interface RecipeNetworkDataSource {
    suspend fun getRecipes(): NetworkRecipeList
    suspend fun getRecipe(uuid: String): NetworkRecipe
}