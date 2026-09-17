package com.tenmilelabs.chefai.data.source.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity
import com.tenmilelabs.chefai.core.data.local.room.ShoppingListCheckEntity
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
 * Instrumented test for [com.tenmilelabs.chefai.core.data.local.room.dao.ShoppingListCheckDao]
 * against a real (in-memory) Room database — including the `ON DELETE CASCADE` foreign key onto
 * `meal_plans`, which no unit test (backed by a hand-written fake DAO) can actually exercise.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class ShoppingListCheckDaoTest {

    private lateinit var database: ChefAIDataBase

    private val user = UserEntity(
        uuid = UuidV7Generator.newId(),
        displayName = "Chef",
        email = "chef@test.com",
        avatarUrl = "",
        updatedAt = 0L,
        deletedAt = null,
    )

    private fun mealPlan(uuid: UUID = UuidV7Generator.newId()) = MealPlanEntity(
        uuid = uuid,
        userId = user.uuid,
        name = "This week",
        status = "READY",
        preferencesJson = "{}",
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
    )

    private fun check(
        mealPlanId: UUID,
        itemKey: String,
        checkedAt: Long = 1_000L,
        syncState: SyncState = SyncState.PENDING,
    ) = ShoppingListCheckEntity(
        mealPlanId = mealPlanId,
        itemKey = itemKey,
        checkedAt = checkedAt,
        syncState = syncState,
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
    fun upsert_thenObserveCheckedKeys_returnsTheTickedItem() = runTest {
        val plan = mealPlan()
        database.mealPlanDao().upsertMealPlan(plan)

        database.shoppingListCheckDao().upsert(check(plan.uuid, "onion"))

        assertEquals(listOf("onion"), database.shoppingListCheckDao().observeCheckedKeys(plan.uuid).first())
    }

    @Test
    fun observeCheckedKeys_scopesToItsOwnMealPlan() = runTest {
        val planA = mealPlan()
        val planB = mealPlan()
        database.mealPlanDao().upsertMealPlan(planA)
        database.mealPlanDao().upsertMealPlan(planB)
        database.shoppingListCheckDao().upsert(check(planA.uuid, "onion"))
        database.shoppingListCheckDao().upsert(check(planB.uuid, "garlic"))

        assertEquals(listOf("onion"), database.shoppingListCheckDao().observeCheckedKeys(planA.uuid).first())
        assertEquals(listOf("garlic"), database.shoppingListCheckDao().observeCheckedKeys(planB.uuid).first())
    }

    @Test
    fun upsert_onAnExistingKey_doesNotDuplicateTheRow() = runTest {
        val plan = mealPlan()
        database.mealPlanDao().upsertMealPlan(plan)

        database.shoppingListCheckDao().upsert(check(plan.uuid, "onion", checkedAt = 1L))
        database.shoppingListCheckDao().upsert(check(plan.uuid, "onion", checkedAt = 2L))

        assertEquals(listOf("onion"), database.shoppingListCheckDao().observeCheckedKeys(plan.uuid).first())
    }

    @Test
    fun observeCheckedKeys_excludesAnUpsertedButUncheckedRow() = runTest {
        // As of ADR-014's sync wiring, unchecking is an upsert with checked = false, not a
        // delete — the row is still on the list, just not in the checked set observeCheckedKeys
        // reports. See ShoppingListCheckDao's own KDoc.
        val plan = mealPlan()
        database.mealPlanDao().upsertMealPlan(plan)
        database.shoppingListCheckDao().upsert(check(plan.uuid, "onion"))
        database.shoppingListCheckDao().upsert(
            check(plan.uuid, "onion").copy(checked = false)
        )

        assertTrue(database.shoppingListCheckDao().observeCheckedKeys(plan.uuid).first().isEmpty())
        assertEquals(false, database.shoppingListCheckDao().getCheck(plan.uuid, "onion")?.checked)
    }

    @Test
    fun observeCheckedKeys_excludesASoftDeletedRow() = runTest {
        // A pulled item can arrive with deletedAt set (it left the list entirely — see
        // SyncGroceryListItem's doc) without going through delete()/clearForPlan() at all.
        val plan = mealPlan()
        database.mealPlanDao().upsertMealPlan(plan)
        database.shoppingListCheckDao().upsert(
            check(plan.uuid, "onion").copy(deletedAt = 5_000L)
        )

        assertTrue(database.shoppingListCheckDao().observeCheckedKeys(plan.uuid).first().isEmpty())
    }

    @Test
    fun delete_removesOnlyThatItem() = runTest {
        val plan = mealPlan()
        database.mealPlanDao().upsertMealPlan(plan)
        database.shoppingListCheckDao().upsert(check(plan.uuid, "onion"))
        database.shoppingListCheckDao().upsert(check(plan.uuid, "garlic"))

        database.shoppingListCheckDao().delete(plan.uuid, "onion")

        assertEquals(listOf("garlic"), database.shoppingListCheckDao().observeCheckedKeys(plan.uuid).first())
    }

    @Test
    fun clearForPlan_unchecksEveryItemForThatPlanOnly() = runTest {
        val planA = mealPlan()
        val planB = mealPlan()
        database.mealPlanDao().upsertMealPlan(planA)
        database.mealPlanDao().upsertMealPlan(planB)
        database.shoppingListCheckDao().upsert(check(planA.uuid, "onion", syncState = SyncState.SYNCED))
        database.shoppingListCheckDao().upsert(check(planA.uuid, "garlic", syncState = SyncState.SYNCED))
        database.shoppingListCheckDao().upsert(check(planB.uuid, "milk"))

        database.shoppingListCheckDao().clearForPlan(planA.uuid, state = SyncState.PENDING, updatedAt = 9_999L)

        assertTrue(database.shoppingListCheckDao().observeCheckedKeys(planA.uuid).first().isEmpty())
        assertEquals(listOf("milk"), database.shoppingListCheckDao().observeCheckedKeys(planB.uuid).first())
    }

    @Test
    fun clearForPlan_stampsUnchecheckedRowsPendingSoTheyPushLikeAnIndividualUncheckWould() = runTest {
        val plan = mealPlan()
        database.mealPlanDao().upsertMealPlan(plan)
        database.shoppingListCheckDao().upsert(check(plan.uuid, "onion", syncState = SyncState.SYNCED))

        database.shoppingListCheckDao().clearForPlan(plan.uuid, state = SyncState.PENDING, updatedAt = 9_999L)

        val onion = database.shoppingListCheckDao().getCheck(plan.uuid, "onion")
        assertEquals(false, onion?.checked)
        assertEquals(SyncState.PENDING, onion?.syncState)
        assertEquals(9_999L, onion?.updatedAt)
        assertEquals(
            "uncheck-all rows must show up in the push queue like any other dirty row",
            listOf("onion"),
            database.shoppingListCheckDao().getAllDirty().map { it.itemKey },
        )
    }

    @Test
    fun observeCheckedByUserIds_returnsOnlyCheckedItemsWithAKnownChecker() = runTest {
        val plan = mealPlan()
        val checkerId = UuidV7Generator.newId()
        database.mealPlanDao().upsertMealPlan(plan)
        database.shoppingListCheckDao().upsert(check(plan.uuid, "onion").copy(checkedBy = checkerId))
        database.shoppingListCheckDao().upsert(check(plan.uuid, "garlic")) // checked, no known checker
        database.shoppingListCheckDao().upsert(
            check(plan.uuid, "milk").copy(checked = false, checkedBy = checkerId) // unchecked
        )

        val rows = database.shoppingListCheckDao().observeCheckedByUserIds(plan.uuid).first()

        assertEquals(listOf("onion"), rows.map { it.itemKey })
        assertEquals(checkerId, rows.single().checkedBy)
    }

    @Test
    fun clearForPlan_clearsCheckedByAlongWithChecked() = runTest {
        val plan = mealPlan()
        val checkerId = UuidV7Generator.newId()
        database.mealPlanDao().upsertMealPlan(plan)
        database.shoppingListCheckDao().upsert(
            check(plan.uuid, "onion", syncState = SyncState.SYNCED).copy(checkedBy = checkerId)
        )

        database.shoppingListCheckDao().clearForPlan(plan.uuid, state = SyncState.PENDING, updatedAt = 9_999L)

        assertTrue(database.shoppingListCheckDao().observeCheckedByUserIds(plan.uuid).first().isEmpty())
        assertEquals(null, database.shoppingListCheckDao().getCheck(plan.uuid, "onion")?.checkedBy)
    }

    @Test
    fun clearForPlan_doesNotTouchAnAlreadyUncheckedRow() = runTest {
        val plan = mealPlan()
        database.mealPlanDao().upsertMealPlan(plan)
        database.shoppingListCheckDao().upsert(
            check(plan.uuid, "onion", syncState = SyncState.SYNCED).copy(checked = false, updatedAt = 111L)
        )

        database.shoppingListCheckDao().clearForPlan(plan.uuid, state = SyncState.PENDING, updatedAt = 9_999L)

        val onion = database.shoppingListCheckDao().getCheck(plan.uuid, "onion")
        assertEquals(
            "a row that was never ticked has nothing to push just because uncheck-all ran",
            SyncState.SYNCED,
            onion?.syncState,
        )
        assertEquals(111L, onion?.updatedAt)
    }

    @Test
    fun deletingTheMealPlanRow_cascadesToItsChecks() = runTest {
        val plan = mealPlan()
        database.mealPlanDao().upsertMealPlan(plan)
        database.shoppingListCheckDao().upsert(check(plan.uuid, "onion"))

        // No repository path hard-deletes a meal plan row (soft delete only sets `deletedAt`), so
        // the FK's ON DELETE CASCADE is exercised directly against the underlying table here.
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM meal_plans WHERE uuid = x'${plan.uuid.toHex()}'"
        )

        assertTrue(database.shoppingListCheckDao().observeCheckedKeys(plan.uuid).first().isEmpty())
    }

    @Test
    fun getCheck_returnsTheStoredRow() = runTest {
        val plan = mealPlan()
        database.mealPlanDao().upsertMealPlan(plan)
        database.shoppingListCheckDao().upsert(check(plan.uuid, "onion"))

        val stored = database.shoppingListCheckDao().getCheck(plan.uuid, "onion")

        assertEquals("onion", stored?.itemKey)
        assertTrue("a freshly-upserted row defaults to checked", stored?.checked == true)
    }

    @Test
    fun getCheck_returnsNullForAnUnknownItem() = runTest {
        val plan = mealPlan()
        database.mealPlanDao().upsertMealPlan(plan)

        assertEquals(null, database.shoppingListCheckDao().getCheck(plan.uuid, "onion"))
    }

    @Test
    fun getAllDirty_returnsOnlyPendingAndDeletedRows() = runTest {
        val plan = mealPlan()
        database.mealPlanDao().upsertMealPlan(plan)
        database.shoppingListCheckDao().upsert(check(plan.uuid, "onion", syncState = SyncState.PENDING))
        database.shoppingListCheckDao().upsert(check(plan.uuid, "garlic", syncState = SyncState.SYNCED))
        database.shoppingListCheckDao().upsert(check(plan.uuid, "milk", syncState = SyncState.DELETED))

        val dirtyKeys = database.shoppingListCheckDao().getAllDirty().map { it.itemKey }.toSet()

        assertEquals(setOf("onion", "milk"), dirtyKeys)
    }

    @Test
    fun updateSyncState_updatesOnlyTheTargetedRow() = runTest {
        val plan = mealPlan()
        database.mealPlanDao().upsertMealPlan(plan)
        database.shoppingListCheckDao().upsert(check(plan.uuid, "onion", syncState = SyncState.PENDING))
        database.shoppingListCheckDao().upsert(check(plan.uuid, "garlic", syncState = SyncState.PENDING))

        database.shoppingListCheckDao().updateSyncState(plan.uuid, "onion", SyncState.SYNCED, updatedAt = 5_000L)

        val onion = database.shoppingListCheckDao().getCheck(plan.uuid, "onion")
        val garlic = database.shoppingListCheckDao().getCheck(plan.uuid, "garlic")
        assertEquals(SyncState.SYNCED, onion?.syncState)
        assertEquals(5_000L, onion?.updatedAt)
        assertEquals("an untouched sibling row keeps its own state", SyncState.PENDING, garlic?.syncState)
    }

    /** Matches `UuidConverters`' big-endian most-significant/least-significant blob layout. */
    private fun UUID.toHex(): String {
        val buffer = java.nio.ByteBuffer.allocate(16)
        buffer.putLong(mostSignificantBits)
        buffer.putLong(leastSignificantBits)
        return buffer.array().joinToString("") { "%02x".format(it) }
    }
}
