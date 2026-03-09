package com.tenmilelabs.chefai.core.data.sync

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.FakeTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeAllergenDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeLabelDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeSourceClassificationDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeSyncMetadataDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeTagDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeUserDao
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.network.FakeSyncNetworkDataSource
import com.tenmilelabs.chefai.core.data.sync.network.dto.AcceptedEntityDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.ConflictEntityDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncErrorDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPullResponse
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPushResponse
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeIngredientDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeStepDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * End-to-end sync scenario tests that exercise the full [SyncOrchestrator]
 * through realistic multi-step workflows using fakes (no Android framework).
 *
 * These complement the unit-level tests in [SyncOrchestratorTest] by focusing
 * on complete user scenarios rather than individual push/pull operations.
 */
@ExperimentalCoroutinesApi
class SyncE2ETest {

    // SUT
    private lateinit var syncOrchestrator: SyncOrchestrator

    // Fakes
    private lateinit var recipeDao: FakeRecipeDao
    private lateinit var recipeStepDao: FakeRecipeStepDao
    private lateinit var recipeIngredientDao: FakeRecipeIngredientDao
    private lateinit var allergenDao: FakeAllergenDao
    private lateinit var sourceClassificationDao: FakeSourceClassificationDao
    private lateinit var ingredientDao: FakeIngredientDao
    private lateinit var recipeTagCrossRefDao: FakeRecipeTagCrossRefDao
    private lateinit var labelDao: FakeLabelDao
    private lateinit var tagDao: FakeTagDao
    private lateinit var userDao: FakeUserDao
    private lateinit var recipeLabelCrossRefDao: FakeRecipeLabelCrossRefDao
    private lateinit var syncMetadataDao: FakeSyncMetadataDao
    private lateinit var syncNetworkDataSource: FakeSyncNetworkDataSource
    private val fakeTransactionRunner = FakeTransactionRunner()
    private val testDispatcher = StandardTestDispatcher()

    // Stable test IDs
    private val creatorId = UuidV7Generator.newId()
    private val ingredientId1 = UuidV7Generator.newId()
    private val ingredientId2 = UuidV7Generator.newId()
    private val tagId1 = UuidV7Generator.newId()
    private val labelId1 = UuidV7Generator.newId()

    @Before
    fun setup() {
        recipeDao = FakeRecipeDao()
        recipeStepDao = FakeRecipeStepDao()
        recipeIngredientDao = FakeRecipeIngredientDao()
        allergenDao = FakeAllergenDao()
        sourceClassificationDao = FakeSourceClassificationDao()
        ingredientDao = FakeIngredientDao()
        recipeTagCrossRefDao = FakeRecipeTagCrossRefDao()
        recipeLabelCrossRefDao = FakeRecipeLabelCrossRefDao()
        syncMetadataDao = FakeSyncMetadataDao()
        syncNetworkDataSource = FakeSyncNetworkDataSource()
        labelDao = FakeLabelDao()
        tagDao = FakeTagDao()
        userDao = FakeUserDao()

        // Seed server-known ingredients so push filtering passes for tests that use them
        runBlocking {
            ingredientDao.upsertAll(
                listOf(
                    IngredientEntity(ingredientId1, "Ingredient 1", null, null, 0L, null, SyncState.SYNCED),
                    IngredientEntity(ingredientId2, "Ingredient 2", null, null, 0L, null, SyncState.SYNCED),
                )
            )
        }

        syncOrchestrator = SyncOrchestrator(
            syncNetworkDataSource = syncNetworkDataSource,
            recipeDao = recipeDao,
            recipeStepDao = recipeStepDao,
            allergenDao = allergenDao,
            sourceClassificationDao = sourceClassificationDao,
            ingredientDao = ingredientDao,
            tagDao = tagDao,
            labelDao = labelDao,
            userDao = userDao,
            recipeIngredientDao = recipeIngredientDao,
            recipeTagCrossRefDao = recipeTagCrossRefDao,
            recipeLabelCrossRefDao = recipeLabelCrossRefDao,
            syncMetadataDao = syncMetadataDao,
            transactionRunner = fakeTransactionRunner,
            ioDispatcher = testDispatcher
        )
    }

    // --- Helpers ---

    private fun createLocalRecipe(
        uuid: UUID = UuidV7Generator.newId(),
        title: String = "Local Recipe",
        syncState: SyncState = SyncState.PENDING,
        updatedAt: Long = 1000L,
        deletedAt: Long? = null
    ): RecipeEntity = RecipeEntity(
        uuid = uuid,
        title = title,
        description = "A test recipe",
        imageUrl = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 15,
        cookTimeMinutes = 30,
        servings = 4,
        creatorId = creatorId,
        recipeExternalUrl = null,
        privacy = RecipePrivacy.PUBLIC,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        syncState = syncState
    )

    private fun createServerRecipeDto(
        uuid: UUID = UuidV7Generator.newId(),
        title: String = "Server Recipe",
        updatedAt: Long = 2000L,
        deletedAt: Long? = null,
        steps: List<SyncRecipeStepDto> = emptyList(),
        ingredients: List<SyncRecipeIngredientDto> = emptyList(),
        tagIds: List<String> = emptyList(),
        labelIds: List<String> = emptyList()
    ): SyncRecipeDto = SyncRecipeDto(
        uuid = uuid.toString(),
        title = title,
        description = "Server description",
        imageUrl = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 15,
        cookTimeMinutes = 30,
        servings = 4,
        creatorId = creatorId.toString(),
        recipeExternalUrl = null,
        privacy = "PUBLIC",
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        steps = steps,
        ingredients = ingredients,
        tagIds = tagIds,
        labelIds = labelIds
    )

    // ==========================================================================================
    // Task 3: Offline Create → Sync → Verify
    // ==========================================================================================

    @Test
    fun `offline create then sync pushes recipe and marks as synced`() = runTest(testDispatcher) {
        // GIVEN: user creates a recipe offline → PENDING state
        val recipeId = UuidV7Generator.newId()
        recipeDao.upsertRecipe(createLocalRecipe(uuid = recipeId, title = "Homemade Pasta"))

        // Server accepts the push
        val serverTimestamp = 5000L
        syncNetworkDataSource.pushResponses.addLast(
            SyncPushResponse(
                accepted = listOf(AcceptedEntityDto(recipeId.toString(), serverTimestamp)),
                conflicts = emptyList(),
                errors = emptyList(),
                serverTimestamp = serverTimestamp
            )
        )

        // WHEN: sync fires (e.g., app comes to foreground)
        val result = syncOrchestrator.sync()

        // THEN: recipe was pushed and is now SYNCED locally
        assertThat(result.pushResult.accepted).isEqualTo(1)
        assertThat(syncNetworkDataSource.capturedPushRequests).hasSize(1)
        assertThat(syncNetworkDataSource.capturedPushRequests[0].recipes[0].title).isEqualTo("Homemade Pasta")

        val syncedRecipe = recipeDao.getRecipeById(recipeId)!!
        assertThat(syncedRecipe.syncState).isEqualTo(SyncState.SYNCED)
        assertThat(syncedRecipe.updatedAt).isEqualTo(serverTimestamp)
    }

    @Test
    fun `offline create with steps and ingredients syncs complete aggregate`() = runTest(testDispatcher) {
        // GIVEN: recipe with steps + ingredients created offline
        val recipeId = UuidV7Generator.newId()
        val stepId1 = UuidV7Generator.newId()
        val stepId2 = UuidV7Generator.newId()

        recipeDao.upsertRecipe(createLocalRecipe(uuid = recipeId, title = "Pancakes"))
        recipeStepDao.upsertStep(RecipeStepEntity(stepId1, recipeId, 0, "Mix dry ingredients", 1000L, null))
        recipeStepDao.upsertStep(RecipeStepEntity(stepId2, recipeId, 1, "Add wet ingredients and stir", 1000L, null))
        recipeIngredientDao.upsertRecipeIngredient(
            RecipeIngredientEntity(recipeId, ingredientId1, 200.0, "grams", 1000L)
        )
        recipeIngredientDao.upsertRecipeIngredient(
            RecipeIngredientEntity(recipeId, ingredientId2, 2.0, "cups", 1000L)
        )

        // Server accepts
        val serverTimestamp = 5000L
        syncNetworkDataSource.pushResponses.addLast(
            SyncPushResponse(
                accepted = listOf(AcceptedEntityDto(recipeId.toString(), serverTimestamp)),
                conflicts = emptyList(),
                errors = emptyList(),
                serverTimestamp = serverTimestamp
            )
        )

        // WHEN
        val result = syncOrchestrator.sync()

        // THEN: the full aggregate was pushed
        assertThat(result.pushResult.accepted).isEqualTo(1)
        val pushedDto = syncNetworkDataSource.capturedPushRequests[0].recipes[0]
        assertThat(pushedDto.steps).hasSize(2)
        assertThat(pushedDto.steps[0].instruction).isEqualTo("Mix dry ingredients")
        assertThat(pushedDto.steps[1].instruction).isEqualTo("Add wet ingredients and stir")
        assertThat(pushedDto.ingredients).hasSize(2)

        // All children are marked SYNCED
        val syncedRecipe = recipeDao.getRecipeById(recipeId)!!
        assertThat(syncedRecipe.syncState).isEqualTo(SyncState.SYNCED)

        val syncedSteps = recipeStepDao.getStepsForRecipe(recipeId)
        assertThat(syncedSteps).hasSize(2)
        syncedSteps.forEach { step ->
            assertThat(step.syncState).isEqualTo(SyncState.SYNCED)
        }

        val syncedIngredients = recipeIngredientDao.getIngredientsForRecipe(recipeId)
        assertThat(syncedIngredients).hasSize(2)
        syncedIngredients.forEach { ingredient ->
            assertThat(ingredient.syncState).isEqualTo(SyncState.SYNCED)
        }
    }

    @Test
    fun `delete offline then sync pushes soft delete`() = runTest(testDispatcher) {
        // GIVEN: recipe existed and user deletes it offline
        val recipeId = UuidV7Generator.newId()
        val deletedRecipe = createLocalRecipe(
            uuid = recipeId,
            title = "To Be Deleted",
            syncState = SyncState.DELETED,
            deletedAt = 3000L
        )
        recipeDao.upsertRecipe(deletedRecipe)

        // Server accepts the deletion
        val serverTimestamp = 5000L
        syncNetworkDataSource.pushResponses.addLast(
            SyncPushResponse(
                accepted = listOf(AcceptedEntityDto(recipeId.toString(), serverTimestamp)),
                conflicts = emptyList(),
                errors = emptyList(),
                serverTimestamp = serverTimestamp
            )
        )

        // WHEN
        val result = syncOrchestrator.sync()

        // THEN: deleted recipe was pushed
        assertThat(result.pushResult.accepted).isEqualTo(1)
        val pushedDto = syncNetworkDataSource.capturedPushRequests[0].recipes[0]
        assertThat(pushedDto.uuid).isEqualTo(recipeId.toString())

        // Local state becomes SYNCED (soft-deleted but synced)
        val localRecipe = recipeDao.getRecipeById(recipeId)!!
        assertThat(localRecipe.syncState).isEqualTo(SyncState.SYNCED)
    }

    // ==========================================================================================
    // Task 4: Multi-Device Conflict → Last-Writer-Wins
    // ==========================================================================================

    @Test
    fun `push conflict - server newer replaces local`() = runTest(testDispatcher) {
        // GIVEN: local recipe with updatedAt=1000, PENDING
        val recipeId = UuidV7Generator.newId()
        recipeDao.upsertRecipe(
            createLocalRecipe(uuid = recipeId, title = "My Local Version", updatedAt = 1000L)
        )

        // Server responds with a conflict because another device updated it
        val serverVersion = createServerRecipeDto(
            uuid = recipeId,
            title = "Other Device Version",
            updatedAt = 2000L,
            steps = listOf(SyncRecipeStepDto(UuidV7Generator.newId().toString(), 0, "Server step"))
        )
        syncNetworkDataSource.pushResponses.addLast(
            SyncPushResponse(
                accepted = emptyList(),
                conflicts = listOf(
                    ConflictEntityDto(
                        uuid = recipeId.toString(),
                        reason = "stale_version",
                        serverVersion = serverVersion
                    )
                ),
                errors = emptyList(),
                serverTimestamp = 2000L
            )
        )

        // WHEN
        val result = syncOrchestrator.sync()

        // THEN: server version replaces local, state becomes SYNCED
        assertThat(result.pushResult.conflicts).isEqualTo(1)
        val localRecipe = recipeDao.getRecipeById(recipeId)!!
        assertThat(localRecipe.title).isEqualTo("Other Device Version")
        assertThat(localRecipe.syncState).isEqualTo(SyncState.SYNCED)
        assertThat(localRecipe.updatedAt).isEqualTo(2000L)

        // Children were replaced with server version
        val steps = recipeStepDao.getStepsForRecipe(recipeId)
        assertThat(steps).hasSize(1)
        assertThat(steps[0].instruction).isEqualTo("Server step")
    }

    @Test
    fun `pull conflict - local PENDING newer preserved`() = runTest(testDispatcher) {
        // GIVEN: local recipe is newer (updatedAt=5000) and PENDING
        val recipeId = UuidV7Generator.newId()
        recipeDao.upsertRecipe(
            createLocalRecipe(uuid = recipeId, title = "Local Newer Edit", updatedAt = 5000L)
        )

        // Push phase: server returns error so recipe stays PENDING
        syncNetworkDataSource.pushResponses.addLast(
            SyncPushResponse(
                accepted = emptyList(),
                conflicts = emptyList(),
                errors = listOf(SyncErrorDto(recipeId.toString(), "unavailable", "Retry later")),
                serverTimestamp = 4000L
            )
        )

        // Pull phase: server sends an older version (updatedAt=3000)
        val olderServerVersion = createServerRecipeDto(
            uuid = recipeId,
            title = "Server Stale Version",
            updatedAt = 3000L
        )
        syncNetworkDataSource.pullResponses.addLast(
            SyncPullResponse(
                recipes = listOf(olderServerVersion),
                serverTimestamp = 4000L,
                hasMore = false
            )
        )

        // WHEN
        syncOrchestrator.sync()

        // THEN: local version preserved — it's newer
        val keptRecipe = recipeDao.getRecipeById(recipeId)!!
        assertThat(keptRecipe.title).isEqualTo("Local Newer Edit")
        assertThat(keptRecipe.syncState).isEqualTo(SyncState.PENDING)
        assertThat(keptRecipe.updatedAt).isEqualTo(5000L)
    }

    @Test
    fun `full cycle - push accepted then pull brings other device changes`() = runTest(testDispatcher) {
        // GIVEN: a local PENDING recipe
        val localRecipeId = UuidV7Generator.newId()
        recipeDao.upsertRecipe(
            createLocalRecipe(uuid = localRecipeId, title = "My Recipe", updatedAt = 1000L)
        )

        // Push phase: server accepts our recipe
        val pushTimestamp = 5000L
        syncNetworkDataSource.pushResponses.addLast(
            SyncPushResponse(
                accepted = listOf(AcceptedEntityDto(localRecipeId.toString(), pushTimestamp)),
                conflicts = emptyList(),
                errors = emptyList(),
                serverTimestamp = pushTimestamp
            )
        )

        // Pull phase: server returns a different recipe from another device
        val otherDeviceRecipeId = UuidV7Generator.newId()
        val otherDeviceRecipe = createServerRecipeDto(
            uuid = otherDeviceRecipeId,
            title = "Recipe From Other Device",
            updatedAt = 4500L,
            steps = listOf(
                SyncRecipeStepDto(UuidV7Generator.newId().toString(), 0, "Step from other device")
            )
        )
        syncNetworkDataSource.pullResponses.addLast(
            SyncPullResponse(
                recipes = listOf(otherDeviceRecipe),
                serverTimestamp = 6000L,
                hasMore = false
            )
        )

        // WHEN
        val result = syncOrchestrator.sync()

        // THEN: our recipe was pushed and accepted
        assertThat(result.pushResult.accepted).isEqualTo(1)
        val ourRecipe = recipeDao.getRecipeById(localRecipeId)!!
        assertThat(ourRecipe.syncState).isEqualTo(SyncState.SYNCED)
        assertThat(ourRecipe.title).isEqualTo("My Recipe")

        // AND: the other device's recipe was pulled in
        assertThat(result.pullResult.upserted).isEqualTo(1)
        val pulledRecipe = recipeDao.getRecipeById(otherDeviceRecipeId)!!
        assertThat(pulledRecipe.title).isEqualTo("Recipe From Other Device")
        assertThat(pulledRecipe.syncState).isEqualTo(SyncState.SYNCED)

        // Both recipes exist locally
        assertThat(recipeDao.getRecipeById(localRecipeId)).isNotNull()
        assertThat(recipeDao.getRecipeById(otherDeviceRecipeId)).isNotNull()
    }
}
