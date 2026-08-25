package com.tenmilelabs.chefai.collections.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.room.BookmarkedRecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.BookmarkedRecipeDao
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

class DefaultCollectionsRepositoryTest {

    private val userId = UUID.randomUUID()
    private val recipeId = UUID.randomUUID()

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
    fun `addBookmark upserts a live entity and requests a bookmark sync`() = runTest {
        val dao: BookmarkedRecipeDao = mockk(relaxed = true)
        val syncScheduler: SyncScheduler = mockk(relaxed = true)
        val repository = DefaultCollectionsRepository(dao, syncScheduler)

        repository.addBookmark(userId, recipeId)

        val saved = slot<BookmarkedRecipeEntity>()
        coVerify(exactly = 1) { dao.upsert(capture(saved)) }
        assertThat(saved.captured.userId).isEqualTo(userId)
        assertThat(saved.captured.recipeId).isEqualTo(recipeId)
        assertThat(saved.captured.deletedAt).isNull()
        verify(exactly = 1) { syncScheduler.requestBookmarkSync() }
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
}
