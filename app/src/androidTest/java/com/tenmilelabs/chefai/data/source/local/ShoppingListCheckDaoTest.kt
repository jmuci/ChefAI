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

    private fun check(mealPlanId: UUID, itemKey: String, checkedAt: Long = 1_000L) =
        ShoppingListCheckEntity(mealPlanId = mealPlanId, itemKey = itemKey, checkedAt = checkedAt)

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
    fun delete_removesOnlyThatItem() = runTest {
        val plan = mealPlan()
        database.mealPlanDao().upsertMealPlan(plan)
        database.shoppingListCheckDao().upsert(check(plan.uuid, "onion"))
        database.shoppingListCheckDao().upsert(check(plan.uuid, "garlic"))

        database.shoppingListCheckDao().delete(plan.uuid, "onion")

        assertEquals(listOf("garlic"), database.shoppingListCheckDao().observeCheckedKeys(plan.uuid).first())
    }

    @Test
    fun clearForPlan_removesEveryCheckForThatPlanOnly() = runTest {
        val planA = mealPlan()
        val planB = mealPlan()
        database.mealPlanDao().upsertMealPlan(planA)
        database.mealPlanDao().upsertMealPlan(planB)
        database.shoppingListCheckDao().upsert(check(planA.uuid, "onion"))
        database.shoppingListCheckDao().upsert(check(planA.uuid, "garlic"))
        database.shoppingListCheckDao().upsert(check(planB.uuid, "milk"))

        database.shoppingListCheckDao().clearForPlan(planA.uuid)

        assertTrue(database.shoppingListCheckDao().observeCheckedKeys(planA.uuid).first().isEmpty())
        assertEquals(listOf("milk"), database.shoppingListCheckDao().observeCheckedKeys(planB.uuid).first())
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

    /** Matches `UuidConverters`' big-endian most-significant/least-significant blob layout. */
    private fun UUID.toHex(): String {
        val buffer = java.nio.ByteBuffer.allocate(16)
        buffer.putLong(mostSignificantBits)
        buffer.putLong(leastSignificantBits)
        return buffer.array().joinToString("") { "%02x".format(it) }
    }
}
