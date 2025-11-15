package com.tenmilelabs.chefai.data.repository

import com.tenmilelabs.chefai.data.mapper.toDomain
import com.tenmilelabs.chefai.data.mapper.toRoomEntity
import com.tenmilelabs.chefai.data.source.local.RecipeDao
import com.tenmilelabs.chefai.data.source.local.util.decodeHex
import com.tenmilelabs.chefai.data.source.local.util.toUuid
import com.tenmilelabs.chefai.data.source.network.RecipeNetworkDataSource
import com.tenmilelabs.chefai.di.ApplicationScope
import com.tenmilelabs.chefai.di.IoDispatcher
import com.tenmilelabs.chefai.domain.model.Recipe
import com.tenmilelabs.chefai.domain.model.RecipePreview
import com.tenmilelabs.chefai.domain.repository.RecipesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import timber.log.Timber
import java.nio.channels.UnresolvedAddressException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class DefaultRecipeRepository @Inject constructor(
    private val localDatSource: RecipeDao,
    private val networkDataSource: RecipeNetworkDataSource,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : RecipesRepository {
    //TODO implement authentication
    val testUserId = "F47AC10B58CC4372A5670E02B2C3D479".decodeHex().toUuid()
    override suspend fun getRecipes(): List<Recipe> {
        return withContext(dispatcher) {
            localDatSource.observeRecipesWithDetails().first().map { it.toDomain() }
        }
    }

    override fun getRecipesPreviewStream(): Flow<List<RecipePreview>> {
        return localDatSource.observeAllRecipesForUser(testUserId).map { recipes ->
            withContext(dispatcher) {
                recipes.toDomain()
            }
        }.catch { e ->
            logAndReThrow(e)
        }
    }

    override fun getRecipesStream(): Flow<List<Recipe>> {
        return if (false) { // Network
            flow {
                emit(getAllItemsFromNetwork())
            }
        } else {        // Local
            return localDatSource.observeRecipesWithDetails().map { recipes ->
                withContext(dispatcher) {
                    recipes.toDomain()
                }
            }.catch { e ->
                // TODO try to recover by potentially wiping the DB, recreating and re-syncing
                logAndReThrow(e)
            }
        }
    }

    private suspend fun getAllItemsFromNetwork(): List<Recipe> = try {
        val recipes = networkDataSource.getRecipes().toDomain()
        Timber.d("Fetched ${recipes.size} recipes from the BE")
        recipes
    } catch (e: CancellationException) {
        Timber.w("Network request cancelled.")
        throw e
    } catch (e: UnresolvedAddressException) {
        Timber.e(e, "DNS resolution failed. Device may be offline.")
        throw e
    } catch (e: IOException) {
        Timber.e(e, "A network error occurred.")
        throw e
    }

    override fun getRecipeStream(uuid: UUID): Flow<Recipe?> {
        return localDatSource.observeRecipeWithDetails(uuid).map { it?.toDomain() }
    }

    override suspend fun getRecipe(uuid: UUID): Recipe? {
        return localDatSource.getRecipeWithDetails(uuid)?.toDomain()
    }

    override suspend fun createRecipe(recipe: Recipe) {
        localDatSource.upsertRecipe(recipe.toRoomEntity())
    }

    override suspend fun updateRecipe(recipe: Recipe) {
        localDatSource.upsertRecipe(recipe.toRoomEntity())
    }

    override suspend fun deleteAllRecipes() {
        localDatSource.deleteAllRecipes()
    }

    override suspend fun deleteRecipe(recipeId: UUID) {
        localDatSource.deleteRecipe(recipeId)
    }

    private suspend fun <T> FlowCollector<List<T>>.logAndReThrow(
        e: Throwable
    ): Nothing {
        if (e is IllegalStateException) {
            Timber.e(e, "Error observing recipes: ${e.message}")
            Timber.d("StackTrace: " + e.stackTrace)
            emit(emptyList())
        }
        throw e
    }
}