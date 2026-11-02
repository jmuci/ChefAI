package com.tenmilelabs.chefai.data.source.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.BookmarkedRecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
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
 * Instrumented test for [com.tenmilelabs.chefai.core.data.local.room.dao.BookmarkedRecipeDao]
 * against a real (in-memory) Room database. [DefaultCollectionsRepositoryTest] (unit test suite)
 * mocks this DAO's interface, so its generated SQL — including the soft-delete and account-upgrade
 * reassignment queries — was otherwise unverified.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class BookmarkedRecipeDaoTest {

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

    private lateinit var recipe: RecipeEntity

    private fun bookmark(
        userId: UUID,
        recipeId: UUID,
        updatedAt: Long = 0L,
        deletedAt: Long? = null,
        syncState: SyncState = SyncState.PENDING,
        isStarred: Boolean = false,
        isGoTo: Boolean = false,
    ) = BookmarkedRecipeEntity(
        userId = userId,
        recipeId = recipeId,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        syncState = syncState,
        isStarred = isStarred,
        isGoTo = isGoTo,
    )

    @Before
    fun createDb() = runTest {
        database = Room.inMemoryDatabaseBuilder(getApplicationContext(), ChefAIDataBase::class.java)
            .allowMainThreadQueries()
            .build()
        database.userDao().upsertUser(userA)
        database.userDao().upsertUser(userB)
        recipe = RecipeEntity(
            uuid = UuidV7Generator.newId(),
            title = "Chili",
            description = "",
            imageUrl = "",
            imageUrlThumbnail = "",
            prepTimeMinutes = 10,
            cookTimeMinutes = 20,
            servings = 4,
            creatorId = userA.uuid,
            recipeExternalUrl = null,
            updatedAt = 0L,
            deletedAt = null,
        )
        database.recipeDao().upsertRecipe(recipe)
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun observeBookmarkedRecipeIds_excludesSoftDeletedAndOtherUsersBookmarks() = runTest {
        database.bookmarkedRecipeDao().upsert(bookmark(userA.uuid, recipe.uuid))
        database.bookmarkedRecipeDao().upsert(bookmark(userB.uuid, recipe.uuid))

        val forA = database.bookmarkedRecipeDao().observeBookmarkedRecipeIds(userA.uuid).first()
        assertEquals(listOf(recipe.uuid), forA)

        database.bookmarkedRecipeDao().softDelete(userA.uuid, recipe.uuid, ts = 1_000L)

        assertTrue(database.bookmarkedRecipeDao().observeBookmarkedRecipeIds(userA.uuid).first().isEmpty())
        // Softly deleting user A's bookmark must not touch user B's.
        assertEquals(listOf(recipe.uuid), database.bookmarkedRecipeDao().observeBookmarkedRecipeIds(userB.uuid).first())
    }

    @Test
    fun upsert_onTheSameUserAndRecipe_updatesRatherThanDuplicates() = runTest {
        database.bookmarkedRecipeDao().upsert(bookmark(userA.uuid, recipe.uuid, updatedAt = 1L))
        database.bookmarkedRecipeDao().upsert(bookmark(userA.uuid, recipe.uuid, updatedAt = 2L))

        val dirty = database.bookmarkedRecipeDao().getAllDirty()
        assertEquals(1, dirty.size)
        assertEquals(2L, dirty.single().updatedAt)
    }

    @Test
    fun softDelete_setsDeletedAtAndFlipsSyncStateToDELETED() = runTest {
        database.bookmarkedRecipeDao().upsert(bookmark(userA.uuid, recipe.uuid, syncState = SyncState.SYNCED))

        database.bookmarkedRecipeDao().softDelete(userA.uuid, recipe.uuid, ts = 555L)

        val dirty = database.bookmarkedRecipeDao().getAllDirty().single()
        assertEquals(SyncState.DELETED, dirty.syncState)
        assertEquals(555L, dirty.deletedAt)
        assertEquals(555L, dirty.updatedAt)
    }

    @Test
    fun getAllDirty_excludesAlreadySyncedBookmarks() = runTest {
        val secondRecipe = recipe.copy(uuid = UuidV7Generator.newId())
        database.recipeDao().upsertRecipe(secondRecipe)
        database.bookmarkedRecipeDao().upsert(bookmark(userA.uuid, recipe.uuid, syncState = SyncState.SYNCED))
        database.bookmarkedRecipeDao().upsert(bookmark(userA.uuid, secondRecipe.uuid, syncState = SyncState.PENDING))

        val dirty = database.bookmarkedRecipeDao().getAllDirty()

        assertEquals(listOf(secondRecipe.uuid), dirty.map { it.recipeId })
    }

    @Test
    fun updateSyncState_touchesOnlyTheGivenUserRecipePair() = runTest {
        val secondRecipe = recipe.copy(uuid = UuidV7Generator.newId())
        database.recipeDao().upsertRecipe(secondRecipe)
        database.bookmarkedRecipeDao().upsert(bookmark(userA.uuid, recipe.uuid, syncState = SyncState.PENDING))
        database.bookmarkedRecipeDao().upsert(bookmark(userA.uuid, secondRecipe.uuid, syncState = SyncState.PENDING))

        database.bookmarkedRecipeDao().updateSyncState(userA.uuid, recipe.uuid, SyncState.SYNCED, updatedAt = 10L)

        val dirty = database.bookmarkedRecipeDao().getAllDirty()
        assertEquals(listOf(secondRecipe.uuid), dirty.map { it.recipeId })
    }

    @Test
    fun reassignUserAndMarkPending_movesBookmarksAndMarksThemPendingForResync() = runTest {
        database.bookmarkedRecipeDao().upsert(bookmark(userA.uuid, recipe.uuid, syncState = SyncState.SYNCED))

        database.bookmarkedRecipeDao().reassignUserAndMarkPending(
            oldUserId = userA.uuid,
            newUserId = userB.uuid,
            updatedAt = 777L,
        )

        assertTrue(database.bookmarkedRecipeDao().observeBookmarkedRecipeIds(userA.uuid).first().isEmpty())
        assertEquals(listOf(recipe.uuid), database.bookmarkedRecipeDao().observeBookmarkedRecipeIds(userB.uuid).first())
        val dirty = database.bookmarkedRecipeDao().getAllDirty().single()
        assertEquals(SyncState.PENDING, dirty.syncState)
        assertEquals(userB.uuid, dirty.userId)
    }

    @Test
    fun setStarredAndSetGoTo_updateALiveBookmarkAndMarkItPending() = runTest {
        val dao = database.bookmarkedRecipeDao()
        dao.upsert(bookmark(userA.uuid, recipe.uuid, syncState = SyncState.SYNCED))

        assertEquals(1, dao.setStarred(userA.uuid, recipe.uuid, starred = true, ts = 10L))
        assertEquals(1, dao.setGoTo(userA.uuid, recipe.uuid, goTo = true, ts = 20L))

        val row = requireNotNull(dao.getLive(userA.uuid, recipe.uuid))
        assertTrue(row.isStarred)
        assertTrue(row.isGoTo)
        assertEquals(20L, row.updatedAt)
        assertEquals(SyncState.PENDING, row.syncState)
    }

    @Test
    fun setStarredAndSetGoTo_leaveASoftDeletedOrMissingBookmarkAlone() = runTest {
        val dao = database.bookmarkedRecipeDao()
        dao.upsert(bookmark(userA.uuid, recipe.uuid, deletedAt = 5L, syncState = SyncState.DELETED))

        assertEquals(0, dao.setStarred(userA.uuid, recipe.uuid, starred = true, ts = 10L))
        assertEquals(0, dao.setGoTo(userB.uuid, recipe.uuid, goTo = true, ts = 10L))
        assertNull(dao.getLive(userA.uuid, recipe.uuid))
        assertEquals(false, dao.get(userA.uuid, recipe.uuid)?.isStarred)
    }

    @Test
    fun softDelete_clearsTheStarAndGoToMarks() = runTest {
        val dao = database.bookmarkedRecipeDao()
        dao.upsert(bookmark(userA.uuid, recipe.uuid, isStarred = true, isGoTo = true))

        dao.softDelete(userA.uuid, recipe.uuid, ts = 50L)

        val row = requireNotNull(dao.get(userA.uuid, recipe.uuid))
        assertEquals(false, row.isStarred)
        assertEquals(false, row.isGoTo)
        assertTrue(dao.observeLiveBookmarks(userA.uuid).first().isEmpty())
    }

    @Test
    fun reassignUserAndMarkPending_carriesTheStarAndGoToMarks() = runTest {
        val dao = database.bookmarkedRecipeDao()
        dao.upsert(bookmark(userA.uuid, recipe.uuid, isStarred = true, isGoTo = true))

        dao.reassignUserAndMarkPending(oldUserId = userA.uuid, newUserId = userB.uuid, updatedAt = 777L)

        val row = dao.observeLiveBookmarks(userB.uuid).first().single()
        assertTrue(row.isStarred)
        assertTrue(row.isGoTo)
    }

    @Test
    fun deletingTheBookmarkedRecipeCascadesToItsBookmarks() = runTest {
        database.bookmarkedRecipeDao().upsert(bookmark(userA.uuid, recipe.uuid))

        database.recipeDao().deleteRecipe(recipe.uuid)

        assertTrue(database.bookmarkedRecipeDao().observeBookmarkedRecipeIds(userA.uuid).first().isEmpty())
    }
}
