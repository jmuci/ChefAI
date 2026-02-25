package com.tenmilelabs.chefai.core.data.sync

import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.SyncMetadataEntity
import com.tenmilelabs.chefai.core.data.local.room.TransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.SyncMetadataDao
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.mapper.toIngredientEntities
import com.tenmilelabs.chefai.core.data.sync.mapper.toLabelCrossRefs
import com.tenmilelabs.chefai.core.data.sync.mapper.toRecipeEntity
import com.tenmilelabs.chefai.core.data.sync.mapper.toStepEntities
import com.tenmilelabs.chefai.core.data.sync.mapper.toSyncDto
import com.tenmilelabs.chefai.core.data.sync.mapper.toTagCrossRefs
import com.tenmilelabs.chefai.core.data.sync.network.SyncNetworkDataSource
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPushRequest
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPushResponse
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeDto
import com.tenmilelabs.chefai.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(
    val pushResult: PushResult,
    val pullResult: PullResult
)

data class PushResult(
    val accepted: Int,
    val conflicts: Int,
    val errors: Int
)

data class PullResult(
    val upserted: Int,
    val deleted: Int,
    val pages: Int
)

@Singleton
class SyncOrchestrator @Inject constructor(
    private val syncNetworkDataSource: SyncNetworkDataSource,
    private val recipeDao: RecipeDao,
    private val recipeStepDao: RecipeStepDao,
    private val recipeIngredientDao: RecipeIngredientDao,
    private val recipeTagCrossRefDao: RecipeTagCrossRefDao,
    private val recipeLabelCrossRefDao: RecipeLabelCrossRefDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val transactionRunner: TransactionRunner,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        const val ENTITY_TYPE_RECIPES = "recipes"
        private const val PUSH_BATCH_SIZE = 50
    }

    suspend fun sync(): SyncResult = withContext(ioDispatcher) {
        val pushResult = push()
        val pullResult = pull()
        SyncResult(pushResult, pullResult)
    }

    private suspend fun push(): PushResult {
        val dirtyRecipes = recipeDao.getAllDirty()
        if (dirtyRecipes.isEmpty()) {
            Timber.d("Push: No dirty recipes to push")
            return PushResult(0, 0, 0)
        }

        Timber.d("Push: Found ${dirtyRecipes.size} dirty recipes")

        val syncRecipes = dirtyRecipes.map { recipe -> buildSyncRecipeDto(recipe) }

        var totalAccepted = 0
        var totalConflicts = 0
        var totalErrors = 0

        // Batch into groups of PUSH_BATCH_SIZE
        syncRecipes.chunked(PUSH_BATCH_SIZE).forEach { batch ->
            Timber.d("Push: Sending batch of ${batch.size} recipes")
            val response = syncNetworkDataSource.pushRecipes(SyncPushRequest(batch))
            processPushResponse(response)
            totalAccepted += response.accepted.size
            totalConflicts += response.conflicts.size
            totalErrors += response.errors.size
        }

        Timber.d("Push: completed — accepted=$totalAccepted, conflicts=$totalConflicts, errors=$totalErrors")
        return PushResult(totalAccepted, totalConflicts, totalErrors)
    }

    private suspend fun buildSyncRecipeDto(recipe: RecipeEntity): SyncRecipeDto {
        val steps = recipeStepDao.getStepsForRecipe(recipe.uuid)
        val ingredients = recipeIngredientDao.getIngredientsForRecipe(recipe.uuid)
        val tags = recipeTagCrossRefDao.getTagsForRecipe(recipe.uuid)
        val labels = recipeLabelCrossRefDao.getLabelsForRecipe(recipe.uuid)
        return recipe.toSyncDto(steps, ingredients, tags, labels)
    }

    private suspend fun processPushResponse(response: SyncPushResponse) {
        // Process accepted recipes
        for (accepted in response.accepted) {
            val uuid = UUID.fromString(accepted.uuid)
            transactionRunner {
                recipeDao.updateSyncState(uuid, SyncState.SYNCED, accepted.serverUpdatedAt)
                recipeStepDao.updateSyncStateForRecipe(uuid, SyncState.SYNCED, accepted.serverUpdatedAt)
                recipeIngredientDao.updateSyncStateForRecipe(uuid, SyncState.SYNCED, accepted.serverUpdatedAt)
                recipeTagCrossRefDao.updateSyncStateForRecipe(uuid, SyncState.SYNCED, accepted.serverUpdatedAt)
                recipeLabelCrossRefDao.updateSyncStateForRecipe(uuid, SyncState.SYNCED, accepted.serverUpdatedAt)
            }
        }

        // Process conflicts: server wins, replace local
        for (conflict in response.conflicts) {
            Timber.d("Push conflict for ${conflict.uuid}: ${conflict.reason}")
            transactionRunner {
                upsertRecipeAggregate(conflict.serverVersion)
            }
        }

        // Process errors: log and keep as PENDING for retry
        for (error in response.errors) {
            Timber.w("Push error for ${error.uuid}: ${error.reason} - ${error.message}")
        }
    }

    private suspend fun pull(): PullResult {
        var since = syncMetadataDao.getLastSyncedAt(ENTITY_TYPE_RECIPES) ?: 0L
        var pages = 0
        var totalUpserted = 0
        var totalDeleted = 0

        Timber.d("Pull: starting from checkpoint=$since")

        do {
            val response = syncNetworkDataSource.pullRecipes(since = since, limit = 100)
            Timber.d("Pull: received ${response.recipes.size} recipes, hasMore=${response.hasMore}")

            transactionRunner {
                for (syncRecipe in response.recipes) {
                    val result = applyPulledRecipe(syncRecipe)
                    if (result == ApplyResult.DELETED) totalDeleted++ else totalUpserted++
                }
            }

            syncMetadataDao.upsert(
                SyncMetadataEntity(ENTITY_TYPE_RECIPES, response.serverTimestamp)
            )

            since = response.serverTimestamp
            pages++
        } while (response.hasMore)

        Timber.d("Pull: completed — upserted=$totalUpserted, deleted=$totalDeleted, pages=$pages")
        return PullResult(totalUpserted, totalDeleted, pages)
    }

    private suspend fun applyPulledRecipe(syncRecipe: SyncRecipeDto): ApplyResult {
        val recipeUuid = UUID.fromString(syncRecipe.uuid)
        val localRecipe = recipeDao.getRecipeById(recipeUuid)

        // Handle soft-deleted recipes
        if (syncRecipe.deletedAt != null) {
            if (localRecipe != null) {
                recipeDao.upsertRecipe(
                    localRecipe.copy(
                        deletedAt = syncRecipe.deletedAt,
                        syncState = SyncState.SYNCED,
                        updatedAt = syncRecipe.updatedAt
                    )
                )
            }
            return ApplyResult.DELETED
        }

        when {
            // New recipe from server
            localRecipe == null -> {
                upsertRecipeAggregate(syncRecipe)
            }
            // Previously synced, update with server version
            localRecipe.syncState == SyncState.SYNCED -> {
                upsertRecipeAggregate(syncRecipe)
            }
            // Local has unpushed changes
            localRecipe.syncState == SyncState.PENDING -> {
                if (syncRecipe.updatedAt > localRecipe.updatedAt) {
                    // Server wins
                    upsertRecipeAggregate(syncRecipe)
                }
                // else: local is newer, skip — push will send it next cycle
            }
        }

        return ApplyResult.UPSERTED
    }

    private suspend fun upsertRecipeAggregate(syncRecipe: SyncRecipeDto) {
        val recipeId = UUID.fromString(syncRecipe.uuid)

        recipeDao.upsertRecipe(syncRecipe.toRecipeEntity())

        recipeStepDao.deleteAllForRecipe(recipeId)
        recipeStepDao.upsertAll(syncRecipe.toStepEntities())

        recipeIngredientDao.deleteAllForRecipe(recipeId)
        recipeIngredientDao.upsertAll(syncRecipe.toIngredientEntities())

        recipeTagCrossRefDao.deleteAllForRecipe(recipeId)
        recipeTagCrossRefDao.upsertAll(syncRecipe.toTagCrossRefs())

        recipeLabelCrossRefDao.deleteAllForRecipe(recipeId)
        recipeLabelCrossRefDao.upsertAll(syncRecipe.toLabelCrossRefs())
    }

    private enum class ApplyResult { UPSERTED, DELETED }
}
