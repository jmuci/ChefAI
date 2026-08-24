package com.tenmilelabs.chefai.recipes.domain.repository

import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Outcome of [RecipesRepository.getOrFetchRecipe] — see ChefAI#186. */
sealed interface RecipeFetchResult {
    data class Found(val recipe: Recipe) : RecipeFetchResult

    /** The recipe doesn't exist, is soft-deleted, or is private and not owned by this caller. */
    data object NotAvailable : RecipeFetchResult

    /** The recipe might exist, but it couldn't be resolved — a transient failure worth retrying. */
    data object NetworkError : RecipeFetchResult
}

interface RecipesRepository {
    fun getRecipesStream(): Flow<List<Recipe>>

    fun getRecipesPreviewStream(): Flow<List<RecipePreview>>

    fun getRecipesPreviewStreamForUser(userUuid: UUID): Flow<List<RecipePreview>>

    fun getPublicRecipesStream(): Flow<List<Recipe>>

    suspend fun getRecipe(uuid: UUID): Recipe?
    fun getRecipeStream(uuid: UUID): Flow<Recipe?>

    /**
     * Returns the recipe from Room if present; otherwise fetches it from
     * `GET /api/v1/recipes/{recipeId}` and persists it before returning. For a search result
     * not yet delivered by `/sync/pull` — anonymous sessions never even pull (see
     * ChefAI#186) — this is what makes it possible to open or bookmark that result at all.
     */
    suspend fun getOrFetchRecipe(uuid: UUID): RecipeFetchResult

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