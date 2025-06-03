package com.tenmilelabs.chefai.data

import com.tenmilelabs.chefai.data.source.local.RecipeDao
import com.tenmilelabs.chefai.data.source.local.RecipeEntity
import com.tenmilelabs.chefai.di.DefaultDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Singleton

@Singleton
class DefaultRecipeRepository(
    private val localDatSource: RecipeDao,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    @ApplicationContext private val scope: CoroutineScope,
) : RecipesRepository {

    override suspend fun getRecipes(): List<Recipe> {
        return withContext(dispatcher) {
            localDatSource.getAllRecipes().toExternal()
        }
    }

    override fun getRecipesObservable(): Flow<List<Recipe>> {
        return localDatSource.observeAll().map { recipes ->
            withContext(dispatcher) {
                recipes.toExternal()
            }
        }
    }

    override fun getRecipeStream(uuid: String): Flow<Recipe> {
        return localDatSource.observeRecipeById(uuid).map { it.toExternal() }
    }

    override suspend fun getRecipe(uuid: String): Recipe? {
        return localDatSource.getRecipeById(uuid)?.toExternal()
    }

    override suspend fun createRecipe(recipe: Recipe): String {
        // ID creation might be a complex operation so it's executed using the supplied
        // coroutine dispatcher
        val recipeUid = withContext(dispatcher) {
            UUID.randomUUID().toString()
        }

        localDatSource.upsertRecipe(recipe.copy(uuid = recipeUid).toRecipeEntity())
        return recipeUid
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