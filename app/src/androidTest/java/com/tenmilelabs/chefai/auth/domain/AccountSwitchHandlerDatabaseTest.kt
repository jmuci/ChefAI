package com.tenmilelabs.chefai.auth.domain

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.tenmilelabs.chefai.auth.data.local.FakeSecurePreferences
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.HouseholdEntity
import com.tenmilelabs.chefai.core.data.local.room.HouseholdInviteEntity
import com.tenmilelabs.chefai.core.data.local.room.HouseholdMemberEntity
import com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeCookEventEntity
import com.tenmilelabs.chefai.core.data.local.room.RoomTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.mealplans.data.local.SavedPlanImageStore
import com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented: pins down that [AccountSwitchHandler]'s account-switch `database.clearAllTables()`
 * path clears the household tables too — see ADR-014. Room's `clearAllTables()` already covers
 * every table registered on the `@Database` by construction, so nothing in `AccountSwitchHandler`
 * itself needed to change when households were added; this test exists purely so that invariant is
 * pinned down rather than relying on it staying true by accident.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class AccountSwitchHandlerDatabaseTest {

    private lateinit var database: ChefAIDataBase
    private lateinit var handler: AccountSwitchHandler
    private lateinit var securePreferences: FakeSecurePreferences

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(getApplicationContext(), ChefAIDataBase::class.java)
            .allowMainThreadQueries()
            .build()
        securePreferences = FakeSecurePreferences()
        handler = AccountSwitchHandler(
            securePreferences = securePreferences,
            database = database,
            recipeDao = database.recipeDao(),
            userDao = database.userDao(),
            householdDao = database.householdDao(),
            mealPlanDao = database.mealPlanDao(),
            savedMealPlanDao = database.savedMealPlanDao(),
            bookmarkedRecipeDao = database.bookmarkedRecipeDao(),
            recipeCookEventDao = database.recipeCookEventDao(),
            syncMetadataDao = database.syncMetadataDao(),
            transactionRunner = RoomTransactionRunner(database),
            recipeImageStore = RecipeImageStore(getApplicationContext(), Dispatchers.IO),
            savedPlanImageStore = SavedPlanImageStore(getApplicationContext(), Dispatchers.IO),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun accountSwitchClearsHouseholdTables() = runTest {
        val previousUserId = UuidV7Generator.newId()
        securePreferences.setCurrentUserId(previousUserId)

        val household = HouseholdEntity(
            uuid = UuidV7Generator.newId(),
            name = "The Test Kitchen",
            ownerId = previousUserId,
            createdAt = 0L,
            updatedAt = 0L,
        )
        database.householdDao().upsertHousehold(household)
        database.householdDao().upsertMembers(
            listOf(
                HouseholdMemberEntity(
                    householdId = household.uuid,
                    userId = previousUserId,
                    displayName = "Chef",
                    avatarUrl = "",
                    role = "OWNER",
                    joinedAt = 0L,
                )
            )
        )
        database.householdDao().upsertInvites(
            listOf(
                HouseholdInviteEntity(
                    inviteId = UuidV7Generator.newId(),
                    householdId = household.uuid,
                    expiresAt = 999_999L,
                    createdAt = 1_000L,
                )
            )
        )

        // A different account logging in with no anonymous session takes the full-clear path.
        handler.handleLogin(newUserId = UuidV7Generator.newId())

        assertNull(database.householdDao().observeHousehold(household.uuid).first())
        assertTrue(database.householdDao().observeMembers(household.uuid).first().isEmpty())
        assertTrue(database.householdDao().observePendingInvites().first().isEmpty())
    }

    @Test
    fun accountSwitchClearsSavedMealPlansAndTheirImages() = runTest {
        val previousUserId = UuidV7Generator.newId()
        securePreferences.setCurrentUserId(previousUserId)
        database.userDao().upsertUser(
            com.tenmilelabs.chefai.core.data.local.room.UserEntity(
                uuid = previousUserId, displayName = "Chef", email = "c@test.com",
                avatarUrl = "", updatedAt = 0L, deletedAt = null,
            )
        )
        val planId = UuidV7Generator.newId()
        database.savedMealPlanDao().upsert(
            com.tenmilelabs.chefai.core.data.local.room.SavedMealPlanEntity(
                uuid = planId, userId = previousUserId, title = "T", preferencesJson = "{}",
                daysJson = "[]", createdAt = 1L, updatedAt = 1L,
            )
        )
        val dir = java.io.File(getApplicationContext<android.content.Context>().filesDir, "saved_plan_images")
        dir.mkdirs()
        val image = java.io.File(dir, planId.toString()).apply { writeText("x") }

        handler.handleLogin(newUserId = UuidV7Generator.newId())

        assertNull(database.savedMealPlanDao().getById(planId))
        assertTrue(!image.exists())
    }

    @Test
    fun anonymousSwitchKeepsOnlyTheAnonymousSessionsPlansAndCookHistory() = runTest {
        // Pins down the real SQL behind the preserve branch — the NOT IN subquery in particular,
        // which the JVM fakes only approximate.
        val previousUserId = UuidV7Generator.newId()
        val anonymousUserId = UuidV7Generator.newId()
        securePreferences.setCurrentUserId(previousUserId)
        listOf(previousUserId, anonymousUserId).forEach { id ->
            database.userDao().upsertUser(
                UserEntity(uuid = id, displayName = "Chef", email = "$id@test.com", avatarUrl = "", updatedAt = 0L, deletedAt = null)
            )
        }
        val departingPlan = planFor(previousUserId)
        val anonymousPlan = planFor(anonymousUserId)
        database.mealPlanDao().upsertMealPlan(departingPlan)
        database.mealPlanDao().upsertMealPlan(anonymousPlan)
        val departingEvent = cookEventOn(departingPlan.uuid)
        val anonymousEvent = cookEventOn(anonymousPlan.uuid)
        database.recipeCookEventDao().insert(departingEvent)
        database.recipeCookEventDao().insert(anonymousEvent)

        handler.handleLogin(newUserId = UuidV7Generator.newId(), anonymousUserId = anonymousUserId)

        assertNull(database.mealPlanDao().getMealPlanById(departingPlan.uuid))
        assertTrue(database.mealPlanDao().getMealPlanById(anonymousPlan.uuid) != null)
        assertNull(database.recipeCookEventDao().getById(departingEvent.uuid))
        assertTrue(database.recipeCookEventDao().observeCookedRecipeIds().first() == listOf(anonymousEvent.recipeId))
    }

    private fun planFor(userId: java.util.UUID) = MealPlanEntity(
        uuid = UuidV7Generator.newId(), userId = userId, name = "Plan", status = "READY",
        preferencesJson = "{}", createdAt = 1L, updatedAt = 1L, deletedAt = null,
    )

    private fun cookEventOn(mealPlanId: java.util.UUID) = RecipeCookEventEntity(
        uuid = UuidV7Generator.newId(), recipeId = UuidV7Generator.newId(), mealPlanId = mealPlanId,
        dayIndex = 0, slot = "DINNER", cookedAt = 1L,
    )
}
