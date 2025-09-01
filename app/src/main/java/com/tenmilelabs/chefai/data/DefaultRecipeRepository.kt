package com.tenmilelabs.chefai.data

import com.tenmilelabs.chefai.data.source.local.RecipeDao
import com.tenmilelabs.chefai.data.source.network.RecipeNetworkDataSource
import com.tenmilelabs.chefai.di.ApplicationScope
import com.tenmilelabs.chefai.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultRecipeRepository @Inject constructor(
    private val localDatSource: RecipeDao,
    private val networkDataSource: RecipeNetworkDataSource,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) : RecipesRepository {

    // TODO Cache in memory list of recipes (paginated?)
    // TODO Decide when to read from local DB or network / cache
    //  -> Implement Data Merging Strategy (based on some form of timeStamps Perhaps)
    // TODO Use appScope to make sure coroutines don't get cancelled
    //TODO Follow those docs : https://developer.android.com/topic/architecture/data-layer
    // TODO Read https://developer.android.com/topic/architecture/data-layer/offline-first
    // TODO obs internet connection state
    override suspend fun getRecipes(): List<Recipe> {
        return withContext(dispatcher) {
            localDatSource.getAllRecipes().toExternal()
        }
    }

    fun getAllItems(): Flow<List<Recipe>> = flow {
            val recipes: List<Recipe> = networkDataSource.getRecipes().toRecipe()
            // TODO(timber) Replace with Timber Logging
            //Log.d("RecipesRepository", "Fetched  ${recipes.size} recipes from the BE")
            emit(recipes)
    }.catch { e->
        // TODO(timber) Replace with Timber Logging
        // Handle error, e.g., emit an empty list or an error state
        println("Error fetching items: ${e.message}")
        emit(emptyList())
        throw e
    }

    override fun getRecipesFlow(): Flow<List<Recipe>> {
        return if (true) { // Network
            getAllItems()
        } else {        // Local
            return localDatSource.observeAll().map { recipes ->
                withContext(dispatcher) {
                    recipes.toExternal()
                }
            }
        }
    }

    override fun getRecipeFlow(uuid: String): Flow<Recipe?> {
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