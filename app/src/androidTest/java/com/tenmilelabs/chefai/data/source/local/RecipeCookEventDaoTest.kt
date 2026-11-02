package com.tenmilelabs.chefai.data.source.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.MealPlanDayEntity
import com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeCookEventEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Instrumented test for [com.tenmilelabs.chefai.core.data.local.room.dao.RecipeCookEventDao] against
 * a real (in-memory) Room database — chiefly that cook history outlives the meal plan it came from,
 * which `meal_plan_days`' own `ON DELETE CASCADE` cooked marks do not.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class RecipeCookEventDaoTest {

    private lateinit var database: ChefAIDataBase

    private val user = UserEntity(
        uuid = UuidV7Generator.newId(),
        displayName = "Chef",
        email = "chef@test.com",
        avatarUrl = "",
        updatedAt = 0L,
        deletedAt = null,
    )

    private fun mealPlan() = MealPlanEntity(
        uuid = UuidV7Generator.newId(),
        userId = user.uuid,
        name = "This week",
        status = "READY",
        preferencesJson = "{}",
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
        syncState = SyncState.SYNCED,
        householdId = null,
    )

    private fun event(
        recipeId: UUID,
        mealPlanId: UUID = UUID.randomUUID(),
        dayIndex: Int = 0,
        slot: String = "DINNER",
    ) = RecipeCookEventEntity(
        uuid = UuidV7Generator.newId(),
        recipeId = recipeId,
        mealPlanId = mealPlanId,
        dayIndex = dayIndex,
        slot = slot,
        cookedAt = 1_000L,
    )

    @Before
    fun createDb() = runTest {
        database = Room.inMemoryDatabaseBuilder(getApplicationContext(), ChefAIDataBase::class.java)
            .allowMainThreadQueries()
            .build()
        database.userDao().upsertUser(user)
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun observeCookedRecipeIds_isEmptyWithNoHistory() = runTest {
        assertTrue(database.recipeCookEventDao().observeCookedRecipeIds().first().isEmpty())
    }

    @Test
    fun observeCookedRecipeIds_returnsEachRecipeOnceHoweverOftenCooked() = runTest {
        val dao = database.recipeCookEventDao()
        val recipe = UUID.randomUUID()
        dao.insert(event(recipe, dayIndex = 0))
        dao.insert(event(recipe, dayIndex = 3))

        assertEquals(listOf(recipe), dao.observeCookedRecipeIds().first())
    }

    @Test
    fun tombstoneForSlot_hidesOnlyThatSlotsEventForThatRecipeAndQueuesItForPush() = runTest {
        val dao = database.recipeCookEventDao()
        val planId = UUID.randomUUID()
        val dinner = UUID.randomUUID()
        val lunch = UUID.randomUUID()
        val earlierDinnerInSameSlot = UUID.randomUUID()
        val dinnerEvent = event(dinner, planId, dayIndex = 0, slot = "DINNER").copy(syncState = SyncState.SYNCED)
        dao.insert(dinnerEvent)
        dao.insert(event(lunch, planId, dayIndex = 0, slot = "LUNCH"))
        dao.insert(event(earlierDinnerInSameSlot, planId, dayIndex = 0, slot = "DINNER"))

        dao.tombstoneForSlot(planId, dayIndex = 0, slot = "DINNER", recipeId = dinner, deletedAt = 5_000L)

        assertEquals(
            setOf(lunch, earlierDinnerInSameSlot),
            dao.observeCookedRecipeIds().first().toSet()
        )
        val tombstone = requireNotNull(dao.getById(dinnerEvent.uuid))
        assertEquals(5_000L, tombstone.deletedAt)
        assertEquals(5_000L, tombstone.updatedAt)
        assertEquals(SyncState.PENDING, tombstone.syncState)
        assertEquals(null, dao.getLiveForSlot(planId, 0, "DINNER", dinner))
    }

    @Test
    fun getAllDirty_returnsPendingEventsAndTombstonesButNotSyncedOnes() = runTest {
        val dao = database.recipeCookEventDao()
        val pending = event(UUID.randomUUID())
        val synced = event(UUID.randomUUID()).copy(syncState = SyncState.SYNCED)
        val parked = event(UUID.randomUUID()).copy(syncState = SyncState.CANCELLED)
        listOf(pending, synced, parked).forEach { dao.insert(it) }

        assertEquals(listOf(pending.uuid), dao.getAllDirty().map { it.uuid })
    }

    @Test
    fun updateSyncState_leavesARowEditedSincePushPending() = runTest {
        val dao = database.recipeCookEventDao()
        val e = event(UUID.randomUUID()).copy(updatedAt = 1_000L)
        dao.insert(e)

        val skipped = dao.updateSyncState(e.uuid, SyncState.SYNCED, 9_000L, expectedUpdatedAt = 999L)
        val applied = dao.updateSyncState(e.uuid, SyncState.SYNCED, 9_000L, expectedUpdatedAt = 1_000L)

        assertEquals(0, skipped)
        assertEquals(1, applied)
        assertEquals(9_000L, requireNotNull(dao.getById(e.uuid)).updatedAt)
    }

    @Test
    fun reassignId_rekeysTheRowAndQueuesItAgain() = runTest {
        val dao = database.recipeCookEventDao()
        val e = event(UUID.randomUUID()).copy(syncState = SyncState.SYNCED)
        dao.insert(e)
        val newId = UuidV7Generator.newId()

        dao.reassignId(e.uuid, newId)

        assertEquals(null, dao.getById(e.uuid))
        assertEquals(SyncState.PENDING, requireNotNull(dao.getById(newId)).syncState)
    }

    @Test
    fun history_survivesHardDeletingTheMealPlan() = runTest {
        val plan = mealPlan()
        val recipe = UUID.randomUUID()
        database.mealPlanDao().upsertMealPlan(plan)
        database.mealPlanDao().upsertDays(
            listOf(
                MealPlanDayEntity(
                    uuid = UuidV7Generator.newId(),
                    mealPlanId = plan.uuid,
                    dayIndex = 0,
                    dinnerRecipeId = recipe,
                    lunchRecipeId = null,
                    dinnerCookedAt = 1_000L,
                )
            )
        )
        database.recipeCookEventDao().insert(event(recipe, plan.uuid))

        // When: the plan is hard-deleted, cascading away its days and their cooked marks
        database.openHelper.writableDatabase.execSQL("DELETE FROM meal_plans")

        // Then
        assertTrue(database.mealPlanDao().getDaysForMealPlan(plan.uuid).isEmpty())
        assertEquals(listOf(recipe), database.recipeCookEventDao().observeCookedRecipeIds().first())
    }
}
