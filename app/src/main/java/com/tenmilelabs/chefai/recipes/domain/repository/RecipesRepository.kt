package com.tenmilelabs.chefai.recipes.domain.repository

import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface RecipesRepository {
    fun getRecipesStream(): Flow<List<Recipe>>

    fun getRecipesPreviewStream(): Flow<List<RecipePreview>>

    fun getRecipesPreviewStreamForUser(userUuid: UUID): Flow<List<RecipePreview>>

    fun getPublicRecipesStream(): Flow<List<Recipe>>

    suspend fun getRecipe(uuid: UUID): Recipe?
    fun getRecipeStream(uuid: UUID): Flow<Recipe?>

    suspend fun createRecipe(recipe: Recipe)

    suspend fun updateRecipe(recipe: Recipe)

    suspend fun deleteAllRecipes()

    suspend fun deleteRecipe(recipeId: UUID)

    suspend fun softDeleteRecipe(recipeId: UUID)

    /**
     * Returns a flow of recipe previews for the given UUIDs, resolved from Room.
     * The result is filtered in-memory from the full recipe stream, so the flow
     * updates reactively whenever any matching recipe changes in the database.
     * Returns an empty flow immediately if [ids] is empty.
     */
    fun getRecipePreviewsByIds(ids: List<UUID>): Flow<List<RecipePreview>>

    /** Returns the number of recipes created by (or belonging to) the given user. */
    suspend fun getRecipeCountForUser(userId: UUID): Int
}