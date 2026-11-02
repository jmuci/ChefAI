package com.tenmilelabs.chefai.collections.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.collections.domain.model.BookmarkUpdate
import com.tenmilelabs.chefai.collections.domain.model.RecipeBookmark
import com.tenmilelabs.chefai.core.data.local.room.dao.BookmarkedRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeBookmarkedRecipeDao
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

class DefaultCollectionsRepositoryTest {

    private val userId = UUID.randomUUID()
    private val recipeId = UUID.randomUUID()

    // Star / go-to tests run against the JVM fake DAO, so writes and reads see the same rows.
    private val fakeDao = FakeBookmarkedRecipeDao()
    private val fakeSyncScheduler: SyncScheduler = mockk(relaxed = true)
    private val fakeBacked = DefaultCollectionsRepository(fakeDao, fakeSyncScheduler)

    private suspend fun row() = fakeDao.get(userId, recipeId)

    @Test
    fun `observeBookmarkedRecipeIds de-duplicates the dao's list into a set`() = runTest {
        val duplicateId = UUID.randomUUID()
        val dao: BookmarkedRecipeDao = mockk {
            every { observeBookmarkedRecipeIds(userId) } returns flowOf(listOf(duplicateId, duplicateId, recipeId))
        }
        val repository = DefaultCollectionsRepository(dao, mockk(relaxed = true))

        repository.observeBookmarkedRecipeIds(userId).test {
            assertThat(awaitItem()).containsExactly(duplicateId, recipeId)
            awaitComplete()
        }
    }

    @Test
    fun `removeBookmark soft-deletes via the dao and requests a bookmark sync`() = runTest {
        val dao: BookmarkedRecipeDao = mockk(relaxed = true)
        val syncScheduler: SyncScheduler = mockk(relaxed = true)
        val repository = DefaultCollectionsRepository(dao, syncScheduler)

        repository.removeBookmark(userId, recipeId)

        coVerify(exactly = 1) { dao.softDelete(userId, recipeId, any()) }
        verify(exactly = 1) { syncScheduler.requestBookmarkSync() }
    }

    @Test
    fun `addBookmark upserts a live, unmarked, pending entity and requests a bookmark sync`() = runTest {
        fakeBacked.addBookmark(userId, recipeId)

        val saved = requireNotNull(row())
        assertThat(saved.deletedAt).isNull()
        assertThat(saved.isStarred).isFalse()
        assertThat(saved.isGoTo).isFalse()
        assertThat(saved.syncState).isEqualTo(SyncState.PENDING)
        verify(exactly = 1) { fakeSyncScheduler.requestBookmarkSync() }
    }

    @Test
    fun `Bookmark on an already-bookmarked recipe keeps its marks and does not resync`() = runTest {
        fakeBacked.update(userId, recipeId, BookmarkUpdate.Star)
        fakeBacked.update(userId, recipeId, BookmarkUpdate.SetGoTo(true))

        fakeBacked.update(userId, recipeId, BookmarkUpdate.Bookmark)

        assertThat(requireNotNull(row()).isStarred).isTrue()
        assertThat(requireNotNull(row()).isGoTo).isTrue()
        verify(exactly = 2) { fakeSyncScheduler.requestBookmarkSync() }
    }

    @Test
    fun `Star on an unbookmarked recipe bookmarks and stars it`() = runTest {
        fakeBacked.update(userId, recipeId, BookmarkUpdate.Star)

        fakeBacked.observeBookmarks(userId).test {
            assertThat(awaitItem()).containsExactly(recipeId, RecipeBookmark(recipeId, isStarred = true))
            cancelAndIgnoreRemainingEvents()
        }
        verify(exactly = 1) { fakeSyncScheduler.requestBookmarkSync() }
    }

    @Test
    fun `Star on a bookmarked recipe marks the row pending`() = runTest {
        fakeBacked.addBookmark(userId, recipeId)
        fakeDao.updateSyncState(userId, recipeId, SyncState.SYNCED, updatedAt = 1L)

        fakeBacked.update(userId, recipeId, BookmarkUpdate.Star)

        assertThat(requireNotNull(row()).isStarred).isTrue()
        assertThat(requireNotNull(row()).syncState).isEqualTo(SyncState.PENDING)
    }

    @Test
    fun `Unstar keeps the bookmark`() = runTest {
        fakeBacked.update(userId, recipeId, BookmarkUpdate.Star)

        fakeBacked.update(userId, recipeId, BookmarkUpdate.Unstar)

        val saved = requireNotNull(row())
        assertThat(saved.deletedAt).isNull()
        assertThat(saved.isStarred).isFalse()
    }

    @Test
    fun `Remove clears the star and the go-to mark with the bookmark`() = runTest {
        fakeBacked.update(userId, recipeId, BookmarkUpdate.Star)
        fakeBacked.update(userId, recipeId, BookmarkUpdate.SetGoTo(true))

        fakeBacked.update(userId, recipeId, BookmarkUpdate.Remove)

        val saved = requireNotNull(row())
        assertThat(saved.deletedAt).isNotNull()
        assertThat(saved.isStarred).isFalse()
        assertThat(saved.isGoTo).isFalse()
        fakeBacked.observeBookmarks(userId).test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-bookmarking a removed recipe starts with no marks`() = runTest {
        fakeBacked.update(userId, recipeId, BookmarkUpdate.Star)
        fakeBacked.update(userId, recipeId, BookmarkUpdate.Remove)

        fakeBacked.update(userId, recipeId, BookmarkUpdate.Bookmark)

        assertThat(fakeDao.getLive(userId, recipeId)?.isStarred).isFalse()
    }

    @Test
    fun `SetGoTo is ignored when the recipe isn't bookmarked`() = runTest {
        fakeBacked.update(userId, recipeId, BookmarkUpdate.SetGoTo(true))

        assertThat(row()).isNull()
        verify(exactly = 0) { fakeSyncScheduler.requestBookmarkSync() }
    }

    @Test
    fun `SetGoTo toggles the go-to mark on a bookmark without touching its star`() = runTest {
        fakeBacked.addBookmark(userId, recipeId)

        fakeBacked.update(userId, recipeId, BookmarkUpdate.SetGoTo(true))
        assertThat(requireNotNull(row()).isGoTo).isTrue()

        fakeBacked.update(userId, recipeId, BookmarkUpdate.SetGoTo(false))
        assertThat(requireNotNull(row()).isGoTo).isFalse()
        assertThat(requireNotNull(row()).isStarred).isFalse()
    }
}
