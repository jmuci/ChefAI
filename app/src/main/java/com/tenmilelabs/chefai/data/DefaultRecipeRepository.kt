package com.tenmilelabs.chefai.data

import com.tenmilelabs.chefai.data.source.local.RecipeDao
import com.tenmilelabs.chefai.data.source.network.RecipeNetworkDataSource
import com.tenmilelabs.chefai.di.ApplicationScope
import com.tenmilelabs.chefai.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultRecipeRepository @Inject constructor(
    private val localDatSource: RecipeDao,
    private val networkDataSource: RecipeNetworkDataSource,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : RecipesRepository {

    // Fire and forget
    override suspend fun getRecipes(): List<Recipe> {
        return withContext(dispatcher) {
            localDatSource.getAllRecipes().toExternal()
        }
    }

    override fun getRecipesStrem(): Flow<List<Recipe>> {
        return if (true) { // Network
            flow {
                emit(getAllItemsFromNetwork())
            }
        } else {        // Local
            return localDatSource.observeAll().map { recipes ->
                withContext(dispatcher) {
                    recipes.toExternal()
                }
            }
        }
    }

    private suspend fun getAllItemsFromNetwork(): List<Recipe> = try {
        val recipes = networkDataSource.getRecipes().toRecipe()
        Timber.d("Fetched ${recipes.size} recipes from the BE")
        recipes
    } catch(e: IOException)  {
        // TODO(timber) Replace with Timber Logging
        // Handle error, e.g., emit an empty list or an error state
        Timber.e("Error fetching recipe items from network. Error: ${e.message}")
        throw e
    }

    override fun getRecipeStream(uuid: String): Flow<Recipe?> {
        return localDatSource.observeRecipeById(uuid).map { it.toExternal() }
    }

    override suspend fun getRecipe(uuid: String): Recipe? {
        return localDatSource.getRecipeById(uuid)?.toExternal()
    }

    /**
     * Creates a new [Recipe] in the local data source.
     * The optional uuid parameter should only be used for testing
     */
    override suspend fun createRecipe(recipe: Recipe, uuid: String): String {
        // ID creation might be a complex operation so it's executed using the supplied
        // coroutine dispatcher
        val recipeUuid = withContext(dispatcher) {
            uuid.ifEmpty {
                UUID.randomUUID().toString()
            }
        }

        localDatSource.upsertRecipe(recipe.copy(uuid = recipeUuid).toRecipeEntity())
        return recipeUuid
    }

    override suspend fun updateRecipe(recipe: Recipe) {
        localDatSource.upsertRecipe(recipe.toRecipeEntity())
    }

    override suspend fun deleteAllRecipes() {
        localDatSource.deleteAllRecipes()
    }

    override suspend fun deleteRecipe(recipeId: String) {
        localDatSource.deleteRecipe(recipeId)
    }
}