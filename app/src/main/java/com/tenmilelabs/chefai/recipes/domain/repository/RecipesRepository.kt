package com.tenmilelabs.chefai.recipes.domain.repository

import androidx.paging.PagingData
import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
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

    fun getRecipesPreviewPager(userId: UUID, pageSize: Int = 20): Flow<PagingData<RecipePreview>>

}