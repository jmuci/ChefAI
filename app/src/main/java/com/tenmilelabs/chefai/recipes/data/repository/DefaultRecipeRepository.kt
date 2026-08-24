package com.tenmilelabs.chefai.recipes.data.repository

import androidx.room.Transaction
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.room.dao.IngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.LabelDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.TagDao
import com.tenmilelabs.chefai.core.data.sync.RecipeFetchOutcome
import com.tenmilelabs.chefai.core.data.sync.SyncOrchestrator
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore
import com.tenmilelabs.chefai.recipes.data.mapper.toCrossRef
import com.tenmilelabs.chefai.recipes.data.mapper.toDomain
import com.tenmilelabs.chefai.recipes.data.mapper.toRecipePreviewDomain
import com.tenmilelabs.chefai.recipes.data.mapper.toRoomEntity
import com.tenmilelabs.chefai.recipes.data.network.RecipeNetworkDataSource
import com.tenmilelabs.chefai.recipes.domain.repository.RecipeFetchResult
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    private val sessionManager: SessionManager,
    private val recipeImageStore: RecipeImageStore,
    private val syncManager: SyncScheduler,
    private val syncOrchestrator: SyncOrchestrator) : RecipesRepository {

    override fun getRecipesPreviewStream(): Flow<List<RecipePreview>> {
        // TODO pass the userId to only show recipes from the user when filtering.
        // val userId = sessionManager.getCurrentUser()?.uuid
        return recipeDao.observeRecipesWithDetails().map { recipes ->
            recipes.toRecipePreviewDomain()
        }.catch { e ->
            logAndReThrow(e, emptyList())
        }
    }

    override fun getRecipesPreviewStreamForUser(userUuid: UUID): Flow<List<RecipePreview>> {
        return recipeDao.observeRecipesWithDetailsForUser(userUuid).map { recipes ->
            recipes.toRecipePreviewDomain()
        }.catch { e ->
            logAndReThrow(e, emptyList())
        }
    }

    override fun getPublicRecipesStream(): Flow<List<Recipe>> {
        return recipeDao.observePublicRecipesWithDetails().map { recipes ->
            recipes.toDomain()
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
            return recipeDao.observeRecipesWithDetails().map { recipes ->
                recipes.toDomain()
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
        val details = recipeDao.getRecipeWithDetails(uuid) ?: return null
        val ingredients = recipeDao.observeIngredientsForRecipe(uuid).first()
        return details.toDomain(ingredients)
    }

    override suspend fun getOrFetchRecipe(uuid: UUID): RecipeFetchResult {
        getRecipe(uuid)?.let { return RecipeFetchResult.Found(it) }
        return when (syncOrchestrator.fetchAndPersistRecipe(uuid)) {
            // Re-reads via getRecipe rather than mapping the fetched DTO directly, reusing its
            // existing DAO-to-domain mapping instead of adding a second one.
            RecipeFetchOutcome.PERSISTED -> getRecipe(uuid)?.let { RecipeFetchResult.Found(it) }
                ?: RecipeFetchResult.NotAvailable
            RecipeFetchOutcome.NOT_AVAILABLE -> RecipeFetchResult.NotAvailable
            RecipeFetchOutcome.NETWORK_ERROR -> RecipeFetchResult.NetworkError
        }
    }

    @Transaction
    override suspend fun createRecipe(recipe: Recipe) {
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

        syncManager.requestMutationSync()
    }

    @Transaction
    override suspend fun updateRecipe(recipe: Recipe) {
        // Update the recipe entity
        recipeDao.upsertRecipe(recipe.toRoomEntity())

        // Replace steps: delete old, insert new
        recipeStepDao.deleteAllForRecipe(recipe.uuid)
        recipe.steps.forEach { step ->
            recipeStepDao.upsertStep(step.toRoomEntity(recipe.uuid))
        }

        // Upsert ingredients (ensure ingredient entities exist)
        recipe.ingredients.forEach { ingredient ->
            ingredientDao.upsertIngredient(ingredient.toRoomEntity())
        }

        // Replace ingredient cross-references
        recipeIngredientDao.upsertAllForRecipe(
            recipe.uuid,
            recipe.ingredients.map { it.toCrossRef(recipe.uuid) }
        )

        // Upsert tags and replace cross-references
        recipe.tags.forEach { tag ->
            tagDao.upsertTag(tag.toRoomEntity())
        }
        recipeTagDao.deleteAllForRecipe(recipe.uuid)
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

        // Upsert labels and replace cross-references
        recipe.labels.forEach { label ->
            labelDao.upsertLabel(label.toRoomEntity())
        }
        recipeLabelDao.deleteAllForRecipe(recipe.uuid)
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

        syncManager.requestMutationSync()
    }

    override suspend fun deleteAllRecipes() {
        recipeDao.deleteAllRecipes()
    }

    override fun getRecipePreviewsByIds(ids: List<UUID>): Flow<List<RecipePreview>> {
        if (ids.isEmpty()) return flowOf(emptyList())
        val idSet = ids.toHashSet()
        return getRecipesPreviewStream().map { all -> all.filter { it.uuid in idSet } }
    }

    override suspend fun deleteRecipe(recipeId: UUID) {
        recipeDao.deleteRecipe(recipeId)
        recipeImageStore.delete(recipeId)
        syncManager.requestMutationSync()
    }

    /**
     * Marks the recipe deleted and reclaims its cached image.
     *
     * The image goes now rather than on some later hard delete because there is no later hard delete:
     * nothing in the app ever removes a soft-deleted row, so waiting would mean keeping up to
     * `MAX_IMAGE_BYTES` per deleted recipe forever. Deletion has no undo (#129), and the row is gone
     * from every query the moment `deletedAt` is set.
     *
     * For a scraped image this costs nothing — the backfill can re-derive it from `imageUrl` if the
     * recipe ever comes back. For a user-picked photo it is irreversible, since there is no source to
     * re-derive from. That asymmetry is the clearest argument for uploading user-authored images,
     * which is Stage 2 of #132; see ADR-011.
     */
    override suspend fun softDeleteRecipe(recipeId: UUID) {
        recipeDao.softDelete(recipeId, System.currentTimeMillis())
        recipeImageStore.delete(recipeId)
        syncManager.requestMutationSync()
    }

    override suspend fun getRecipeCountForUser(userId: UUID): Int =
        recipeDao.countRecipesForUser(userId)

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