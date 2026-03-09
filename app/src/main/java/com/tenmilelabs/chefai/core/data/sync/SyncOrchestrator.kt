package com.tenmilelabs.chefai.core.data.sync

import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.SyncMetadataEntity
import com.tenmilelabs.chefai.core.data.local.room.TransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.AllergenDao
import com.tenmilelabs.chefai.core.data.local.room.dao.IngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.LabelDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.SourceClassificationDao
import com.tenmilelabs.chefai.core.data.local.room.dao.SyncMetadataDao
import com.tenmilelabs.chefai.core.data.local.room.dao.TagDao
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.mapper.toAllergenEntity
import com.tenmilelabs.chefai.core.data.sync.mapper.toIngredientEntities
import com.tenmilelabs.chefai.core.data.sync.mapper.toIngredientEntity
import com.tenmilelabs.chefai.core.data.sync.mapper.toLabelCrossRefs
import com.tenmilelabs.chefai.core.data.sync.mapper.toLabelEntity
import com.tenmilelabs.chefai.core.data.sync.mapper.toRecipeEntity
import com.tenmilelabs.chefai.core.data.sync.mapper.toSourceClassificationEntity
import com.tenmilelabs.chefai.core.data.sync.mapper.toStepEntities
import com.tenmilelabs.chefai.core.data.sync.mapper.toSyncDto
import com.tenmilelabs.chefai.core.data.sync.mapper.toTagCrossRefs
import com.tenmilelabs.chefai.core.data.sync.mapper.toTagEntity
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
    private val allergenDao: AllergenDao,
    private val sourceClassificationDao: SourceClassificationDao,
    private val ingredientDao: IngredientDao,
    private val tagDao: TagDao,
    private val labelDao: LabelDao,
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
        val allIngredients = recipeIngredientDao.getIngredientsForRecipe(recipe.uuid)
        val tags = recipeTagCrossRefDao.getTagsForRecipe(recipe.uuid)
        val labels = recipeLabelCrossRefDao.getLabelsForRecipe(recipe.uuid)

        // Only push ingredient refs that are known to the server (i.e., pulled via sync).
        // Locally-seeded or fake ingredient IDs will not have syncState=SYNCED and would
        // cause INGREDIENT_NOT_FOUND on the backend.
        // TODO Revisit after https://github.com/jmuci/ChefAI/issues/101
        val ingredients = if (allIngredients.isEmpty()) {
            emptyList()
        } else {
            val ids = allIngredients.map { it.ingredientId }
            val syncedIds = ingredientDao.getSyncedExistingIds(ids).toSet()
            val filtered = allIngredients.filter { it.ingredientId in syncedIds }
            if (filtered.size < allIngredients.size) {
                Timber.w(
                    "buildSyncRecipeDto: recipe %s has %d ingredient ref(s) not known to server, skipping",
                    recipe.uuid,
                    allIngredients.size - filtered.size
                )
            }
            filtered
        }

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
                // Upsert reference data in FK dependency order before recipes:
                //   allergens (leaf) → ingredients.allergenId (RESTRICT)
                //   source_classifications (leaf) → ingredients.sourcePrimaryId (SET_NULL)
                //   ingredients → recipe_ingredients.ingredientId (RESTRICT)
                //   tags (leaf) → recipe_tags.tagId (RESTRICT)
                //   labels (leaf) → recipe_labels.labelId (RESTRICT)
                allergenDao.upsertAll(response.allergens.map { it.toAllergenEntity() })
                sourceClassificationDao.upsertAll(response.sourceClassifications.map { it.toSourceClassificationEntity() })
                ingredientDao.upsertAll(response.ingredients.map { it.toIngredientEntity() })
                tagDao.upsertAll(response.tags.map { it.toTagEntity() })
                labelDao.upsertAll(response.labels.map { it.toLabelEntity() })

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
        val ingredientEntities = syncRecipe.toIngredientEntities()
        // We prevent upserting recipes that reference ingredients that that don't exist in Room.
        if (ingredientEntities.isNotEmpty()) {
            val referencedIds = ingredientEntities.map { it.ingredientId }
            val existingIds = ingredientDao.getExistingIds(referencedIds).toSet()
            val validEntities = ingredientEntities.filter { it.ingredientId in existingIds }
            if (validEntities.size < ingredientEntities.size) {
                val missing = referencedIds.filterNot { it in existingIds }
                Timber.w("upsertRecipeAggregate: recipe %s references %d unknown ingredientId(s), skipping them: %s", syncRecipe.uuid, missing.size, missing)
            }
            recipeIngredientDao.upsertAll(validEntities)
        }

        recipeTagCrossRefDao.deleteAllForRecipe(recipeId)
        val tagCrossRefs = syncRecipe.toTagCrossRefs()
        if (tagCrossRefs.isNotEmpty()) {
            val referencedTagIds = tagCrossRefs.map { it.tagId }
            val existingTagIds = tagDao.getExistingIds(referencedTagIds).toSet()
            val validTagRefs = tagCrossRefs.filter { it.tagId in existingTagIds }
            if (validTagRefs.size < tagCrossRefs.size) {
                val missing = referencedTagIds.filterNot { it in existingTagIds }
                Timber.w("upsertRecipeAggregate: recipe %s references %d unknown tagId(s), skipping them: %s", syncRecipe.uuid, missing.size, missing)
            }
            recipeTagCrossRefDao.upsertAll(validTagRefs)
        }

        recipeLabelCrossRefDao.deleteAllForRecipe(recipeId)
        val labelCrossRefs = syncRecipe.toLabelCrossRefs()
        if (labelCrossRefs.isNotEmpty()) {
            val referencedLabelIds = labelCrossRefs.map { it.labelId }
            val existingLabelIds = labelDao.getExistingIds(referencedLabelIds).toSet()
            val validLabelRefs = labelCrossRefs.filter { it.labelId in existingLabelIds }
            if (validLabelRefs.size < labelCrossRefs.size) {
                val missing = referencedLabelIds.filterNot { it in existingLabelIds }
                Timber.w("upsertRecipeAggregate: recipe %s references %d unknown labelId(s), skipping them: %s", syncRecipe.uuid, missing.size, missing)
            }
            recipeLabelCrossRefDao.upsertAll(validLabelRefs)
        }
    }

    private enum class ApplyResult { UPSERTED, DELETED }
}
