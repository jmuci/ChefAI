package com.tenmilelabs.chefai.recipes.data.network

import java.util.UUID

interface RecipeDetailNetworkDataSource {
    suspend fun fetchRecipe(recipeId: UUID): RecipeDetailNetworkResult
}
