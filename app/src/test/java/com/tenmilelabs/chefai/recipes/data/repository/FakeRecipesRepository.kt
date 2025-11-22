package com.tenmilelabs.chefai.recipes.data.repository

import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class FakeRecipesRepository : RecipesRepository {

    // For general recipes list if needed by other ViewModels
    private val recipesListFlow = MutableSharedFlow<List<Recipe>>(replay = 1)
    private val recipesPreviewListFlow = MutableSharedFlow<List<RecipePreview>>(replay = 1)
    private var recipesToEmitGeneral: List<Recipe> = emptyList()
    private var shouldReturnErrorForGetRecipes = false
    private var exceptionForGetRecipes: Exception? = null

    // For specific recipe details
    private val recipeDetailFlows = mutableMapOf<UUID, MutableSharedFlow<Recipe?>>()
    private var shouldReturnErrorForGetRecipe = false
    private var exceptionForGetRecipe: Exception? = null

    fun setRecipesToEmit(recipes: List<Recipe>) {
        recipesListFlow.tryEmit(recipes)
    }

    fun setRecipePreviewsToEmit(recipePreviews: List<RecipePreview>) {
        recipesPreviewListFlow.tryEmit(recipePreviews)
    }

    fun setShouldReturnErrorForGetRecipes(value: Boolean, exception: Exception? = null) {
        shouldReturnErrorForGetRecipes = value
        this.exceptionForGetRecipes = exception ?: Exception("Test repository error for getRecipes")
    }

    fun setShouldReturnErrorForGetRecipe(value: Boolean, exception: Exception? = null) {
        shouldReturnErrorForGetRecipe = value
        this.exceptionForGetRecipe = exception ?: Exception("Test repository error for getRecipe")
    }

    fun emitRecipe(recipeId: UUID, recipe: Recipe?) {
        getFlowForRecipe(recipeId).tryEmit(recipe)
    }

    fun emitErrorForRecipe(recipeId: UUID, error: Throwable) {
        getFlowForRecipe(recipeId).tryEmit(null) // Or handle error emission differently if your flow supports it
        // A more robust way would be to make recipeDetailFlows emit Result<Recipe?> or similar
        // For simplicity with current ViewModel, emitting null on error from flow might be how it's handled
        // Or, we can throw from the flow itself.
    }


    private fun getFlowForRecipe(recipeId: UUID): MutableSharedFlow<Recipe?> {
        return recipeDetailFlows.getOrPut(recipeId) { MutableSharedFlow(replay = 1) }
    }

    override fun getRecipesPreviewStream(): Flow<List<RecipePreview>> {
        if (shouldReturnErrorForGetRecipes) {
            return flow {
                throw (exceptionForGetRecipes ?: Exception("Configured repository error"))
            }
        }
        return recipesPreviewListFlow
    }

    override fun getRecipesStream(): Flow<List<Recipe>> {
        if (shouldReturnErrorForGetRecipes) {
            return flow {
                throw (exceptionForGetRecipes ?: Exception("Configured repository error"))
            }
        }
        return recipesListFlow
    }

    override suspend fun getRecipe(uuid: UUID): Recipe? {
        TODO("Not yet implemented")
    }

    fun setRecipesForList(recipes: List<Recipe>) {
        recipesToEmitGeneral = recipes
        recipesListFlow.tryEmit(recipesToEmitGeneral)
    }

    override fun getRecipeStream(uuid: UUID): Flow<Recipe?> {
        if (shouldReturnErrorForGetRecipe) {
            return flow {
                throw (exceptionForGetRecipe ?: Exception("Configured repository error"))
            }
        }
        return getFlowForRecipe(uuid)
    }

    override suspend fun createRecipe(recipe: Recipe) {
        TODO("Not yet implemented")
    }

    override suspend fun updateRecipe(recipe: Recipe) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteAllRecipes() {
        TODO("Not yet implemented")
    }

    override suspend fun deleteRecipe(recipeId: UUID) {
        // No-op
    }
}