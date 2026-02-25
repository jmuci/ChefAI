package com.tenmilelabs.chefai.core.data.sync

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.room.FakeTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.room.TransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeSyncMetadataDao
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

@ExperimentalCoroutinesApi
class SyncOrchestratorTest {

    // SUT
    private lateinit var syncOrchestrator: SyncOrchestrator

    // Fakes
    private lateinit var recipeDao: FakeRecipeDao
    private lateinit var recipeStepDao: FakeRecipeStepDao
    private lateinit var recipeIngredientDao: FakeRecipeIngredientDao
    private lateinit var recipeTagCrossRefDao: FakeRecipeTagCrossRefDao
    private lateinit var recipeLabelCrossRefDao: FakeRecipeLabelCrossRefDao
    private lateinit var syncMetadataDao: FakeSyncMetadataDao
    private lateinit var syncNetworkDataSource: FakeSyncNetworkDataSource

    /** A pass-through TransactionRunner that just executes the block (no real transaction). */
    private val fakeTransactionRunner = FakeTransactionRunner()

    private val testDispatcher = StandardTestDispatcher()

    // Test data
    private val creatorId = UUID.randomUUID()
    private val recipeId1 = UUID.randomUUID()
    private val recipeId2 = UUID.randomUUID()
    private val stepId1 = UUID.randomUUID()
    private val ingredientId1 = UUID.randomUUID()
    private val tagId1 = UUID.randomUUID()
    private val labelId1 = UUID.randomUUID()

    @Before
    fun setup() {
        recipeDao = FakeRecipeDao()
        recipeStepDao = FakeRecipeStepDao()
        recipeIngredientDao = FakeRecipeIngredientDao()
        recipeTagCrossRefDao = FakeRecipeTagCrossRefDao()
        recipeLabelCrossRefDao = FakeRecipeLabelCrossRefDao()
        syncMetadataDao = FakeSyncMetadataDao()
        syncNetworkDataSource = FakeSyncNetworkDataSource()

        syncOrchestrator = SyncOrchestrator(
            syncNetworkDataSource = syncNetworkDataSource,
            recipeDao = recipeDao,
            recipeStepDao = recipeStepDao,
            recipeIngredientDao = recipeIngredientDao,
            recipeTagCrossRefDao = recipeTagCrossRefDao,
            recipeLabelCrossRefDao = recipeLabelCrossRefDao,
            syncMetadataDao = syncMetadataDao,
            transactionRunner = fakeTransactionRunner,
            ioDispatcher = testDispatcher
        )
    }

    // --- Helper functions ---

    private fun createDirtyRecipe(
        uuid: UUID = UUID.randomUUID(),
        title: String = "Dirty Recipe",
        syncState: SyncState = SyncState.PENDING,
        updatedAt: Long = 1000L
    ): RecipeEntity = RecipeEntity(
        uuid = uuid,
        title = title,
        description = "Description",
        imageUrl = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 10,
        cookTimeMinutes = 20,
        servings = 4,
        creatorId = creatorId,
        recipeExternalUrl = null,
        privacy = RecipePrivacy.PUBLIC,
        updatedAt = updatedAt,
        deletedAt = null,
        syncState = syncState
    )

    private fun createSyncRecipeDto(
        uuid: UUID = UUID.randomUUID(),
        title: String = "Server Recipe",
        updatedAt: Long = 2000L,
        deletedAt: Long? = null
    ): SyncRecipeDto = SyncRecipeDto(
        uuid = uuid.toString(),
        title = title,
        description = "Server description",
        imageUrl = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 10,
        cookTimeMinutes = 20,
        servings = 4,
        creatorId = creatorId.toString(),
        recipeExternalUrl = null,
        privacy = "PUBLIC",
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        steps = listOf(
            SyncRecipeStepDto(uuid = UUID.randomUUID().toString(), orderIndex = 0, instruction = "Step 1")
        ),
        ingredients = listOf(
            SyncRecipeIngredientDto(ingredientId = ingredientId1.toString(), quantity = 100.0, unit = "grams")
        ),
        tagIds = listOf(tagId1.toString()),
        labelIds = listOf(labelId1.toString())
    )

    // ==================== PUSH TESTS ====================

    @Test
    fun `push with no dirty recipes is a no-op`() = runTest(testDispatcher) {
        val result = syncOrchestrator.sync()

        assertThat(result.pushResult.accepted).isEqualTo(0)
        assertThat(result.pushResult.conflicts).isEqualTo(0)
        assertThat(result.pushResult.errors).isEqualTo(0)
        assertThat(syncNetworkDataSource.capturedPushRequests).isEmpty()
    }

    @Test
    fun `push sends dirty recipes to network`() = runTest(testDispatcher) {
        val dirtyRecipe = createDirtyRecipe(uuid = recipeId1)
        recipeDao.upsertRecipe(dirtyRecipe)

        syncOrchestrator.sync()

        assertThat(syncNetworkDataSource.capturedPushRequests).hasSize(1)
        val request = syncNetworkDataSource.capturedPushRequests.first()
        assertThat(request.recipes).hasSize(1)
        assertThat(request.recipes[0].uuid).isEqualTo(recipeId1.toString())
    }

    @Test
    fun `push assembles complete recipe aggregate DTO`() = runTest(testDispatcher) {
        val recipe = createDirtyRecipe(uuid = recipeId1)
        recipeDao.upsertRecipe(recipe)
        recipeStepDao.upsertStep(
            RecipeStepEntity(stepId1, recipeId1, 0, "Mix things", 1000L, null)
        )
        recipeIngredientDao.upsertRecipeIngredient(
            RecipeIngredientEntity(recipeId1, ingredientId1, 200.0, "grams", 1000L)
        )
        recipeTagCrossRefDao.upsertCrossRef(
            RecipeTagCrossRef(recipeId1, tagId1, 1000L, null)
        )
        recipeLabelCrossRefDao.upsertCrossRef(
            RecipeLabelCrossRef(recipeId1, labelId1, 1000L, null)
        )

        syncOrchestrator.sync()

        val dto = syncNetworkDataSource.capturedPushRequests[0].recipes[0]
        assertThat(dto.steps).hasSize(1)
        assertThat(dto.steps[0].instruction).isEqualTo("Mix things")
        assertThat(dto.ingredients).hasSize(1)
        assertThat(dto.ingredients[0].ingredientId).isEqualTo(ingredientId1.toString())
        assertThat(dto.tagIds).containsExactly(tagId1.toString())
        assertThat(dto.labelIds).containsExactly(labelId1.toString())
    }

    @Test
    fun `push accepted recipes become SYNCED`() = runTest(testDispatcher) {
        val recipe = createDirtyRecipe(uuid = recipeId1)
        recipeDao.upsertRecipe(recipe)
        recipeStepDao.upsertStep(
            RecipeStepEntity(stepId1, recipeId1, 0, "Step", 1000L, null)
        )

        val serverTimestamp = 5000L
        syncNetworkDataSource.pushResponses.addLast(
            SyncPushResponse(
                accepted = listOf(AcceptedEntityDto(recipeId1.toString(), serverTimestamp)),
                conflicts = emptyList(),
                errors = emptyList(),
                serverTimestamp = serverTimestamp
            )
        )

        syncOrchestrator.sync()

        val updatedRecipe = recipeDao.getRecipeById(recipeId1)!!
        assertThat(updatedRecipe.syncState).isEqualTo(SyncState.SYNCED)
        assertThat(updatedRecipe.updatedAt).isEqualTo(serverTimestamp)
    }

    @Test
    fun `push conflicts replace local with server version`() = runTest(testDispatcher) {
        val localRecipe = createDirtyRecipe(uuid = recipeId1, title = "Local Title", updatedAt = 1000L)
        recipeDao.upsertRecipe(localRecipe)

        val serverVersion = createSyncRecipeDto(uuid = recipeId1, title = "Server Title", updatedAt = 3000L)
        syncNetworkDataSource.pushResponses.addLast(
            SyncPushResponse(
                accepted = emptyList(),
                conflicts = listOf(
                    ConflictEntityDto(
                        uuid = recipeId1.toString(),
                        reason = "stale_version",
                        serverVersion = serverVersion
                    )
                ),
                errors = emptyList(),
                serverTimestamp = 3000L
            )
        )

        val result = syncOrchestrator.sync()

        assertThat(result.pushResult.conflicts).isEqualTo(1)
        val updatedRecipe = recipeDao.getRecipeById(recipeId1)!!
        assertThat(updatedRecipe.title).isEqualTo("Server Title")
        assertThat(updatedRecipe.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `push errors keep recipes as PENDING`() = runTest(testDispatcher) {
        val recipe = createDirtyRecipe(uuid = recipeId1)
        recipeDao.upsertRecipe(recipe)

        syncNetworkDataSource.pushResponses.addLast(
            SyncPushResponse(
                accepted = emptyList(),
                conflicts = emptyList(),
                errors = listOf(SyncErrorDto(recipeId1.toString(), "server_error", "Internal error")),
                serverTimestamp = 5000L
            )
        )

        val result = syncOrchestrator.sync()

        assertThat(result.pushResult.errors).isEqualTo(1)
        val recipe2 = recipeDao.getRecipeById(recipeId1)!!
        assertThat(recipe2.syncState).isEqualTo(SyncState.PENDING)
    }

    @Test
    fun `push sends DELETED recipes`() = runTest(testDispatcher) {
        val deletedRecipe = createDirtyRecipe(uuid = recipeId1, syncState = SyncState.DELETED)
        recipeDao.upsertRecipe(deletedRecipe)

        syncOrchestrator.sync()

        assertThat(syncNetworkDataSource.capturedPushRequests).hasSize(1)
        assertThat(syncNetworkDataSource.capturedPushRequests[0].recipes).hasSize(1)
    }

    @Test
    fun `push batches recipes in groups of 50`() = runTest(testDispatcher) {
        // Create 75 dirty recipes -> should produce 2 batches (50 + 25)
        repeat(75) { i ->
            recipeDao.upsertRecipe(createDirtyRecipe(title = "Recipe $i"))
        }

        syncOrchestrator.sync()

        assertThat(syncNetworkDataSource.capturedPushRequests).hasSize(2)
        assertThat(syncNetworkDataSource.capturedPushRequests[0].recipes).hasSize(50)
        assertThat(syncNetworkDataSource.capturedPushRequests[1].recipes).hasSize(25)
    }

    // ==================== PULL TESTS ====================

    @Test
    fun `pull with no data on server is a no-op`() = runTest(testDispatcher) {
        val result = syncOrchestrator.sync()

        assertThat(result.pullResult.upserted).isEqualTo(0)
        assertThat(result.pullResult.deleted).isEqualTo(0)
        assertThat(result.pullResult.pages).isEqualTo(1)
    }

    @Test
    fun `pull inserts new recipe from server`() = runTest(testDispatcher) {
        val serverRecipe = createSyncRecipeDto(uuid = recipeId1, title = "From Server")
        syncNetworkDataSource.pullResponses.addLast(
            SyncPullResponse(
                recipes = listOf(serverRecipe),
                serverTimestamp = 5000L,
                hasMore = false
            )
        )

        val result = syncOrchestrator.sync()

        assertThat(result.pullResult.upserted).isEqualTo(1)
        val localRecipe = recipeDao.getRecipeById(recipeId1)!!
        assertThat(localRecipe.title).isEqualTo("From Server")
        assertThat(localRecipe.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `pull updates existing SYNCED recipe with server version`() = runTest(testDispatcher) {
        val existingRecipe = createDirtyRecipe(uuid = recipeId1, title = "Old Title", syncState = SyncState.SYNCED)
        recipeDao.upsertRecipe(existingRecipe)

        val serverRecipe = createSyncRecipeDto(uuid = recipeId1, title = "Updated Title", updatedAt = 5000L)
        syncNetworkDataSource.pullResponses.addLast(
            SyncPullResponse(recipes = listOf(serverRecipe), serverTimestamp = 5000L, hasMore = false)
        )

        syncOrchestrator.sync()

        val updatedRecipe = recipeDao.getRecipeById(recipeId1)!!
        assertThat(updatedRecipe.title).isEqualTo("Updated Title")
    }

    @Test
    fun `pull server wins when local is PENDING and server is newer`() = runTest(testDispatcher) {
        val localRecipe = createDirtyRecipe(uuid = recipeId1, title = "Local Edit", updatedAt = 1000L)
        recipeDao.upsertRecipe(localRecipe)

        val serverRecipe = createSyncRecipeDto(uuid = recipeId1, title = "Server Edit", updatedAt = 2000L)
        syncNetworkDataSource.pullResponses.addLast(
            SyncPullResponse(recipes = listOf(serverRecipe), serverTimestamp = 3000L, hasMore = false)
        )

        syncOrchestrator.sync()

        val updatedRecipe = recipeDao.getRecipeById(recipeId1)!!
        assertThat(updatedRecipe.title).isEqualTo("Server Edit")
        assertThat(updatedRecipe.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `pull local wins when local is PENDING and newer than server`() = runTest(testDispatcher) {
        val localRecipe = createDirtyRecipe(uuid = recipeId1, title = "Local Newer", updatedAt = 5000L)
        recipeDao.upsertRecipe(localRecipe)

        // Push phase will try to send this PENDING recipe. Return an error so it stays PENDING.
        syncNetworkDataSource.pushResponses.addLast(
            SyncPushResponse(
                accepted = emptyList(),
                conflicts = emptyList(),
                errors = listOf(SyncErrorDto(recipeId1.toString(), "unavailable", "Retry later")),
                serverTimestamp = 4000L
            )
        )

        val serverRecipe = createSyncRecipeDto(uuid = recipeId1, title = "Server Older", updatedAt = 3000L)
        syncNetworkDataSource.pullResponses.addLast(
            SyncPullResponse(recipes = listOf(serverRecipe), serverTimestamp = 4000L, hasMore = false)
        )

        syncOrchestrator.sync()

        val keptRecipe = recipeDao.getRecipeById(recipeId1)!!
        assertThat(keptRecipe.title).isEqualTo("Local Newer")
        assertThat(keptRecipe.syncState).isEqualTo(SyncState.PENDING)
    }

    @Test
    fun `pull soft-deletes recipe when server sends deletedAt`() = runTest(testDispatcher) {
        val existingRecipe = createDirtyRecipe(uuid = recipeId1, syncState = SyncState.SYNCED)
        recipeDao.upsertRecipe(existingRecipe)

        val serverRecipe = createSyncRecipeDto(uuid = recipeId1, deletedAt = 5000L)
        syncNetworkDataSource.pullResponses.addLast(
            SyncPullResponse(recipes = listOf(serverRecipe), serverTimestamp = 6000L, hasMore = false)
        )

        val result = syncOrchestrator.sync()

        assertThat(result.pullResult.deleted).isEqualTo(1)
        val deletedRecipe = recipeDao.getRecipeById(recipeId1)!!
        assertThat(deletedRecipe.deletedAt).isEqualTo(5000L)
        assertThat(deletedRecipe.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `pull handles pagination with hasMore`() = runTest(testDispatcher) {
        val serverRecipe1 = createSyncRecipeDto(uuid = recipeId1, title = "Page 1 Recipe", updatedAt = 1000L)
        val serverRecipe2 = createSyncRecipeDto(uuid = recipeId2, title = "Page 2 Recipe", updatedAt = 2000L)

        syncNetworkDataSource.pullResponses.addLast(
            SyncPullResponse(recipes = listOf(serverRecipe1), serverTimestamp = 1500L, hasMore = true)
        )
        syncNetworkDataSource.pullResponses.addLast(
            SyncPullResponse(recipes = listOf(serverRecipe2), serverTimestamp = 2500L, hasMore = false)
        )

        val result = syncOrchestrator.sync()

        assertThat(result.pullResult.upserted).isEqualTo(2)
        assertThat(result.pullResult.pages).isEqualTo(2)
        assertThat(recipeDao.getRecipeById(recipeId1)).isNotNull()
        assertThat(recipeDao.getRecipeById(recipeId2)).isNotNull()
    }

    @Test
    fun `pull updates sync checkpoint`() = runTest(testDispatcher) {
        syncNetworkDataSource.pullResponses.addLast(
            SyncPullResponse(recipes = emptyList(), serverTimestamp = 9999L, hasMore = false)
        )

        syncOrchestrator.sync()

        val checkpoint = syncMetadataDao.getLastSyncedAt(SyncOrchestrator.ENTITY_TYPE_RECIPES)
        assertThat(checkpoint).isEqualTo(9999L)
    }

    @Test
    fun `pull uses last checkpoint as since parameter`() = runTest(testDispatcher) {
        syncMetadataDao.upsert(
            com.tenmilelabs.chefai.core.data.local.room.SyncMetadataEntity("recipes", 5000L)
        )

        syncOrchestrator.sync()

        assertThat(syncNetworkDataSource.capturedPullCalls).hasSize(1)
        val (since, _) = syncNetworkDataSource.capturedPullCalls.first()
        assertThat(since).isEqualTo(5000L)
    }

    @Test
    fun `pull replaces children during upsert aggregate`() = runTest(testDispatcher) {
        val existingRecipe = createDirtyRecipe(uuid = recipeId1, syncState = SyncState.SYNCED)
        recipeDao.upsertRecipe(existingRecipe)
        recipeStepDao.upsertStep(
            RecipeStepEntity(UUID.randomUUID(), recipeId1, 0, "Old step", 1000L, null)
        )

        val serverRecipe = createSyncRecipeDto(uuid = recipeId1, updatedAt = 5000L)
        syncNetworkDataSource.pullResponses.addLast(
            SyncPullResponse(recipes = listOf(serverRecipe), serverTimestamp = 6000L, hasMore = false)
        )

        syncOrchestrator.sync()

        val steps = recipeStepDao.getStepsForRecipe(recipeId1)
        assertThat(steps).hasSize(1)
        assertThat(steps[0].instruction).isEqualTo("Step 1")
        assertThat(steps[0].syncState).isEqualTo(SyncState.SYNCED)
    }

    // ==================== INTEGRATION TESTS ====================

    @Test
    fun `full sync cycle push then pull`() = runTest(testDispatcher) {
        val localRecipe = createDirtyRecipe(uuid = recipeId1, title = "Local Recipe")
        recipeDao.upsertRecipe(localRecipe)

        val newServerRecipe = createSyncRecipeDto(uuid = recipeId2, title = "Server New Recipe")

        syncNetworkDataSource.pushResponses.addLast(
            SyncPushResponse(
                accepted = listOf(AcceptedEntityDto(recipeId1.toString(), 5000L)),
                conflicts = emptyList(),
                errors = emptyList(),
                serverTimestamp = 5000L
            )
        )
        syncNetworkDataSource.pullResponses.addLast(
            SyncPullResponse(recipes = listOf(newServerRecipe), serverTimestamp = 6000L, hasMore = false)
        )

        val result = syncOrchestrator.sync()

        assertThat(result.pushResult.accepted).isEqualTo(1)
        assertThat(result.pullResult.upserted).isEqualTo(1)

        val syncedLocal = recipeDao.getRecipeById(recipeId1)!!
        assertThat(syncedLocal.syncState).isEqualTo(SyncState.SYNCED)

        val pulledRecipe = recipeDao.getRecipeById(recipeId2)!!
        assertThat(pulledRecipe.title).isEqualTo("Server New Recipe")
        assertThat(pulledRecipe.syncState).isEqualTo(SyncState.SYNCED)
    }
}
