package com.tenmilelabs.chefai.data.source.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.SavedMealPlanEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
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
 * Instrumented test for [com.tenmilelabs.chefai.core.data.local.room.dao.SavedMealPlanDao] against
 * a real (in-memory) Room database.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class SavedMealPlanDaoTest {

    private lateinit var database: ChefAIDataBase

    private val user = UserEntity(
        uuid = UuidV7Generator.newId(),
        displayName = "Chef",
        email = "chef@test.com",
        avatarUrl = "",
        updatedAt = 0L,
        deletedAt = null,
    )

    private fun saved(
        title: String = "Week",
        userId: UUID = user.uuid,
        createdAt: Long = 1_000L,
    ) = SavedMealPlanEntity(
        uuid = UuidV7Generator.newId(),
        userId = userId,
        title = title,
        preferencesJson = "{}",
        daysJson = "[]",
        createdAt = createdAt,
        updatedAt = createdAt,
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
    fun upsert_thenGetById_roundTripsEveryColumn() = runTest {
        val dao = database.savedMealPlanDao()
        val plan = saved().copy(sourceMealPlanId = UUID.randomUUID(), heroImagePath = "/files/x", daysJson = "[1]")

        dao.upsert(plan)

        assertEquals(plan, dao.getById(plan.uuid))
    }

    @Test
    fun observeForUser_returnsLivePlansNewestFirstAndOnlyForThatUser() = runTest {
        val dao = database.savedMealPlanDao()
        val other = UserEntity(UuidV7Generator.newId(), "Other", "o@test.com", "", 0L, null)
        database.userDao().upsertUser(other)
        val older = saved("Older", createdAt = 1_000L)
        val newer = saved("Newer", createdAt = 2_000L)
        listOf(older, newer, saved("Theirs", userId = other.uuid)).forEach { dao.upsert(it) }

        assertEquals(listOf(newer.uuid, older.uuid), dao.observeForUser(user.uuid).first().map { it.uuid })
    }

    @Test
    fun softDelete_hidesThePlanTombstonesItAndQueuesItForPush() = runTest {
        val dao = database.savedMealPlanDao()
        val plan = saved().copy(syncState = SyncState.SYNCED)
        dao.upsert(plan)

        dao.softDelete(plan.uuid, deletedAt = 5_000L)

        assertTrue(dao.observeForUser(user.uuid).first().isEmpty())
        val row = requireNotNull(dao.getById(plan.uuid))
        assertEquals(5_000L, row.deletedAt)
        assertEquals(5_000L, row.updatedAt)
        assertEquals(SyncState.PENDING, row.syncState)
        assertEquals(listOf(plan.uuid), dao.getAllDirty().map { it.uuid })
    }

    @Test
    fun getAllDirty_returnsPendingAndDeletedButNotSyncedOrCancelled() = runTest {
        val dao = database.savedMealPlanDao()
        val pending = saved("p")
        val deleted = saved("d").copy(syncState = SyncState.DELETED)
        listOf(
            pending,
            deleted,
            saved("s").copy(syncState = SyncState.SYNCED),
            saved("c").copy(syncState = SyncState.CANCELLED),
        ).forEach { dao.upsert(it) }

        assertEquals(setOf(pending.uuid, deleted.uuid), dao.getAllDirty().map { it.uuid }.toSet())
    }

    @Test
    fun updateSyncState_marksASyncedRowWhenTheVersionMatches() = runTest {
        val dao = database.savedMealPlanDao()
        val plan = saved().copy(updatedAt = 1_000L)
        dao.upsert(plan)

        val updated = dao.updateSyncState(plan.uuid, SyncState.SYNCED, updatedAt = 9_000L, expectedUpdatedAt = 1_000L)

        assertEquals(1, updated)
        val row = requireNotNull(dao.getById(plan.uuid))
        assertEquals(SyncState.SYNCED, row.syncState)
        assertEquals(9_000L, row.updatedAt)
    }

    @Test
    fun updateSyncState_leavesARowEditedSincePushPending() = runTest {
        val dao = database.savedMealPlanDao()
        val plan = saved().copy(updatedAt = 2_000L)
        dao.upsert(plan)

        val updated = dao.updateSyncState(plan.uuid, SyncState.SYNCED, updatedAt = 9_000L, expectedUpdatedAt = 1_000L)

        assertEquals(0, updated)
        val row = requireNotNull(dao.getById(plan.uuid))
        assertEquals(SyncState.PENDING, row.syncState)
        assertEquals(2_000L, row.updatedAt)
    }

    @Test
    fun deletingTheOwningUser_cascadesToTheirSavedPlans() = runTest {
        val dao = database.savedMealPlanDao()
        val plan = saved()
        dao.upsert(plan)
        assertNotNull(dao.getById(plan.uuid))

        database.userDao().deleteAllUsers()

        assertNull(dao.getById(plan.uuid))
    }
}
