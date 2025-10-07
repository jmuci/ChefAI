package com.tenmilelabs.chefai.domain.repository

import com.tenmilelabs.chefai.domain.model.Recipe
import kotlinx.coroutines.flow.Flow

interface RecipesRepository {
    suspend fun getRecipes(): List<Recipe>
    fun getRecipesStream(): Flow<List<Recipe>>

    suspend fun getRecipe(uuid: String): Recipe?
    fun getRecipeStream(uuid: String): Flow<Recipe?>

    suspend fun createRecipe(recipe: Recipe, uuid: String = ""): String

    suspend fun updateRecipe(recipe: Recipe)

    suspend fun deleteAllRecipes()

    suspend fun deleteRecipe(recipeId: String)
}