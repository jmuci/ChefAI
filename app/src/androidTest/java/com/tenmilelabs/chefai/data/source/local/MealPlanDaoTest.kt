package com.tenmilelabs.chefai.data.source.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.MealPlanDayEntity
import com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
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
 * Instrumented test for [com.tenmilelabs.chefai.core.data.local.room.dao.MealPlanDao] against a real
 * (in-memory) Room database — meal plans get heavy feature traffic (week detail view, cooked
 * toggles, on-device generation) but the DAO's generated SQL had no coverage beyond
 * `DefaultMealPlanRepositoryTest`'s hand-written fake.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class MealPlanDaoTest {

    private lateinit var database: ChefAIDataBase

    private val userA = UserEntity(
        uuid = UuidV7Generator.newId(),
        displayName = "User A",
        email = "a@test.com",
        avatarUrl = "",
        updatedAt = 0L,
        deletedAt = null,
    )

    private val userB = UserEntity(
        uuid = UuidV7Generator.newId(),
        displayName = "User B",
        email = "b@test.com",
        avatarUrl = "",
        updatedAt = 0L,
        deletedAt = null,
    )

    private fun mealPlan(
        uuid: UUID = UuidV7Generator.newId(),
        userId: UUID = userA.uuid,
        name: String = "This week",
        createdAt: Long = System.currentTimeMillis(),
        deletedAt: Long? = null,
        syncState: SyncState = SyncState.PENDING,
    ) = MealPlanEntity(
        uuid = uuid,
        userId = userId,
        name = name,
        status = "READY",
        preferencesJson = "{}",
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = deletedAt,
        syncState = syncState,
    )

    private fun day(
        mealPlanId: UUID,
        dayIndex: Int,
        dinnerCookedAt: Long? = null,
        lunchCookedAt: Long? = null,
    ) = MealPlanDayEntity(
        uuid = UuidV7Generator.newId(),
        mealPlanId = mealPlanId,
        dayIndex = dayIndex,
        dinnerRecipeId = null,
        lunchRecipeId = null,
        dinnerCookedAt = dinnerCookedAt,
        lunchCookedAt = lunchCookedAt,
    )

    @Before
    fun createDb() = runTest {
        database = Room.inMemoryDatabaseBuilder(getApplicationContext(), ChefAIDataBase::class.java)
            .allowMainThreadQueries()
            .build()
        database.userDao().upsertUser(userA)
        database.userDao().upsertUser(userB)
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun observeMealPlansForUser_excludesOtherUsersAndSoftDeletedPlans() = runTest {
        val mine = mealPlan(userId = userA.uuid)
        val someoneElses = mealPlan(userId = userB.uuid)
        val deleted = mealPlan(userId = userA.uuid, deletedAt = 999L)
        database.mealPlanDao().upsertMealPlan(mine)
        database.mealPlanDao().upsertMealPlan(someoneElses)
        database.mealPlanDao().upsertMealPlan(deleted)

        val result = database.mealPlanDao().observeMealPlansForUser(userA.uuid).first()

        assertEquals(listOf(mine.uuid), result.map { it.uuid })
    }

    @Test
    fun observeMealPlansForUser_ordersNewestFirst() = runTest {
        val older = mealPlan(userId = userA.uuid, createdAt = 1_000L)
        val newer = mealPlan(userId = userA.uuid, createdAt = 2_000L)
        database.mealPlanDao().upsertMealPlan(older)
        database.mealPlanDao().upsertMealPlan(newer)

        val result = database.mealPlanDao().observeMealPlansForUser(userA.uuid).first()

        assertEquals(listOf(newer.uuid, older.uuid), result.map { it.uuid })
    }

    @Test
    fun upsertDays_thenDeleteDaysForMealPlan_removesOnlyThosedays() = runTest {
        val plan = mealPlan()
        val otherPlan = mealPlan()
        database.mealPlanDao().upsertMealPlan(plan)
        database.mealPlanDao().upsertMealPlan(otherPlan)
        database.mealPlanDao().upsertDays(listOf(day(plan.uuid, 0), day(plan.uuid, 1)))
        database.mealPlanDao().upsertDays(listOf(day(otherPlan.uuid, 0)))

        database.mealPlanDao().deleteDaysForMealPlan(plan.uuid)

        assertTrue(database.mealPlanDao().getDaysForMealPlan(plan.uuid).isEmpty())
        assertEquals(1, database.mealPlanDao().getDaysForMealPlan(otherPlan.uuid).size)
    }

    @Test
    fun setDinnerCookedAt_touchesOnlyDinner_leavingLunchUntouched() = runTest {
        val plan = mealPlan()
        val theDay = day(plan.uuid, dayIndex = 0)
        database.mealPlanDao().upsertMealPlan(plan)
        database.mealPlanDao().upsertDays(listOf(theDay))

        database.mealPlanDao().setDinnerCookedAt(theDay.uuid, cookedAt = 12_345L)

        val loaded = database.mealPlanDao().getDaysForMealPlan(plan.uuid).single()
        assertEquals(12_345L, loaded.dinnerCookedAt)
        assertNull(loaded.lunchCookedAt)
    }

    @Test
    fun setDinnerCookedAt_withNull_unmarksIt() = runTest {
        val plan = mealPlan()
        val theDay = day(plan.uuid, dayIndex = 0, dinnerCookedAt = 999L)
        database.mealPlanDao().upsertMealPlan(plan)
        database.mealPlanDao().upsertDays(listOf(theDay))

        database.mealPlanDao().setDinnerCookedAt(theDay.uuid, cookedAt = null)

        assertNull(database.mealPlanDao().getDaysForMealPlan(plan.uuid).single().dinnerCookedAt)
    }

    @Test
    fun observeDaysForUser_joinsAcrossAllOfThatUsersLivePlans_orderedByDayIndex() = runTest {
        val planOne = mealPlan(userId = userA.uuid)
        val planTwo = mealPlan(userId = userA.uuid)
        val someoneElsesPlan = mealPlan(userId = userB.uuid)
        database.mealPlanDao().upsertMealPlan(planOne)
        database.mealPlanDao().upsertMealPlan(planTwo)
        database.mealPlanDao().upsertMealPlan(someoneElsesPlan)
        database.mealPlanDao().upsertDays(listOf(day(planOne.uuid, dayIndex = 2)))
        database.mealPlanDao().upsertDays(listOf(day(planTwo.uuid, dayIndex = 1)))
        database.mealPlanDao().upsertDays(listOf(day(someoneElsesPlan.uuid, dayIndex = 0)))

        val result = database.mealPlanDao().observeDaysForUser(userA.uuid).first()

        assertEquals(listOf(1, 2), result.map { it.dayIndex })
    }

    @Test
    fun observeDaysForUser_excludesDaysBelongingToASoftDeletedPlan() = runTest {
        val deletedPlan = mealPlan(userId = userA.uuid, deletedAt = 42L)
        database.mealPlanDao().upsertMealPlan(deletedPlan)
        database.mealPlanDao().upsertDays(listOf(day(deletedPlan.uuid, dayIndex = 0)))

        assertTrue(database.mealPlanDao().observeDaysForUser(userA.uuid).first().isEmpty())
    }

    @Test
    fun getAllDirty_returnsOnlyPendingAndDeletedPlans() = runTest {
        val pending = mealPlan(syncState = SyncState.PENDING)
        val deleted = mealPlan(syncState = SyncState.DELETED)
        val synced = mealPlan(syncState = SyncState.SYNCED)
        database.mealPlanDao().upsertMealPlan(pending)
        database.mealPlanDao().upsertMealPlan(deleted)
        database.mealPlanDao().upsertMealPlan(synced)

        val dirty = database.mealPlanDao().getAllDirty().map { it.uuid }.toSet()

        assertEquals(setOf(pending.uuid, deleted.uuid), dirty)
    }

    @Test
    fun reassignUserAndMarkPending_movesOnlyThatUsersPlansAndMarksThemPending() = runTest {
        val mine = mealPlan(userId = userA.uuid, syncState = SyncState.SYNCED)
        val someoneElses = mealPlan(userId = userB.uuid, syncState = SyncState.SYNCED)
        database.mealPlanDao().upsertMealPlan(mine)
        database.mealPlanDao().upsertMealPlan(someoneElses)

        database.mealPlanDao().reassignUserAndMarkPending(
            oldUserId = userA.uuid,
            newUserId = userB.uuid,
            updatedAt = 5_000L,
        )

        val moved = database.mealPlanDao().getMealPlanById(mine.uuid)
        val untouched = database.mealPlanDao().getMealPlanById(someoneElses.uuid)
        assertEquals(userB.uuid, moved?.userId)
        assertEquals(SyncState.PENDING, moved?.syncState)
        assertEquals(SyncState.SYNCED, untouched?.syncState)
    }

    @Test
    fun countMealPlansForUser_excludesSoftDeleted() = runTest {
        database.mealPlanDao().upsertMealPlan(mealPlan(userId = userA.uuid))
        database.mealPlanDao().upsertMealPlan(mealPlan(userId = userA.uuid, deletedAt = 1L))

        assertEquals(1, database.mealPlanDao().countMealPlansForUser(userA.uuid))
    }
}
