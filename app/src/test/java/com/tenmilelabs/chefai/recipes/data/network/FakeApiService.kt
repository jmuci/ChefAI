package com.tenmilelabs.chefai.recipes.data.network

import com.tenmilelabs.chefai.recipes.data.network.model.NetworkRecipe
import com.tenmilelabs.chefai.recipes.data.network.model.NetworkRecipeList

/**
 * Fake implementation of [RecipeNetworkDataSource] for testing purposes.
 * Allows injecting a predefined list of [NetworkRecipe] so tests are deterministic.
 */
class FakeApiService(
    var fakeRecipes: List<NetworkRecipe> = emptyList()
) : RecipeNetworkDataSource {

    override suspend fun getRecipes(): NetworkRecipeList {
        // Returns all fake recipes wrapped in NetworkRecipeList
        return NetworkRecipeList(recipes = fakeRecipes)
    }

    override suspend fun getRecipe(uuid: String): NetworkRecipe {
        // Finds a recipe by UUID or throws an exception if not found
        return fakeRecipes.find { it.uuid == uuid }
            ?: throw NoSuchElementException("Fake recipe with UUID $uuid not found")
    }
}