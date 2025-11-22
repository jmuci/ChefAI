package com.tenmilelabs.chefai.recipes.data.network

import com.tenmilelabs.chefai.recipes.data.network.model.NetworkRecipe
import com.tenmilelabs.chefai.recipes.data.network.model.NetworkRecipeList

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import javax.inject.Inject

const val BASE_URL = "https://jmuci.github.io/ChATestAPI/api"
const val API_VERSION = "v2"
const val RECIPES_ENDPOINT = "$BASE_URL/$API_VERSION/recipes.json"

class ChefAIApiService @Inject constructor(
    val client: HttpClient,
): RecipeNetworkDataSource {

    override suspend fun getRecipes(): NetworkRecipeList =
        // TODO call client.close?
        client.get(RECIPES_ENDPOINT).body()

    override suspend fun getRecipe(uuid: String): NetworkRecipe {
        val recipeList: NetworkRecipeList  = client.get(RECIPES_ENDPOINT).body()
        return recipeList.recipes.find { it.uuid == uuid }
            ?: throw NoSuchElementException("Recipe with UUID $uuid not found in response")

    }
}