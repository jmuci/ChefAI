package com.tenmilelabs.chefai.data

import android.util.Log
import com.tenmilelabs.chefai.data.source.local.RecipeDao
import com.tenmilelabs.chefai.data.source.network.ChefAIApiService
import com.tenmilelabs.chefai.data.source.network.NetworkRecipeList
import com.tenmilelabs.chefai.di.ApplicationScope
import com.tenmilelabs.chefai.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.Throws

@Singleton
class DefaultRecipeRepository @Inject constructor(
    private val localDatSource: RecipeDao,
    private val networkDataSource: ChefAIApiService,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) : RecipesRepository {

    override suspend fun getRecipes(): List<Recipe> {
        return withContext(dispatcher) {
            localDatSource.getAllRecipes().toExternal()
        }
    }

    fun getAllItems(): Flow<List<Recipe>> = flow {
        try {
            val recipes: List<Recipe> = networkDataSource.getRecipes().toRecipe()
            Log.d("RecipesRepository", "Fetched  ${recipes.size} recipes from the BE")
            emit(recipes)
        } catch (e: Exception) {
            // Handle error, e.g., emit an empty list or an error state
            println("Error fetching items: ${e.message}")
            emit(emptyList())
            throw e
        }
    }

    override fun getRecipesFlow(): Flow<List<Recipe>> {
        return if (true) {
            getAllItems()
        } else {
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