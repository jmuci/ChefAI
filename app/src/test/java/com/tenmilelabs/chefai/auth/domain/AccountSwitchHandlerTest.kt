package com.tenmilelabs.chefai.auth.domain

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.data.local.FakeSecurePreferences
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.BookmarkedRecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.FakeTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.HouseholdEntity
import com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeCookEventEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.SavedMealPlanEntity
import com.tenmilelabs.chefai.core.data.local.room.SyncMetadataEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeBookmarkedRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeHouseholdDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeMealPlanDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeCookEventDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeSavedMealPlanDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeSyncMetadataDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeUserDao
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.mealplans.data.local.SavedPlanImageStore
import com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSwitchHandlerTest {

    private lateinit var securePreferences: FakeSecurePreferences
    private lateinit var database: ChefAIDataBase
    private lateinit var recipeDao: FakeRecipeDao
    private lateinit var userDao: FakeUserDao
    private lateinit var householdDao: FakeHouseholdDao
    private lateinit var mealPlanDao: FakeMealPlanDao
    private lateinit var savedMealPlanDao: FakeSavedMealPlanDao
    private lateinit var bookmarkedRecipeDao: FakeBookmarkedRecipeDao
    private lateinit var recipeCookEventDao: FakeRecipeCookEventDao
    private lateinit var syncMetadataDao: FakeSyncMetadataDao
    private lateinit var recipeImageStore: RecipeImageStore
    private lateinit var savedPlanImageStore: SavedPlanImageStore
    private lateinit var handler: AccountSwitchHandler

    @Before
    fun setup() {
        securePreferences = FakeSecurePreferences()
        database = mockk(relaxed = true)
        recipeDao = FakeRecipeDao()
        userDao = FakeUserDao()
        householdDao = FakeHouseholdDao()
        mealPlanDao = FakeMealPlanDao()
        savedMealPlanDao = FakeSavedMealPlanDao()
        bookmarkedRecipeDao = FakeBookmarkedRecipeDao()
        recipeCookEventDao = FakeRecipeCookEventDao(mealPlanDao)
        syncMetadataDao = FakeSyncMetadataDao()
        recipeImageStore = mockk(relaxed = true)
        savedPlanImageStore = mockk(relaxed = true)
        handler = AccountSwitchHandler(
            securePreferences = securePreferences,
            database = database,
            recipeDao = recipeDao,
            userDao = userDao,
            householdDao = householdDao,
            mealPlanDao = mealPlanDao,
            savedMealPlanDao = savedMealPlanDao,
            bookmarkedRecipeDao = bookmarkedRecipeDao,
            recipeCookEventDao = recipeCookEventDao,
            syncMetadataDao = syncMetadataDao,
            transactionRunner = FakeTransactionRunner(),
            recipeImageStore = recipeImageStore,
            savedPlanImageStore = savedPlanImageStore,
        )
    }

    @Test
    fun `clearAllLocalData wipes every table and every cached image`() = runTest {
        handler.clearAllLocalData()

        coVerify(exactly = 1) { database.clearAllTables() }
        coVerify(exactly = 1) { recipeImageStore.deleteAll() }
        coVerify(exactly = 1) { savedPlanImageStore.deleteAll() }
    }

    @Test
    fun `first authenticated login keeps database and stores user id`() = runTest {
        val userId = UuidV7Generator.newId()

        val cleared = handler.handleLogin(userId)

        assertThat(cleared).isEqualTo(AccountSwitchOutcome.NO_CHANGE)
        assertThat(securePreferences.getStoredCurrentUserId().first()).isEqualTo(userId)
        coVerify(exactly = 0) { database.clearAllTables() }
    }

    @Test
    fun `same authenticated user keeps database`() = runTest {
        val userId = UuidV7Generator.newId()
        securePreferences.setCurrentUserId(userId)

        val cleared = handler.handleLogin(userId)

        assertThat(cleared).isEqualTo(AccountSwitchOutcome.NO_CHANGE)
        coVerify(exactly = 0) { database.clearAllTables() }
    }

    @Test
    fun `different authenticated user without anonymous session clears database and stores replacement user id`() = runTest {
        securePreferences.setCurrentUserId(UuidV7Generator.newId())
        val newUserId = UuidV7Generator.newId()

        val cleared = handler.handleLogin(newUserId)

        assertThat(cleared).isEqualTo(AccountSwitchOutcome.CLEARED_DATABASE)
        assertThat(securePreferences.getStoredCurrentUserId().first()).isEqualTo(newUserId)
        coVerify(exactly = 1) { database.clearAllTables() }
        coVerify(exactly = 1) { recipeImageStore.deleteAll() }
        coVerify(exactly = 1) { savedPlanImageStore.deleteAll() }
    }

    @Test
    fun `different authenticated user with anonymous session preserves anonymous data path`() = runTest {
        securePreferences.setCurrentUserId(UuidV7Generator.newId())
        val anonymousUserId = UuidV7Generator.newId()
        val newUserId = UuidV7Generator.newId()

        val outcome = handler.handleLogin(
            newUserId = newUserId,
            anonymousUserId = anonymousUserId
        )

        assertThat(outcome).isEqualTo(AccountSwitchOutcome.PRESERVED_ANONYMOUS_DATA)
        assertThat(securePreferences.getStoredCurrentUserId().first()).isEqualTo(newUserId)
        coVerify(exactly = 0) { database.clearAllTables() }
        coVerify(exactly = 0) { recipeImageStore.deleteAll() }
        coVerify(exactly = 0) { savedPlanImageStore.deleteAll() }
    }

    @Test
    fun `switching away deletes the departing account's images but not the anonymous ones`() = runTest {
        val previousUserId = UuidV7Generator.newId()
        val anonymousUserId = UuidV7Generator.newId()
        val newUserId = UuidV7Generator.newId()
        securePreferences.setCurrentUserId(previousUserId)

        val departingRecipe = recipeFor(previousUserId)
        val anonymousRecipe = recipeFor(anonymousUserId)
        recipeDao.upsertRecipe(departingRecipe)
        recipeDao.upsertRecipe(anonymousRecipe)

        handler.handleLogin(newUserId = newUserId, anonymousUserId = anonymousUserId)

        // The rows going away must take their image files with them, or the previous account's
        // photos stay readable on a shared device.
        coVerify(exactly = 1) { recipeImageStore.delete(departingRecipe.uuid) }
        coVerify(exactly = 0) { recipeImageStore.delete(anonymousRecipe.uuid) }
        coVerify(exactly = 0) { recipeImageStore.deleteAll() }
    }

    @Test
    fun `switching away clears the household cache so the incoming account can't inherit it`() = runTest {
        // Reproduces a real bug: the household tables have no userId column (a single "my
        // household" mirror, see HouseholdEntity's doc), so without this clear, a brand-new
        // account signing up on the same device — e.g. accepting an invite right after the
        // previous account logged out — would see the departing account's household as its own
        // until the next refresh happened to complete.
        val previousUserId = UuidV7Generator.newId()
        val anonymousUserId = UuidV7Generator.newId()
        val newUserId = UuidV7Generator.newId()
        securePreferences.setCurrentUserId(previousUserId)
        householdDao.upsertHousehold(
            HouseholdEntity(
                uuid = UuidV7Generator.newId(),
                name = "The Test Kitchen",
                ownerId = previousUserId,
                createdAt = 0L,
                updatedAt = 0L,
            )
        )

        handler.handleLogin(newUserId = newUserId, anonymousUserId = anonymousUserId)

        assertThat(householdDao.getCachedHouseholdId()).isNull()
    }

    @Test
    fun `switching away drops the departing account's cook history but keeps the anonymous one`() = runTest {
        // Reproduces a real leak: cook events have no user column and observeCookedRecipeIds
        // reads the whole table, so the incoming account's "Cooked before" filter showed the
        // departing account's history.
        val previousUserId = UuidV7Generator.newId()
        val anonymousUserId = UuidV7Generator.newId()
        securePreferences.setCurrentUserId(previousUserId)

        val departingPlan = planFor(previousUserId)
        val anonymousPlan = planFor(anonymousUserId)
        val deletedAnonymousPlan = planFor(anonymousUserId).copy(deletedAt = 5L, syncState = SyncState.DELETED)
        listOf(departingPlan, anonymousPlan, deletedAnonymousPlan).forEach { mealPlanDao.upsertMealPlan(it) }

        val departingSynced = cookEventOn(departingPlan.uuid).copy(syncState = SyncState.SYNCED)
        val departingPending = cookEventOn(departingPlan.uuid)
        val anonymous = cookEventOn(anonymousPlan.uuid)
        // History outlives a plan the anonymous user deleted — the soft-deleted row is the owner signal.
        val anonymousOnDeletedPlan = cookEventOn(deletedAnonymousPlan.uuid)
        // An event whose plan this device no longer has can't be the anonymous session's own.
        val orphan = cookEventOn(UuidV7Generator.newId()).copy(syncState = SyncState.SYNCED)
        listOf(departingSynced, departingPending, anonymous, anonymousOnDeletedPlan, orphan)
            .forEach { recipeCookEventDao.insert(it) }

        handler.handleLogin(newUserId = UuidV7Generator.newId(), anonymousUserId = anonymousUserId)

        assertThat(recipeCookEventDao.all.map { it.uuid })
            .containsExactly(anonymous.uuid, anonymousOnDeletedPlan.uuid)
        assertThat(recipeCookEventDao.observeCookedRecipeIds().first())
            .containsExactly(anonymous.recipeId, anonymousOnDeletedPlan.recipeId)
    }

    @Test
    fun `switching away drops the departing account's plans, saved plans and bookmarks so none of it is pushed`() = runTest {
        // The push collects meal plans and saved plans without a user filter, so leaving these
        // behind sent the departing account's unsynced rows under the incoming account's token.
        val previousUserId = UuidV7Generator.newId()
        val anonymousUserId = UuidV7Generator.newId()
        securePreferences.setCurrentUserId(previousUserId)

        val departingPlan = planFor(previousUserId)
        val householdPlan = planFor(UuidV7Generator.newId()).copy(householdId = UuidV7Generator.newId())
        val anonymousPlan = planFor(anonymousUserId)
        listOf(departingPlan, householdPlan, anonymousPlan).forEach { mealPlanDao.upsertMealPlan(it) }
        val departingSaved = savedPlanFor(previousUserId)
        val anonymousSaved = savedPlanFor(anonymousUserId)
        savedMealPlanDao.upsert(departingSaved)
        savedMealPlanDao.upsert(anonymousSaved)
        val recipeId = UuidV7Generator.newId()
        bookmarkedRecipeDao.upsert(BookmarkedRecipeEntity(previousUserId, recipeId, updatedAt = 1L, deletedAt = null))
        bookmarkedRecipeDao.upsert(BookmarkedRecipeEntity(anonymousUserId, recipeId, updatedAt = 1L, deletedAt = null))

        handler.handleLogin(newUserId = UuidV7Generator.newId(), anonymousUserId = anonymousUserId)

        assertThat(mealPlanDao.getAllDirty().map { it.uuid }).containsExactly(anonymousPlan.uuid)
        assertThat(savedMealPlanDao.all.map { it.uuid }).containsExactly(anonymousSaved.uuid)
        assertThat(bookmarkedRecipeDao.getAllDirty().map { it.userId }).containsExactly(anonymousUserId)
        coVerify(exactly = 1) { savedPlanImageStore.delete(departingSaved.uuid) }
        coVerify(exactly = 0) { savedPlanImageStore.delete(anonymousSaved.uuid) }
    }

    @Test
    fun `switching away resets the pull cursor so the incoming account pulls from the start`() = runTest {
        // The cursor belonged to the departing account: kept, the incoming account's first pull
        // would skip every change it made before that moment.
        securePreferences.setCurrentUserId(UuidV7Generator.newId())
        syncMetadataDao.upsert(SyncMetadataEntity(entityType = "recipes", lastSyncedAt = 1_000L))

        handler.handleLogin(newUserId = UuidV7Generator.newId(), anonymousUserId = UuidV7Generator.newId())

        assertThat(syncMetadataDao.getLastSyncedAt("recipes")).isNull()
    }

    @Test
    fun `same account logging in again keeps its plans and cook history but pulls from the start`() = runTest {
        val userId = UuidV7Generator.newId()
        securePreferences.setCurrentUserId(userId)
        val plan = planFor(userId)
        mealPlanDao.upsertMealPlan(plan)
        recipeCookEventDao.insert(cookEventOn(plan.uuid))
        syncMetadataDao.upsert(SyncMetadataEntity(entityType = "recipes", lastSyncedAt = 1_000L))

        handler.handleLogin(newUserId = userId, anonymousUserId = UuidV7Generator.newId(), isCredentialLogin = true)

        assertThat(mealPlanDao.getMealPlanById(plan.uuid)).isNotNull()
        assertThat(recipeCookEventDao.all).hasSize(1)
        assertThat(syncMetadataDao.getLastSyncedAt("recipes")).isNull()
    }

    @Test
    fun `a session restore for the same account keeps the pull cursor`() = runTest {
        // SessionManager.loadSession runs handleLogin on every cold start; clearing the cursor
        // there would re-download everything each time the app starts.
        val userId = UuidV7Generator.newId()
        securePreferences.setCurrentUserId(userId)
        syncMetadataDao.upsert(SyncMetadataEntity(entityType = "recipes", lastSyncedAt = 1_000L))

        handler.handleLogin(newUserId = userId)

        assertThat(syncMetadataDao.getLastSyncedAt("recipes")).isEqualTo(1_000L)
    }

    private fun planFor(userId: UUID) = MealPlanEntity(
        uuid = UuidV7Generator.newId(),
        userId = userId,
        name = "Plan",
        status = "READY",
        preferencesJson = "{}",
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
    )

    private fun savedPlanFor(userId: UUID) = SavedMealPlanEntity(
        uuid = UuidV7Generator.newId(),
        userId = userId,
        title = "Saved",
        preferencesJson = "{}",
        daysJson = "[]",
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun cookEventOn(mealPlanId: UUID) = RecipeCookEventEntity(
        uuid = UuidV7Generator.newId(),
        recipeId = UuidV7Generator.newId(),
        mealPlanId = mealPlanId,
        dayIndex = 0,
        slot = "DINNER",
        cookedAt = 1L,
    )

    private fun recipeFor(creatorId: UUID) = RecipeEntity(
        uuid = UuidV7Generator.newId(),
        title = "Recipe",
        description = "",
        imageUrl = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 1,
        cookTimeMinutes = 1,
        servings = 1,
        creatorId = creatorId,
        recipeExternalUrl = null,
        updatedAt = 1L,
        deletedAt = null,
    )
}
