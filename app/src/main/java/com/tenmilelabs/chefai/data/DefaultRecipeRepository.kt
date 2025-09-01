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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    companion object {
        private const val CACHE_TTL = 10 * 1000; // 10 seconds
    }

    // Mutex to make writes to cached values thread-safe.
    private val latestNewsMutex = Mutex()

    // Cache of the latest news got from the network.
    private var latestNetworkRecipes: List<Recipe> = emptyList()
    private var cacheExpireTime = 0L;


    override suspend fun getRecipes(): List<Recipe> {
        return withContext(dispatcher) {
            localDatSource.getAllRecipes().toExternal()
        }
    }

    suspend fun getAllItems(): List<Recipe> {
        try {
            return networkDataSource.getRecipes().toRecipe()
        } catch (e: Exception) {
            // TODO(timber) Replace with Timber Logging
            // Handle error, e.g., emit an empty list or an error state
            println("Error fetching items from network: ${e.message}")
            //emit(emptyList())
            throw e
        }
    }

    override fun getRecipesFlow(refresh: Boolean): Flow<List<Recipe>> {
        return if (refresh
            || latestNetworkRecipes.isEmpty()
            || cacheExpireTime > System.currentTimeMillis()
        ) {         // Network
            flow {
                var recipes = getAllItems()
                cacheExpireTime = System.currentTimeMillis() + CACHE_TTL;
                // Thread-safe write to cache latest recipes
                latestNewsMutex.withLock {
                    latestNetworkRecipes = recipes;
                }
                // Update local data source
                localDatSource.upsertAll(recipes.toLocal())
                emit(recipes)
            }
        } else {  // Local
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