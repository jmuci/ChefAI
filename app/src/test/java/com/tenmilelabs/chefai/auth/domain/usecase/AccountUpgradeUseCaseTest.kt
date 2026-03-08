package com.tenmilelabs.chefai.auth.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.room.FakeTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeUserDao
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.domain.model.User
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for [AccountUpgradeUseCase].
 * Verifies the anonymous → authenticated data migration: creatorId reassignment,
 * syncState marking, UserEntity creation/deletion.
 */
class AccountUpgradeUseCaseTest {

    private lateinit var useCase: AccountUpgradeUseCase
    private lateinit var fakeUserDao: FakeUserDao
    private lateinit var fakeRecipeDao: FakeRecipeDao
    private lateinit var fakeRecipeStepDao: FakeRecipeStepDao
    private lateinit var fakeRecipeIngredientDao: FakeRecipeIngredientDao
    private lateinit var fakeRecipeTagCrossRefDao: FakeRecipeTagCrossRefDao
    private lateinit var fakeRecipeLabelCrossRefDao: FakeRecipeLabelCrossRefDao

    private val anonymousId = UuidV7Generator.newId()
    private val authUser = User(
        uuid = UuidV7Generator.newId(),
        displayName = "Alice",
        email = "alice@example.com",
        avatarUrl = ""
    )

    @Before
    fun setup() {
        fakeUserDao = FakeUserDao()
        fakeRecipeDao = FakeRecipeDao()
        fakeRecipeStepDao = FakeRecipeStepDao()
        fakeRecipeIngredientDao = FakeRecipeIngredientDao()
        fakeRecipeTagCrossRefDao = FakeRecipeTagCrossRefDao()
        fakeRecipeLabelCrossRefDao = FakeRecipeLabelCrossRefDao()

        useCase = AccountUpgradeUseCase(
            transactionRunner = FakeTransactionRunner(),
            userDao = fakeUserDao,
            recipeDao = fakeRecipeDao,
            recipeStepDao = fakeRecipeStepDao,
            recipeIngredientDao = fakeRecipeIngredientDao,
            recipeTagCrossRefDao = fakeRecipeTagCrossRefDao,
            recipeLabelCrossRefDao = fakeRecipeLabelCrossRefDao
        )
    }

    // --- Helper factories ---

    private fun createAnonymousUser() = UserEntity(
        uuid = anonymousId,
        displayName = "Guest",
        email = "",
        avatarUrl = "",
        updatedAt = 1000L,
        deletedAt = null,
        syncState = SyncState.PENDING
    )

    private fun createRecipe(id: UUID = UuidV7Generator.newId(), creatorId: UUID = anonymousId) = RecipeEntity(
        uuid = id,
        title = "Test Recipe",
        description = "A test recipe",
        imageUrl = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 10,
        cookTimeMinutes = 20,
        servings = 4,
        creatorId = creatorId,
        recipeExternalUrl = null,
        privacy = RecipePrivacy.PRIVATE,
        updatedAt = 1000L,
        deletedAt = null,
        syncState = SyncState.PENDING
    )

    private fun createStep(recipeId: UUID) = RecipeStepEntity(
        uuid = UuidV7Generator.newId(),
        recipeId = recipeId,
        orderIndex = 0,
        instruction = "Do something",
        updatedAt = 1000L,
        deletedAt = null,
        syncState = SyncState.SYNCED
    )

    // --- Tests ---

    @Test
    fun `upgrade with recipes reassigns creatorId and returns count`() = runTest {
        // Given
        val recipe1 = createRecipe()
        val recipe2 = createRecipe()
        fakeRecipeDao.seed(
            users = listOf(createAnonymousUser()),
            recipes = listOf(recipe1, recipe2)
        )

        // When
        val result = useCase.execute(anonymousId, authUser)

        // Then
        assertThat(result).isEqualTo(2)
        val updatedRecipe1 = fakeRecipeDao.getRecipeById(recipe1.uuid)
        assertThat(updatedRecipe1?.creatorId).isEqualTo(authUser.uuid)
        assertThat(updatedRecipe1?.syncState).isEqualTo(SyncState.PENDING)
        val updatedRecipe2 = fakeRecipeDao.getRecipeById(recipe2.uuid)
        assertThat(updatedRecipe2?.creatorId).isEqualTo(authUser.uuid)
    }

    @Test
    fun `upgrade creates authenticated UserEntity`() = runTest {
        // Given
        fakeRecipeDao.seed(
            users = listOf(createAnonymousUser()),
            recipes = listOf(createRecipe())
        )

        // When
        useCase.execute(anonymousId, authUser)

        // Then
        val authEntity = fakeUserDao.getUserById(authUser.uuid)
        assertThat(authEntity).isNotNull()
        assertThat(authEntity!!.displayName).isEqualTo("Alice")
        assertThat(authEntity.email).isEqualTo("alice@example.com")
        assertThat(authEntity.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `upgrade deletes anonymous UserEntity`() = runTest {
        // Given
        fakeRecipeDao.seed(
            users = listOf(createAnonymousUser()),
            recipes = listOf(createRecipe())
        )

        // When
        useCase.execute(anonymousId, authUser)

        // Then
        val anonEntity = fakeUserDao.getUserById(anonymousId)
        assertThat(anonEntity).isNull()
    }

    @Test
    fun `upgrade with no recipes returns 0 and creates auth user`() = runTest {
        // Given: anonymous user exists but has no recipes
        fakeUserDao.upsertUser(createAnonymousUser())

        // When
        val result = useCase.execute(anonymousId, authUser)

        // Then
        assertThat(result).isEqualTo(0)
        val authEntity = fakeUserDao.getUserById(authUser.uuid)
        assertThat(authEntity).isNotNull()
    }

    @Test
    fun `upgrade with same anonymous and auth ID is no-op`() = runTest {
        // Given: same ID for both
        val sameIdUser = authUser.copy(uuid = anonymousId)

        // When
        val result = useCase.execute(anonymousId, sameIdUser)

        // Then
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `upgrade marks child steps as PENDING`() = runTest {
        // Given
        val recipe = createRecipe()
        val step = createStep(recipe.uuid)
        fakeRecipeDao.seed(
            users = listOf(createAnonymousUser()),
            recipes = listOf(recipe)
        )
        fakeRecipeStepDao.upsertStep(step)

        // When
        useCase.execute(anonymousId, authUser)

        // Then
        val updatedStep = fakeRecipeStepDao.getRecipeStepById(step.uuid)
        assertThat(updatedStep?.syncState).isEqualTo(SyncState.PENDING)
    }

    @Test
    fun `upgrade does not affect other users recipes`() = runTest {
        // Given: recipes from both anonymous user and another user
        val otherUserId = UuidV7Generator.newId()
        val otherUser = UserEntity(
            uuid = otherUserId, displayName = "Other", email = "other@test.com",
            avatarUrl = "", updatedAt = 1000L, deletedAt = null, syncState = SyncState.SYNCED
        )
        val anonRecipe = createRecipe()
        val otherRecipe = createRecipe(creatorId = otherUserId)

        fakeRecipeDao.seed(
            users = listOf(createAnonymousUser(), otherUser),
            recipes = listOf(anonRecipe, otherRecipe)
        )

        // When
        useCase.execute(anonymousId, authUser)

        // Then: anonymous recipe was reassigned
        val updatedAnon = fakeRecipeDao.getRecipeById(anonRecipe.uuid)
        assertThat(updatedAnon?.creatorId).isEqualTo(authUser.uuid)

        // And: other user's recipe is untouched
        val updatedOther = fakeRecipeDao.getRecipeById(otherRecipe.uuid)
        assertThat(updatedOther?.creatorId).isEqualTo(otherUserId)
    }
}
