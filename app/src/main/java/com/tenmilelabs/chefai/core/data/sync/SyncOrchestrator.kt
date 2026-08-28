package com.tenmilelabs.chefai.core.data.sync

import com.tenmilelabs.chefai.core.data.local.room.BookmarkedRecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.MealPlanDayEntity
import com.tenmilelabs.chefai.core.data.local.room.carryForwardCookedMarks
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.SyncMetadataEntity
import com.tenmilelabs.chefai.core.data.local.room.TransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.AllergenDao
import com.tenmilelabs.chefai.core.data.local.room.dao.BookmarkedRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.IngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.LabelDao
import com.tenmilelabs.chefai.core.data.local.room.dao.MealPlanDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeImageStateDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.SourceClassificationDao
import com.tenmilelabs.chefai.core.data.local.room.dao.SyncMetadataDao
import com.tenmilelabs.chefai.core.data.local.room.dao.TagDao
import com.tenmilelabs.chefai.core.data.local.room.dao.UserDao
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.mapper.toAllergenEntity
import com.tenmilelabs.chefai.core.data.sync.mapper.toIngredientEntities
import com.tenmilelabs.chefai.core.data.sync.mapper.toIngredientEntity
import com.tenmilelabs.chefai.core.data.sync.mapper.toLabelCrossRefs
import com.tenmilelabs.chefai.core.data.sync.mapper.toLabelEntity
import com.tenmilelabs.chefai.core.data.sync.mapper.toMealPlanDayEntity
import com.tenmilelabs.chefai.core.data.sync.mapper.toMealPlanEntity
import com.tenmilelabs.chefai.core.data.sync.mapper.toRecipeEntity
import com.tenmilelabs.chefai.core.data.sync.mapper.toSourceClassificationEntity
import com.tenmilelabs.chefai.core.data.sync.mapper.toStepEntities
import com.tenmilelabs.chefai.core.data.sync.mapper.toSyncDto
import com.tenmilelabs.chefai.core.data.sync.mapper.toTagCrossRefs
import com.tenmilelabs.chefai.core.data.sync.mapper.toTagEntity
import com.tenmilelabs.chefai.core.data.sync.mapper.toUserEntity
import com.tenmilelabs.chefai.core.data.sync.network.SyncNetworkDataSource
import com.tenmilelabs.chefai.core.data.sync.network.dto.GenerateMealPlanStatelessResponseDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncBookmarkPushDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncCreatorDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncMealPlanDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncReferenceDataDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPushRequest
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPushResponse
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeDto
import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.recipes.data.network.RecipeDetailNetworkDataSource
import com.tenmilelabs.chefai.recipes.data.network.RecipeDetailNetworkResult
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

/** Outcome of [SyncOrchestrator.fetchAndPersistRecipe] — see ChefAI#186. */
enum class RecipeFetchOutcome { PERSISTED, NOT_AVAILABLE, NETWORK_ERROR }

@Singleton
class SyncOrchestrator @Inject constructor(
    private val syncNetworkDataSource: SyncNetworkDataSource,
    private val recipeDetailNetworkDataSource: RecipeDetailNetworkDataSource,
    private val allergenDao: AllergenDao,
    private val sourceClassificationDao: SourceClassificationDao,
    private val ingredientDao: IngredientDao,
    private val tagDao: TagDao,
    private val labelDao: LabelDao,
    private val userDao: UserDao,
    private val recipeDao: RecipeDao,
    private val recipeImageStateDao: RecipeImageStateDao,
    private val recipeStepDao: RecipeStepDao,
    private val recipeIngredientDao: RecipeIngredientDao,
    private val recipeTagCrossRefDao: RecipeTagCrossRefDao,
    private val recipeLabelCrossRefDao: RecipeLabelCrossRefDao,
    private val bookmarkedRecipeDao: BookmarkedRecipeDao,
    private val mealPlanDao: MealPlanDao,
    private val sessionManager: SessionManager,
    private val syncMetadataDao: SyncMetadataDao,
    private val transactionRunner: TransactionRunner,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SyncExecutor {
    companion object {
        const val ENTITY_TYPE_RECIPES = "recipes"
        private const val PUSH_BATCH_SIZE = 50
    }

    override suspend fun sync(): SyncResult = withContext(ioDispatcher) {
        val pushResult = push()
        val pullResult = pull()
        SyncResult(pushResult, pullResult)
    }

    private suspend fun push(): PushResult {
        val dirtyRecipes = recipeDao.getAllDirty()
        val allDirtyBookmarks = bookmarkedRecipeDao.getAllDirty()

        // Only push bookmarks that belong to the current authenticated user.
        // Bookmarks with stale local UUIDs would be rejected by the backend (USER_MISMATCH).
        val authenticatedUserId = sessionManager.getCurrentUserId()
        val dirtyBookmarks = if (authenticatedUserId != null) {
            val (valid, stale) = allDirtyBookmarks.partition { it.userId == authenticatedUserId }
            if (stale.isNotEmpty()) {
                Timber.w("Push: Skipping ${stale.size} bookmark(s) with non-matching userId (expected $authenticatedUserId)")
            }
            valid
        } else {
            Timber.w("Push: No authenticated userId available, skipping all bookmark pushes")
            emptyList()
        }

        val dirtyMealPlans = mealPlanDao.getAllDirty()
        val syncMealPlans = dirtyMealPlans.map { plan ->
            val days = mealPlanDao.getDaysForMealPlan(plan.uuid)
            plan.toSyncDto(days)
        }

        if (dirtyRecipes.isEmpty() && dirtyBookmarks.isEmpty() && dirtyMealPlans.isEmpty()) {
            Timber.d("Push: Nothing dirty to push")
            return PushResult(0, 0, 0)
        }

        Timber.d("Push: Found ${dirtyRecipes.size} dirty recipes, ${dirtyBookmarks.size} dirty bookmarks, ${dirtyMealPlans.size} dirty meal plans")

        val syncRecipes = dirtyRecipes.map { recipe -> buildSyncRecipeDto(recipe) }
        val syncBookmarks = dirtyBookmarks.map { it.toSyncPushDto() }

        var totalAccepted = 0
        var totalConflicts = 0
        var totalErrors = 0

        // Batch recipes; bookmarks ride along in every batch (or a final bookmark-only batch)
        val recipeBatches = syncRecipes.chunked(PUSH_BATCH_SIZE).ifEmpty { listOf(emptyList()) }
        recipeBatches.forEachIndexed { index, recipeBatch ->
            // Only include bookmarks and meal plans in the last batch to avoid duplicate processing
            val bookmarkBatch = if (index == recipeBatches.lastIndex) syncBookmarks else emptyList()
            val mealPlanBatch = if (index == recipeBatches.lastIndex) syncMealPlans else emptyList()
            Timber.d("Push: Sending batch of ${recipeBatch.size} recipes, ${bookmarkBatch.size} bookmarks, ${mealPlanBatch.size} meal plans")
            // TODO decouple synchronization of unrelated entities such a recipes and meal plans.
            val response = syncNetworkDataSource.pushRecipes(SyncPushRequest(recipeBatch, bookmarkBatch, mealPlanBatch))
            processPushResponse(response, recipeBatch)
            totalAccepted += response.accepted.size
            totalConflicts += response.conflicts.size
            totalErrors += response.errors.size
        }

        Timber.d("Push: completed — accepted=$totalAccepted, conflicts=$totalConflicts, errors=$totalErrors")
        return PushResult(totalAccepted, totalConflicts, totalErrors)
    }

    private fun BookmarkedRecipeEntity.toSyncPushDto() = SyncBookmarkPushDto(
        userId = userId.toString(),
        recipeId = recipeId.toString(),
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

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

    private suspend fun processPushResponse(response: SyncPushResponse, pushedRecipes: List<SyncRecipeDto>) {
        // Unlike steps/tags/labels, ingredient refs are filtered before push (buildSyncRecipeDto
        // only sends ones already known to the server; see #101) — so "recipe X was accepted"
        // doesn't mean every local ingredient ref for X was actually sent. Blanket-marking every
        // local row SYNCED used to lie about the ones filtered out, which made the following pull's
        // upsertRecipeAggregate treat them as safe to delete — permanently losing ingredients the
        // server was never told about. Marking only the ones actually included keeps the rest
        // PENDING so upsertRecipeAggregate can tell the two cases apart and preserve them.
        val pushedIngredientIdsByRecipe = pushedRecipes.associate { dto ->
            dto.uuid to dto.ingredients.map { UUID.fromString(it.ingredientId) }
        }

        // Process accepted recipes
        for (accepted in response.accepted) {
            val uuid = UUID.fromString(accepted.uuid)
            transactionRunner {
                recipeDao.updateSyncState(uuid, SyncState.SYNCED, accepted.serverUpdatedAt)
                recipeStepDao.updateSyncStateForRecipe(uuid, SyncState.SYNCED, accepted.serverUpdatedAt)
                val pushedIngredientIds = pushedIngredientIdsByRecipe[accepted.uuid].orEmpty()
                if (pushedIngredientIds.isNotEmpty()) {
                    recipeIngredientDao.updateSyncStateForRecipeIngredients(
                        uuid, pushedIngredientIds, SyncState.SYNCED, accepted.serverUpdatedAt
                    )
                }
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

        // Process accepted bookmarks
        for (accepted in response.bookmarkedRecipes) {
            val userId = UUID.fromString(accepted.userId)
            val recipeId = UUID.fromString(accepted.recipeId)
            bookmarkedRecipeDao.updateSyncState(userId, recipeId, SyncState.SYNCED, accepted.serverUpdatedAt)
        }

        // Log bookmark errors — keep them as PENDING for retry
        for (error in response.bookmarkErrors) {
            Timber.w("Bookmark push error for recipeId=${error.recipeId}: ${error.reason} - ${error.message}")
        }

        // Process accepted meal plans
        for (accepted in response.mealPlans.accepted) {
            val uuid = UUID.fromString(accepted.uuid)
            mealPlanDao.updateSyncState(uuid, SyncState.SYNCED, accepted.serverUpdatedAt)
        }

        // Meal plan conflicts: server wins — next pull will overwrite
        if (response.mealPlans.conflicts.isNotEmpty()) {
            Timber.w("MealPlan push conflicts: ${response.mealPlans.conflicts}")
        }

        // Meal plan errors: log, keep as PENDING for retry
        for (error in response.mealPlans.errors) {
            Timber.w("MealPlan push error for ${error.uuid}: ${error.reason} - ${error.message}")
        }
    }

    private suspend fun pull(): PullResult {
        var since = syncMetadataDao.getLastSyncedAt(ENTITY_TYPE_RECIPES) ?: 0L
        var pages = 0
        var totalUpserted = 0
        var totalDeleted = 0

        val authenticatedUserId = sessionManager.getCurrentUserId()

        Timber.d("Pull: starting from checkpoint=$since")

        do {
            val response = syncNetworkDataSource.pullRecipes(since = since, limit = 100)
            Timber.d("Pull: received ${response.recipes.size} recipes, ${response.mealPlans.size} meal plans, hasMore=${response.hasMore}")

            transactionRunner {
                // Upsert reference data in FK dependency order before recipes:
                //   creators (leaf) → recipes.creatorId (CASCADE)
                //   allergens (leaf) → ingredients.allergenId (RESTRICT)
                //   source_classifications (leaf) → ingredients.sourcePrimaryId (SET_NULL)
                //   ingredients → recipe_ingredients.ingredientId (RESTRICT)
                //   tags (leaf) → recipe_tags.tagId (RESTRICT)
                //   labels (leaf) → recipe_labels.labelId (RESTRICT)
                userDao.upsertAll(response.creators.map { it.toUserEntity() })
                allergenDao.upsertAll(response.allergens.map { it.toAllergenEntity() })
                sourceClassificationDao.upsertAll(response.sourceClassifications.map { it.toSourceClassificationEntity() })
                ingredientDao.upsertAll(response.ingredients.map { it.toIngredientEntity() })
                tagDao.upsertAll(response.tags.map { it.toTagEntity() })
                labelDao.upsertAll(response.labels.map { it.toLabelEntity() })

                for (syncRecipe in response.recipes) {
                    val result = applyPulledRecipe(syncRecipe)
                    if (result == ApplyResult.DELETED) totalDeleted++ else totalUpserted++
                }

                for (bookmark in response.bookmarkedRecipes) {
                    applyPulledBookmark(bookmark)
                }

                // Meal plans require an authenticated user for the FK
                if (authenticatedUserId != null && response.mealPlans.isNotEmpty()) {
                    for (dto in response.mealPlans) {
                        applyPulledMealPlan(dto, authenticatedUserId, response.serverTimestamp)
                    }
                } else if (response.mealPlans.isNotEmpty()) {
                    Timber.w("Pull: Skipping ${response.mealPlans.size} meal plan(s) — no authenticated user")
                }
            }

            // Advance cursor to max of all entity timestamps so no deltas are re-fetched.
            val bookmarkMaxTs = response.bookmarkedRecipes.maxOfOrNull { it.updatedAt } ?: 0L
            val mealPlanMaxTs = response.mealPlans.maxOfOrNull { it.updatedAt } ?: 0L
            val newCursor = maxOf(response.serverTimestamp, bookmarkMaxTs, mealPlanMaxTs)
            val cursorAdvanced = newCursor > since
            syncMetadataDao.upsert(SyncMetadataEntity(ENTITY_TYPE_RECIPES, newCursor))

            since = newCursor
            pages++

            if (!cursorAdvanced && response.hasMore) {
                // A cursor that can't move forward while the server still claims more data would
                // spin here forever, re-fetching the same page on every iteration. Stop and let
                // the next scheduled sync retry from this checkpoint instead of hanging the
                // caller and hammering the backend indefinitely.
                Timber.w("Pull: cursor stalled at $since after page $pages despite hasMore=true — stopping to avoid an infinite loop")
                break
            }
        } while (response.hasMore)

        Timber.d("Pull: completed — upserted=$totalUpserted, deleted=$totalDeleted, pages=$pages")
        return PullResult(totalUpserted, totalDeleted, pages)
    }

    /**
     * Fetches one recipe by id from `GET /api/v1/recipes/{recipeId}` and persists it, for a
     * search result the device hasn't pulled yet (ChefAI#186) — [pull] can't help here since
     * it's authenticated and delta-scoped, and an anonymous session's [pull] never even runs
     * (see [com.tenmilelabs.chefai.core.data.sync.worker.SyncWorker]).
     *
     * Upserts reference data in the exact same FK order [pull] uses (see the comment in
     * [pull]) before [upsertRecipeAggregate], inside one [transactionRunner] — this is the
     * only transaction in this call path, so nesting isn't a concern even though
     * [upsertRecipeAggregate]'s other two callers each wrap it in their own.
     */
    /**
     * Persists the recipes carried by a stateless meal-plan generation response — see
     * [com.tenmilelabs.chefai.mealplans.data.network.GenerateStatelessResult]. Same FK-order
     * upsert as [fetchAndPersistRecipe], applied to every recipe in the response inside one
     * transaction rather than one recipe at a time.
     */
    suspend fun persistGeneratedRecipes(response: GenerateMealPlanStatelessResponseDto): Unit =
        transactionRunner {
            persistReferenceData(response.creators, response.referenceData)
            response.recipes.forEach { upsertRecipeAggregate(it) }
        }

    suspend fun fetchAndPersistRecipe(recipeId: UUID): RecipeFetchOutcome =
        when (val result = recipeDetailNetworkDataSource.fetchRecipe(recipeId)) {
            is RecipeDetailNetworkResult.Success -> {
                transactionRunner {
                    persistReferenceData(result.response.creators, result.response.referenceData)
                    upsertRecipeAggregate(result.response.recipe)
                }
                RecipeFetchOutcome.PERSISTED
            }
            RecipeDetailNetworkResult.NotFound -> RecipeFetchOutcome.NOT_AVAILABLE
            RecipeDetailNetworkResult.Unauthorized, is RecipeDetailNetworkResult.Error -> {
                Timber.w("fetchAndPersistRecipe: fetch failed for %s: %s", recipeId, result)
                RecipeFetchOutcome.NETWORK_ERROR
            }
        }

    /**
     * Upserts reference data in the FK dependency order [pull] and both callers above rely on:
     * creators (leaf) -> recipes.creatorId (CASCADE); allergens/source classifications (leaf) ->
     * ingredients (RESTRICT/SET_NULL); ingredients -> recipe_ingredients (RESTRICT); tags/labels
     * (leaf) -> their cross-refs (RESTRICT). Must run inside the caller's own [transactionRunner].
     */
    private suspend fun persistReferenceData(
        creators: List<SyncCreatorDto>,
        referenceData: SyncReferenceDataDto,
    ) {
        userDao.upsertAll(creators.map { it.toUserEntity() })
        allergenDao.upsertAll(referenceData.allergens.map { it.toAllergenEntity() })
        sourceClassificationDao.upsertAll(
            referenceData.sourceClassifications.map { it.toSourceClassificationEntity() }
        )
        ingredientDao.upsertAll(referenceData.ingredients.map { it.toIngredientEntity() })
        tagDao.upsertAll(referenceData.tags.map { it.toTagEntity() })
        labelDao.upsertAll(referenceData.labels.map { it.toLabelEntity() })
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

        // The DTO never carries the device-local cached-image path (see SyncMapper.toRecipeEntity) —
        // read back whatever this device already has so this upsert doesn't wipe it.
        val existing = recipeDao.getRecipeById(recipeId)
        recipeDao.upsertRecipe(syncRecipe.toRecipeEntity(existing?.localImagePath))

        // A recipe whose image blob changed — most often from null, when another device finally
        // uploaded one — deserves a fresh set of download attempts. Without this, three failures
        // collected while the image was unobtainable would permanently blind this device to the
        // copy that has since appeared.
        if (existing != null && existing.imageBlobId != syncRecipe.imageBlobId) {
            recipeImageStateDao.delete(recipeId)
        }

        recipeStepDao.deleteAllForRecipe(recipeId)
        recipeStepDao.upsertAll(syncRecipe.toStepEntities())

        // Ingredients this device already has that the server has never accepted — buildSyncRecipeDto
        // filters these out of every push until their catalog entry is SYNCED (#101), so a PENDING
        // cross-ref here doesn't mean "stale," it means "not yet offered to the server." A pull is
        // otherwise server-authoritative, but for this table specifically the server's list is known
        // incomplete, so these must survive the delete-and-replace below or they're gone for good.
        val localPendingIngredients = recipeIngredientDao.getIngredientsForRecipe(recipeId)
            .filter { it.syncState == SyncState.PENDING }

        recipeIngredientDao.deleteAllForRecipe(recipeId)
        val ingredientEntities = syncRecipe.toIngredientEntities()
        // We prevent upserting recipes that reference ingredients that that don't exist in Room.
        val validEntities = if (ingredientEntities.isEmpty()) {
            emptyList()
        } else {
            val referencedIds = ingredientEntities.map { it.ingredientId }
            val existingIds = ingredientDao.getExistingIds(referencedIds).toSet()
            val valid = ingredientEntities.filter { it.ingredientId in existingIds }
            if (valid.size < ingredientEntities.size) {
                val missing = referencedIds.filterNot { it in existingIds }
                Timber.w("upsertRecipeAggregate: recipe %s references %d unknown ingredientId(s), skipping them: %s", syncRecipe.uuid, missing.size, missing)
            }
            valid
        }
        // Server-confirmed entries win on overlap; distinctBy keeps the first occurrence.
        recipeIngredientDao.upsertAll((validEntities + localPendingIngredients).distinctBy { it.ingredientId })

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

    private suspend fun applyPulledBookmark(dto: com.tenmilelabs.chefai.core.data.sync.network.dto.SyncBookmarkPullDto) {
        val userId = UUID.fromString(dto.userId)
        val recipeId = UUID.fromString(dto.recipeId)
        if (dto.deletedAt != null) {
            bookmarkedRecipeDao.softDelete(userId, recipeId, dto.deletedAt)
        } else {
            // bookmarked_recipes.recipeId is a foreign key into recipes.uuid. A bookmark can
            // reference a recipe this device hasn't (or no longer has) locally — a pull for a
            // recipe from another user, or an out-of-order/partial sync — and upserting it
            // unconditionally throws SQLiteConstraintException, aborting the whole pull
            // transaction inside a single bad row. Skip it instead, mirroring how tag/label
            // cross-refs are validated against their local catalog above.
            if (recipeDao.getRecipeById(recipeId) == null) {
                Timber.w("applyPulledBookmark: bookmark references unknown recipe %s, skipping", recipeId)
                return
            }
            bookmarkedRecipeDao.upsert(
                BookmarkedRecipeEntity(
                    userId = userId,
                    recipeId = recipeId,
                    updatedAt = dto.updatedAt,
                    deletedAt = null,
                    syncState = SyncState.SYNCED
                )
            )
        }
    }

    private suspend fun applyPulledMealPlan(dto: SyncMealPlanDto, userId: UUID, serverTimestamp: Long) {
        val planUuid = UUID.fromString(dto.uuid)
        val localPlan = mealPlanDao.getMealPlanById(planUuid)

        // If local has unpushed changes and is newer, skip — push will send it next cycle
        if (localPlan != null && localPlan.syncState == SyncState.PENDING && dto.updatedAt <= localPlan.updatedAt) {
            return
        }

        val entity = dto.toMealPlanEntity(userId)
        mealPlanDao.upsertMealPlan(entity)

        // "Cooked" is local-only state the sync payload knows nothing about, so capture it before
        // the replace below and carry it forward. Keyed by dayIndex rather than day uuid: the
        // server re-issues day rows with its own ids when it regenerates a plan, but the position
        // in the week is what the user marked cooked.
        val previousDays = mealPlanDao.getDaysForMealPlan(planUuid).associateBy { it.dayIndex }

        // Always replace days — server is authoritative (especially after generation)
        mealPlanDao.deleteDaysForMealPlan(planUuid)
        if (dto.days.isNotEmpty()) {
            mealPlanDao.upsertDays(
                dto.days.map { dayDto ->
                    dropUnknownRecipes(dayDto.toMealPlanDayEntity(planUuid))
                        .carryForwardCookedMarks(previousDays)
                }
            )
        }
    }

    /**
     * Nulls out a day's recipe reference(s) that don't resolve on this device.
     *
     * `meal_plan_days.dinnerRecipeId`/`lunchRecipeId` carry no local FK (see
     * [MealPlanDayEntity]), so a pulled day can reference a recipe this device never receives —
     * e.g. a server-generated plan whose candidate query picked something outside what the
     * client's pull is scoped to deliver. Left alone, that produces a permanently unresolvable
     * "Recipe not available" row (`MealPlanMealRow`) that no future sync can fix. Dropping the
     * reference instead makes the slot behave like any other unfilled one — [MealPlanBoard]
     * already skips a day/slot with no recipe assigned.
     */
    private suspend fun dropUnknownRecipes(day: MealPlanDayEntity): MealPlanDayEntity {
        val dinner = day.dinnerRecipeId?.takeIf { recipeDao.getRecipeById(it) != null }
        val lunch = day.lunchRecipeId?.takeIf { recipeDao.getRecipeById(it) != null }
        if (dinner == day.dinnerRecipeId && lunch == day.lunchRecipeId) return day

        Timber.w(
            "applyPulledMealPlan: day %s references unknown recipe(s) — dinner=%s lunch=%s, dropping",
            day.uuid,
            day.dinnerRecipeId.takeIf { dinner == null },
            day.lunchRecipeId.takeIf { lunch == null },
        )
        return day.copy(dinnerRecipeId = dinner, lunchRecipeId = lunch)
    }

    private enum class ApplyResult { UPSERTED, DELETED }
}
