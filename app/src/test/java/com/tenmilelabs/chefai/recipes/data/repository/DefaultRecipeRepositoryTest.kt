package com.tenmilelabs.chefai.recipes.data.repository

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.data.local.FakeSecurePreferences
import com.tenmilelabs.chefai.auth.data.network.FakeAuthNetworkDataSource
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.usecase.AccountUpgradeUseCase
import com.tenmilelabs.chefai.core.data.local.room.FakeTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeLabelDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeTagDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeUserDao
import com.tenmilelabs.chefai.core.data.sync.SyncManager
import io.mockk.mockk
import java.util.UUID
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.testutil.recipe1
import com.tenmilelabs.chefai.core.testutil.recipe3
import com.tenmilelabs.chefai.core.testutil.recipeEntity1
import com.tenmilelabs.chefai.core.testutil.recipeEntity2
import com.tenmilelabs.chefai.core.testutil.recipeEntity3
import com.tenmilelabs.chefai.core.testutil.recipeId1
import com.tenmilelabs.chefai.core.testutil.testIngredients
import com.tenmilelabs.chefai.core.testutil.testLabels
import com.tenmilelabs.chefai.core.testutil.testRecipeIngredients
import com.tenmilelabs.chefai.core.testutil.testRecipeLabels
import com.tenmilelabs.chefai.core.testutil.testRecipeTags
import com.tenmilelabs.chefai.core.testutil.testSteps1
import com.tenmilelabs.chefai.core.testutil.testSteps2
import com.tenmilelabs.chefai.core.testutil.testSteps3
import com.tenmilelabs.chefai.core.testutil.testTags
import com.tenmilelabs.chefai.core.testutil.testUser
import com.tenmilelabs.chefai.recipes.data.network.FakeApiService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class DefaultRecipeRepositoryTest {

    // SUT
    private lateinit var recipeRepository: DefaultRecipeRepository

    // Dependencies
    private lateinit var recipeDao: FakeRecipeDao
    private lateinit var recipeStepDao: FakeRecipeStepDao
    private lateinit var recipeIngredientDao: FakeRecipeIngredientDao
    private lateinit var ingredientDao: FakeIngredientDao
    private lateinit var tagDao: FakeTagDao
    private lateinit var labelDao: FakeLabelDao
    private lateinit var recipeTagDao: FakeRecipeTagCrossRefDao
    private lateinit var recipeLabelDao: FakeRecipeLabelCrossRefDao
    private lateinit var remoteDataSource: FakeApiService
    private lateinit var sessionManager: SessionManager
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope

    @Before
    fun createRepository() {
        testScope = TestScope(testDispatcher)
        val fakeSecurePreferences = FakeSecurePreferences()
        val fakeAuthNetworkDataSource = FakeAuthNetworkDataSource()

        // Pre-seed secure preferences so the session loads as the test user.
        // Use runBlocking to avoid test-scheduler timing issues in @Before.
        runBlocking {
            fakeSecurePreferences.saveAuthData(
                userUuid = testUser.uuid,
                displayName = testUser.displayName,
                email = testUser.email,
                avatarUrl = testUser.avatarUrl,
                accessToken = "test_token",
                refreshToken = "test_refresh",
                tokenExpiry = System.currentTimeMillis() + 3_600_000L
            )
        }

        val fakeUserDao = FakeUserDao()
        sessionManager = SessionManager(
            securePreferences = fakeSecurePreferences,
            authNetworkDataSource = { fakeAuthNetworkDataSource },
            userDao = fakeUserDao,
            accountUpgradeUseCaseProvider = {
                AccountUpgradeUseCase(
                    FakeTransactionRunner(), fakeUserDao, FakeRecipeDao(),
                    FakeRecipeStepDao(), FakeRecipeIngredientDao(),
                    FakeRecipeTagCrossRefDao(), FakeRecipeLabelCrossRefDao()
                )
            },
            applicationScope = testScope
        ).apply {
            uuidGenerator = { UUID.randomUUID() }
        }
        // Advance until the SessionManager init coroutine (loadSession) completes.
        testDispatcher.scheduler.advanceUntilIdle()

        recipeDao = FakeRecipeDao()
        recipeStepDao = FakeRecipeStepDao()
        recipeIngredientDao = FakeRecipeIngredientDao()
        ingredientDao = FakeIngredientDao()
        tagDao = FakeTagDao()
        labelDao = FakeLabelDao()
        recipeTagDao = FakeRecipeTagCrossRefDao()
        recipeLabelDao = FakeRecipeLabelCrossRefDao()
        remoteDataSource = FakeApiService()

        recipeRepository =
            DefaultRecipeRepository(
                recipeDao,
                recipeStepDao,
                recipeIngredientDao,
                ingredientDao,
                tagDao,
                labelDao,
                recipeTagDao,
                recipeLabelDao,
                remoteDataSource,
                sessionManager,
                mockk(relaxed = true) // SyncManager — relaxed mock since it's Android-dependent
            )

        // Seed the fake DAO with our test data.
        // This simulates a database with pre-existing data.
        runBlocking {
            recipeDao.seed(
                users = listOf(testUser),
                recipes = listOf(recipeEntity1, recipeEntity2, recipeEntity3),
                ingredients = testIngredients,
                labels = testLabels,
                tags = testTags,
                steps = testSteps1 + testSteps2 + testSteps3,
                recipeIngredients = testRecipeIngredients,
                recipeLabels = testRecipeLabels,
                recipeTags = testRecipeTags
            )
        }
    }

    @Test
    fun `getRecipes() returns all recipes from local source`() = runTest {
        val recipes = recipeRepository.getRecipesStream().first()
        assertThat(recipes).hasSize(3)
        assertThat(recipes.map { it.uuid }).containsExactly(
            recipeEntity1.uuid,
            recipeEntity2.uuid,
            recipeEntity3.uuid
        )
    }

    @Test
    fun `getRecipesPreviewStream() returns correct preview data`() = runTest {
        val recipePreviews = recipeRepository.getRecipesPreviewStream().first()
        assertThat(recipePreviews).hasSize(3)

        val firstRecipePreview = recipePreviews.find { it.uuid == recipeId1 }
        assertThat(firstRecipePreview).isNotNull()
        assertThat(firstRecipePreview?.title).isEqualTo(recipe1.title)
        assertThat(firstRecipePreview?.imageUrlThumbnail).isEqualTo(recipe1.imageUrlThumbnail)
    }

    @Test
    fun `getRecipesPreviewStream() returns empty list when no recipes exist`() = runTest {
        // Given: The database is cleared.
        recipeDao.deleteAllRecipes()

        // When: The preview stream is observed.
        val recipePreviews = recipeRepository.getRecipesPreviewStream().first()

        // Then: The list is empty.
        assertThat(recipePreviews).isEmpty()
    }

    @Test
    fun `getRecipe() returns correct recipe with details`() = runTest {
        val recipe = recipeRepository.getRecipeStream(recipeId1).first()
        assertThat(recipe).isNotNull()
        assertThat(recipe?.uuid).isEqualTo(recipeId1)
        assertThat(recipe?.title).isEqualTo(recipe1.title)
        assertThat(recipe?.ingredients).hasSize(2)
        assertThat(recipe?.ingredients?.map { it.ingredientDisplayName }).containsExactly(
            "Flour",
            "Milk"
        )
        assertThat(recipe?.steps).hasSize(3)
    }

    @Test
    fun `saveRecipe() creates a new recipe with all details`() = runTest {
        recipeRepository.createRecipe(recipe3)

        val savedRecipe = recipeRepository.getRecipeStream(recipe3.uuid).first()
        assertThat(savedRecipe).isNotNull()
        assertThat(savedRecipe?.title).isEqualTo(recipe3.title)
        assertThat(savedRecipe?.ingredients).hasSize(1) // recipe3 has one ingredient in test data
    }

    @Test
    fun `saveRecipe() updates an existing recipe`() = runTest {
        val updatedRecipe = recipe1.copy(title = "The Best Pancakes")
        recipeRepository.createRecipe(updatedRecipe)

        val savedRecipe = recipeRepository.getRecipeStream(recipe1.uuid).first()
        assertThat(savedRecipe?.title).isEqualTo("The Best Pancakes")
    }

    @Test
    fun `deleteRecipe() removes a recipe and its associations`() = runTest {
        // Given: A recipe with associations exists.
        val recipeBeforeDelete = recipeRepository.getRecipeStream(recipeId1).first()
        assertThat(recipeBeforeDelete).isNotNull()
        assertThat(recipeBeforeDelete?.ingredients).isNotEmpty()
        assertThat(recipeBeforeDelete?.steps).isNotEmpty()

        // When: The recipe is deleted.
        recipeRepository.deleteRecipe(recipeId1)

        // Then: The recipe is no longer available.
        val recipes = recipeRepository.getRecipesStream().first()
        assertThat(recipes.find { it.uuid == recipeId1 }).isNull()
        assertThat(recipes).hasSize(2)
        assertThat(recipeRepository.getRecipeStream(recipeId1).first()).isNull()

        // And When: A new recipe with the same ID but no associations is created.
        val newRecipeWithSameId = recipe1.copy(
            steps = emptyList(),
            ingredients = emptyList(),
            tags = emptyList(),
            labels = emptyList()
        )
        recipeRepository.createRecipe(newRecipeWithSameId)

        // Then: Fetching the recipe should not show the old, orphaned data.
        val recreatedRecipe = recipeRepository.getRecipeStream(recipeId1).first()
        assertThat(recreatedRecipe).isNotNull()
        assertThat(recreatedRecipe?.steps).isEmpty()
        assertThat(recreatedRecipe?.ingredients).isEmpty()
    }

    @Test
    fun `getRecipesPreviewStream returns recipes created by anonymous user`() = runTest {
        // Given: A recipe created with an anonymous user's creatorId
        val anonymousUserId = UUID.randomUUID()
        val anonymousUser = UserEntity(
            uuid = anonymousUserId,
            displayName = "Guest",
            email = "",
            avatarUrl = "",
            updatedAt = System.currentTimeMillis(),
            deletedAt = null,
            syncState = SyncState.PENDING
        )
        val anonymousRecipe = RecipeEntity(
            uuid = UUID.randomUUID(),
            title = "Anonymous Recipe",
            description = "Created offline",
            imageUrl = "",
            imageUrlThumbnail = "",
            prepTimeMinutes = 5,
            cookTimeMinutes = 10,
            servings = 2,
            creatorId = anonymousUserId,
            recipeExternalUrl = null,
            updatedAt = System.currentTimeMillis(),
            deletedAt = null,
            syncState = SyncState.PENDING
        )

        recipeDao.seed(
            users = listOf(anonymousUser),
            recipes = listOf(anonymousRecipe),
            ingredients = emptyList(),
            labels = emptyList(),
            tags = emptyList(),
            steps = emptyList(),
            recipeIngredients = emptyList(),
            recipeLabels = emptyList(),
            recipeTags = emptyList()
        )

        // When: Loading all recipes (no user filtering)
        val previews = recipeRepository.getRecipesPreviewStream().first()

        // Then: The anonymous user's recipe is included
        val anonymousPreview = previews.find { it.title == "Anonymous Recipe" }
        assertThat(anonymousPreview).isNotNull()
        assertThat(anonymousPreview?.creatorId).isEqualTo(anonymousUserId)
    }

    @Test
    fun `createRecipe persists recipe with PENDING syncState`() = runTest {
        // Given: A new recipe to save
        val newRecipe = recipe1.copy(uuid = UUID.randomUUID(), title = "New Test Recipe")

        // When: Saving the recipe
        recipeRepository.createRecipe(newRecipe)

        // Then: The recipe is persisted with PENDING syncState
        val savedEntity = recipeDao.getRecipeById(newRecipe.uuid)
        assertThat(savedEntity).isNotNull()
        assertThat(savedEntity?.syncState).isEqualTo(SyncState.PENDING)
        assertThat(savedEntity?.title).isEqualTo("New Test Recipe")
    }
}
