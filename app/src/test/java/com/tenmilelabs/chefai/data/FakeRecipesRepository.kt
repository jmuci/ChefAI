package com.tenmilelabs.chefai.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow

class FakeRecipesRepository : RecipesRepository {

    // For general recipes list if needed by other ViewModels
    private val recipesListFlow = MutableSharedFlow<List<Recipe>>(replay = 1)
    private var recipesToEmitGeneral: List<Recipe> = emptyList()

    // For specific recipe details
    private val recipeDetailFlows = mutableMapOf<String, MutableSharedFlow<Recipe?>>()
    private var shouldReturnErrorForGetRecipe = false
    private var exceptionForGetRecipe: Exception? = null


    fun setShouldReturnErrorForGetRecipe(value: Boolean, exception: Exception? = null) {
        shouldReturnErrorForGetRecipe = value
        this.exceptionForGetRecipe = exception ?: Exception("Test repository error for getRecipe")
    }

    fun emitRecipe(recipeId: String, recipe: Recipe?) {
        getFlowForRecipe(recipeId).tryEmit(recipe)
    }

    fun emitErrorForRecipe(recipeId: String, error: Throwable) {
        getFlowForRecipe(recipeId).tryEmit(null) // Or handle error emission differently if your flow supports it
        // A more robust way would be to make recipeDetailFlows emit Result<Recipe?> or similar
        // For simplicity with current ViewModel, emitting null on error from flow might be how it's handled
        // Or, we can throw from the flow itself.
    }


    private fun getFlowForRecipe(recipeId: String): MutableSharedFlow<Recipe?> {
        return recipeDetailFlows.getOrPut(recipeId) { MutableSharedFlow(replay = 1) }
    }

    override suspend fun getRecipes(): List<Recipe> {
        TODO("Not yet implemented")
    }

    override fun getRecipesFlow(): Flow<List<Recipe>> {
        return recipesListFlow
    }

    override suspend fun getRecipe(uuid: String): Recipe? {
        TODO("Not yet implemented")
    }

    fun setRecipesForList(recipes: List<Recipe>) {
        recipesToEmitGeneral = recipes
        recipesListFlow.tryEmit(recipesToEmitGeneral)
    }

    override fun getRecipeFlow(recipeId: String): Flow<Recipe?> {
        if (shouldReturnErrorForGetRecipe) {
            return flow { throw (exceptionForGetRecipe ?: Exception("Configured repository error")) }
        }
        return getFlowForRecipe(recipeId)
    }

    override suspend fun createRecipe(
        recipe: Recipe,
        uuid: String
    ): String {
        TODO("Not yet implemented")
    }

    override suspend fun updateRecipe(recipe: Recipe) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteAllRecipes() {
        TODO("Not yet implemented")
    }

    override suspend fun deleteRecipe(recipeId: String) {
        // No-op
    }
}