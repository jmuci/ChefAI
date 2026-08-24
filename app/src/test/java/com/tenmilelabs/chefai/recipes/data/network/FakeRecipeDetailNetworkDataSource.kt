package com.tenmilelabs.chefai.recipes.data.network

import java.util.UUID

/** A configurable fake [RecipeDetailNetworkDataSource] for testing. Defaults to [RecipeDetailNetworkResult.NotFound]. */
class FakeRecipeDetailNetworkDataSource : RecipeDetailNetworkDataSource {

    val requestedRecipeIds = mutableListOf<UUID>()
    var resultToReturn: RecipeDetailNetworkResult = RecipeDetailNetworkResult.NotFound

    override suspend fun fetchRecipe(recipeId: UUID): RecipeDetailNetworkResult {
        requestedRecipeIds += recipeId
        return resultToReturn
    }
}
