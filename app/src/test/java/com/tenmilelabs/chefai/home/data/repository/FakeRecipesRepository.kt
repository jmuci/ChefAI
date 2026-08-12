package com.tenmilelabs.chefai.home.data.repository

import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Minimal [RecipesRepository] fake for [com.tenmilelabs.chefai.home.ui.HomeViewModel] tests.
 * Only implements [getRecipePreviewsByIds]; all other methods throw [UnsupportedOperationException].
 */
class FakeRecipesRepository : RecipesRepository {

    private val previewsFlow = MutableSharedFlow<List<RecipePreview>>(replay = 1)

    /** Emit a list of [RecipePreview]s that will be returned by [getRecipePreviewsByIds]. */
    fun emitPreviews(previews: List<RecipePreview>) {
        previewsFlow.tryEmit(previews)
    }

    override fun getRecipePreviewsByIds(ids: List<UUID>): Flow<List<RecipePreview>> {
        if (ids.isEmpty()) return flowOf(emptyList())
        val idSet = ids.toHashSet()
        return previewsFlow.map { all -> all.filter { it.uuid in idSet } }
    }

    // -- Unused in home tests ---------------------------------------------------

    override fun getRecipesStream(): Flow<List<Recipe>> = error("Not used in home tests")
    override fun getRecipesPreviewStream(): Flow<List<RecipePreview>> = error("Not used in home tests")
    override fun getRecipesPreviewStreamForUser(userUuid: UUID): Flow<List<RecipePreview>> = error("Not used")
    override fun getPublicRecipesStream(): Flow<List<Recipe>> = error("Not used in home tests")
    override suspend fun getRecipe(uuid: UUID): Recipe? = error("Not used in home tests")
    override fun getRecipeStream(uuid: UUID): Flow<Recipe?> = error("Not used in home tests")
    override suspend fun createRecipe(recipe: Recipe) = error("Not used in home tests")
    override suspend fun updateRecipe(recipe: Recipe) = error("Not used in home tests")
    override suspend fun deleteAllRecipes() = error("Not used in home tests")
    override suspend fun deleteRecipe(recipeId: UUID) = error("Not used in home tests")
    override suspend fun softDeleteRecipe(recipeId: UUID) = error("Not used in home tests")
    override suspend fun getRecipeCountForUser(userId: UUID): Int = error("Not used in home tests")
}
