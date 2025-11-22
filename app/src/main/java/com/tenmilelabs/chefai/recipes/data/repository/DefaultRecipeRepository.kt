package com.tenmilelabs.chefai.recipes.data.repository

import androidx.room.Transaction
import com.tenmilelabs.chefai.recipes.data.mapper.toCrossRef
import com.tenmilelabs.chefai.recipes.data.mapper.toDomain
import com.tenmilelabs.chefai.recipes.data.mapper.toRecipePreviewDomain
import com.tenmilelabs.chefai.recipes.data.mapper.toRoomEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.room.dao.IngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.LabelDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.TagDao
import com.tenmilelabs.chefai.core.data.local.util.decodeHex
import com.tenmilelabs.chefai.core.data.local.util.toUuid
import com.tenmilelabs.chefai.recipes.data.network.RecipeNetworkDataSource
import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    private val recipeDao: RecipeDao,
    private val recipeStepDao: RecipeStepDao,
    private val recipeIngredientDao: RecipeIngredientDao,
    private val ingredientDao: IngredientDao,
    private val tagDao: TagDao,
    private val labelDao: LabelDao,
    private val recipeTagDao: RecipeTagCrossRefDao,
    private val recipeLabelDao: RecipeLabelCrossRefDao,
    private val networkDataSource: RecipeNetworkDataSource,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher) : RecipesRepository {
    //TODO implement authentication
    val testUserId = "F47AC10B58CC4372A5670E02B2C3D479".decodeHex().toUuid()

    override fun getRecipesPreviewStream(): Flow<List<RecipePreview>> {
        return recipeDao.observeRecipesWithDetailsForUser(testUserId).map { recipes ->
            withContext(dispatcher) {
                recipes.toRecipePreviewDomain()
            }
        }.catch { e ->
            logAndReThrow(e, emptyList())
        }
    }

    override fun getRecipesStream(): Flow<List<Recipe>> {
        return if (false) { // Network
            flow {
                emit(getAllItemsFromNetwork())
            }
        } else {        // Local
            return recipeDao.observeRecipesWithDetails().map { recipes ->
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
        val ingredientsFlow = recipeDao.observeIngredientsForRecipe(uuid)
        val recipeFlow = recipeDao.observeRecipeWithDetails(uuid)
        return combine(ingredientsFlow, recipeFlow) {
            ingredients, recipesWithDetails ->
            if (recipesWithDetails == null) {
                return@combine null
            }
            recipesWithDetails.toDomain(ingredients)
        }.catch { e ->
            // TODO try to recover by potentially wiping the DB, recreating and re-syncing
            logAndReThrow(e)
        }

            //.map { it?.toDomain() }
    }

    override suspend fun getRecipe(uuid: UUID): Recipe? {
        return recipeDao.getRecipeWithDetails(uuid)?.toDomain()
    }

    @Transaction
    override suspend fun createRecipe(recipe: Recipe) {
        withContext(dispatcher) {
            // Save the recipe entity
            recipeDao.upsertRecipe(recipe.toRoomEntity())

            // Save all tags (if they don't exist)
            recipe.tags.forEach { tag ->
                tagDao.upsertTag(tag.toRoomEntity())
            }

            // Save all labels (if they don't exist)
            recipe.labels.forEach { label ->
                labelDao.upsertLabel(label.toRoomEntity())
            }

            // Save all ingredients (if they don't exist)
            recipe.ingredients.forEach { ingredient ->
                ingredientDao.upsertIngredient(ingredient.toRoomEntity())
            }

            // Save recipe steps
            recipe.steps.forEach { step ->
                recipeStepDao.upsertStep(step.toRoomEntity(recipe.uuid))
            }

            // Upsert ingredient cross-references for the recipe
            recipeIngredientDao.upsertAllForRecipe(
                recipe.uuid,
                recipe.ingredients.map { it.toCrossRef(recipe.uuid) }
            )

            // Save recipe-tag cross-references
            recipe.tags.forEach { tag ->
                recipeTagDao.upsertCrossRef(
                    RecipeTagCrossRef(
                        recipeId = recipe.uuid,
                        tagId = tag.uuid,
                        updatedAt = System.currentTimeMillis(),
                        deletedAt = null,
                        syncState = com.tenmilelabs.chefai.core.data.local.util.SyncState.PENDING
                    )
                )
            }

            // Save recipe-label cross-references
            recipe.labels.forEach { label ->
                recipeLabelDao.upsertCrossRef(
                    RecipeLabelCrossRef(
                        recipeId = recipe.uuid,
                        labelId = label.uuid,
                        updatedAt = System.currentTimeMillis(),
                        deletedAt = null,
                        syncState = com.tenmilelabs.chefai.core.data.local.util.SyncState.PENDING
                    )
                )
            }
        }
    }

    override suspend fun updateRecipe(recipe: Recipe) {
        recipeDao.upsertRecipe(recipe.toRoomEntity())
    }

    override suspend fun deleteAllRecipes() {
        recipeDao.deleteAllRecipes()
    }

    override suspend fun deleteRecipe(recipeId: UUID) {
        recipeDao.deleteRecipe(recipeId)
    }

    private suspend fun <T> FlowCollector<T>.logAndReThrow(
        e: Throwable,
        emptyObject: T? = null
    ): Nothing {
        if (e is IllegalStateException) {
            Timber.e(e, "Error observing recipes: ${e.message}")
            Timber.d("StackTrace: " + e.stackTrace)
            if (emptyObject != null) {
                emit(emptyObject)
            }
        }
        throw e
    }
}