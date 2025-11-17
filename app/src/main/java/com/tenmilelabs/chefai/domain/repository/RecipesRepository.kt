package com.tenmilelabs.chefai.domain.repository

import com.tenmilelabs.chefai.domain.model.Recipe
import com.tenmilelabs.chefai.domain.model.RecipePreview
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface RecipesRepository {
    fun getRecipesStream(): Flow<List<Recipe>>

    fun getRecipesPreviewStream(): Flow<List<RecipePreview>>

    suspend fun getRecipe(uuid: UUID): Recipe?
    fun getRecipeStream(uuid: UUID): Flow<Recipe?>

    suspend fun createRecipe(recipe: Recipe)

    suspend fun updateRecipe(recipe: Recipe)

    suspend fun deleteAllRecipes()

    suspend fun deleteRecipe(recipeId: UUID)
}