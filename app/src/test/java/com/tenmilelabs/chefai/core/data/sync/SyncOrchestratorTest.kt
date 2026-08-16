package com.tenmilelabs.chefai.core.data.sync

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.FakeTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeAllergenDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeBookmarkedRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeMealPlanDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeLabelDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeImageStateDao
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
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncBookmarkPullDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncCreatorDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPullResponse
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPushResponse
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeIngredientDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeStepDto
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.testutil.createTestSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
    private lateinit var allergenDao: FakeAllergenDao
    private lateinit var sourceClassificationDao: FakeSourceClassificationDao
    private lateinit var ingredientDao: FakeIngredientDao
    private lateinit var labelDao: FakeLabelDao
    private lateinit var tagDao: FakeTagDao
    private lateinit var recipeIngredientDao: FakeRecipeIngredientDao
    private lateinit var recipeTagCrossRefDao: FakeRecipeTagCrossRefDao
    private lateinit var recipeLabelCrossRefDao: FakeRecipeLabelCrossRefDao
    private lateinit var bookmarkedRecipeDao: FakeBookmarkedRecipeDao
    private lateinit var sessionManager: SessionManager
    private lateinit var userDao: FakeUserDao
    private lateinit var syncMetadataDao: FakeSyncMetadataDao
    private lateinit var syncNetworkDataSource: FakeSyncNetworkDataSource

    /** A pass-through TransactionRunner that just executes the block (no real transaction). */
    private val fakeTransactionRunner = FakeTransactionRunner()

    private val testDispatcher = StandardTestDispatcher()

    // Test data
    private val creatorId = UuidV7Generator.newId()
    private val recipeId1 = UuidV7Generator.newId()
    private val recipeId2 = UuidV7Generator.newId()
    private val stepId1 = UuidV7Generator.newId()
    private val ingredientId1 = UuidV7Generator.newId()
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
        labelDao = FakeLabelDao()
        tagDao = FakeTagDao()
        userDao = FakeUserDao()
        recipeTagCrossRefDao = FakeRecipeTagCrossRefDao()
        recipeLabelCrossRefDao = FakeRecipeLabelCrossRefDao()
        bookmarkedRecipeDao = FakeBookmarkedRecipeDao()
        sessionManager = createTestSessionManager(CoroutineScope(testDispatcher))
        syncMetadataDao = FakeSyncMetadataDao()
        syncNetworkDataSource = FakeSyncNetworkDataSource()

        syncOrchestrator = SyncOrchestrator(
            syncNetworkDataSource = syncNetworkDataSource,
            recipeDao = recipeDao,
            recipeImageStateDao = FakeRecipeImageStateDao(),
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
            bookmarkedRecipeDao = bookmarkedRecipeDao,
            mealPlanDao = FakeMealPlanDao(),
            sessionManager = sessionManager,
            syncMetadataDao = syncMetadataDao,
            transactionRunner = fakeTransactionRunner,
            ioDispatcher = testDispatcher
        )
    }

    // --- Helper functions ---

    private fun createDirtyRecipe(
        uuid: UUID = UuidV7Generator.newId(),
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
        uuid: UUID = UuidV7Generator.newId(),
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
            SyncRecipeStepDto(uuid = UuidV7Generator.newId().toString(), orderIndex = 0, instruction = "Step 1")
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
        // Ingredient must be SYNCED (i.e. pulled from server) to pass the push filter
        ingredientDao.upsertIngredient(
            IngredientEntity(
                uuid = ingredientId1,
                displayName = "Test Ingredient",
                allergenId = null,
                sourcePrimaryId = null,
                updatedAt = 1000L,
                deletedAt = null,
                syncState = SyncState.SYNCED
            )
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
    fun `pull upserts creators from response before recipes`() = runTest(testDispatcher) {
        val unknownCreatorId = UUID.randomUUID()
        val serverRecipe = createSyncRecipeDto(uuid = recipeId1, title = "From Server").copy(
            creatorId = unknownCreatorId.toString()
        )
        syncNetworkDataSource.pullResponses.addLast(
            SyncPullResponse(
                recipes = listOf(serverRecipe),
                serverTimestamp = 5000L,
                hasMore = false,
                creators = listOf(
                    SyncCreatorDto(
                        uuid = unknownCreatorId.toString(),
                        displayName = "Chef Jane",
                        email = "jane@example.com",
                        avatarUrl = "https://example.com/avatar.jpg",
                        updatedAt = 5000L,
                        deletedAt = null
                    )
                )
            )
        )

        syncOrchestrator.sync()

        val creator = userDao.getUserById(unknownCreatorId)
        assertThat(creator).isNotNull()
        val existingCreator = creator!!
        assertThat(existingCreator.displayName).isEqualTo("Chef Jane")
        assertThat(existingCreator.email).isEqualTo("jane@example.com")
        assertThat(existingCreator.avatarUrl).isEqualTo("https://example.com/avatar.jpg")
        assertThat(existingCreator.syncState).isEqualTo(SyncState.SYNCED)
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
    fun `pull preserves the device-local cached image path, which the server never sends`() = runTest(testDispatcher) {
        val existingRecipe = createDirtyRecipe(uuid = recipeId1, syncState = SyncState.SYNCED)
            .copy(localImagePath = "/data/user/0/com.tenmilelabs.chefai/files/recipe_images/$recipeId1")
        recipeDao.upsertRecipe(existingRecipe)

        // The DTO has no localImagePath field at all — this is what the server sends back for
        // every recipe, cached or not.
        val serverRecipe = createSyncRecipeDto(uuid = recipeId1, title = "Updated Title", updatedAt = 5000L)
        syncNetworkDataSource.pullResponses.addLast(
            SyncPullResponse(recipes = listOf(serverRecipe), serverTimestamp = 5000L, hasMore = false)
        )

        syncOrchestrator.sync()

        val updatedRecipe = recipeDao.getRecipeById(recipeId1)!!
        assertThat(updatedRecipe.title).isEqualTo("Updated Title")
        assertThat(updatedRecipe.localImagePath)
            .isEqualTo("/data/user/0/com.tenmilelabs.chefai/files/recipe_images/$recipeId1")
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
    fun `pull stops instead of looping forever when the server's cursor never advances`() = runTest(testDispatcher) {
        syncMetadataDao.upsert(
            com.tenmilelabs.chefai.core.data.local.room.SyncMetadataEntity("recipes", 5000L)
        )

        // hasMore=true on every page, but serverTimestamp never exceeds the starting checkpoint —
        // a misbehaving server that can never be paginated past. Queue several identical pages so
        // the test would fail loudly (by draining the queue) if pull() doesn't stop on its own.
        repeat(5) {
            syncNetworkDataSource.pullResponses.addLast(
                SyncPullResponse(recipes = listOf(createSyncRecipeDto(uuid = recipeId1)), serverTimestamp = 5000L, hasMore = true)
            )
        }

        val result = syncOrchestrator.sync()

        assertThat(syncNetworkDataSource.capturedPullCalls).hasSize(1)
        assertThat(result.pullResult.pages).isEqualTo(1)
        // The one page we did receive is still applied — stopping early isn't data loss.
        assertThat(recipeDao.getRecipeById(recipeId1)).isNotNull()
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
            RecipeStepEntity(UuidV7Generator.newId(), recipeId1, 0, "Old step", 1000L, null)
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

    @Test
    fun `pull upserts a bookmark for a recipe that exists locally`() = runTest(testDispatcher) {
        recipeDao.upsertRecipe(createDirtyRecipe(uuid = recipeId1, syncState = SyncState.SYNCED))
        val userId = UuidV7Generator.newId()
        syncNetworkDataSource.pullResponses.addLast(
            SyncPullResponse(
                recipes = emptyList(),
                serverTimestamp = 5000L,
                hasMore = false,
                bookmarkedRecipes = listOf(
                    SyncBookmarkPullDto(
                        userId = userId.toString(),
                        recipeId = recipeId1.toString(),
                        updatedAt = 5000L,
                        deletedAt = null,
                    )
                ),
            )
        )

        syncOrchestrator.sync()

        val bookmarked = bookmarkedRecipeDao.observeBookmarkedRecipeIds(userId).first()
        assertThat(bookmarked).containsExactly(recipeId1)
    }

    @Test
    fun `pull skips a bookmark referencing a recipe not known locally rather than failing the sync`() =
        runTest(testDispatcher) {
            // The real Room schema has a foreign key from bookmarked_recipes.recipeId to
            // recipes.uuid — a bookmark for a recipe this device doesn't have (another user's
            // recipe, or an out-of-order/partial pull) must not abort the whole pull transaction.
            val userId = UuidV7Generator.newId()
            val unknownRecipeId = UuidV7Generator.newId()
            syncNetworkDataSource.pullResponses.addLast(
                SyncPullResponse(
                    recipes = emptyList(),
                    serverTimestamp = 5000L,
                    hasMore = false,
                    bookmarkedRecipes = listOf(
                        SyncBookmarkPullDto(
                            userId = userId.toString(),
                            recipeId = unknownRecipeId.toString(),
                            updatedAt = 5000L,
                            deletedAt = null,
                        )
                    ),
                )
            )

            val result = syncOrchestrator.sync()

            assertThat(bookmarkedRecipeDao.observeBookmarkedRecipeIds(userId).first()).isEmpty()
            // The checkpoint still advances — one bad bookmark doesn't roll back everything else
            // that arrived in the same page.
            assertThat(syncMetadataDao.getLastSyncedAt(SyncOrchestrator.ENTITY_TYPE_RECIPES)).isEqualTo(5000L)
            assertThat(result.pullResult.pages).isEqualTo(1)
        }

    @Test
    fun `push filters out ingredient refs not known to server`() = runTest(testDispatcher) {
        val fakeIngredientId = UUID.randomUUID() // not in ingredientDao → not SYNCED
        val recipe = createDirtyRecipe(uuid = recipeId1)
        recipeDao.upsertRecipe(recipe)
        recipeIngredientDao.upsertRecipeIngredient(
            RecipeIngredientEntity(recipeId1, fakeIngredientId, 1.0, "piece", 1000L)
        )

        syncOrchestrator.sync()

        val dto = syncNetworkDataSource.capturedPushRequests[0].recipes[0]
        assertThat(dto.ingredients).isEmpty()
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
