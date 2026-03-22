package com.tenmilelabs.chefai.collections.data.repository

import com.tenmilelabs.chefai.collections.domain.repository.CollectionsRepository
import com.tenmilelabs.chefai.core.data.local.room.BookmarkedRecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.BookmarkedRecipeDao
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCollectionsRepository @Inject constructor(
    private val dao: BookmarkedRecipeDao,
    private val syncScheduler: SyncScheduler
) : CollectionsRepository {

    override fun observeBookmarkedRecipeIds(userId: UUID): Flow<Set<UUID>> =
        dao.observeBookmarkedRecipeIds(userId).map { it.toSet() }

    override suspend fun addBookmark(userId: UUID, recipeId: UUID) {
        dao.upsert(
            BookmarkedRecipeEntity(
                userId = userId,
                recipeId = recipeId,
                updatedAt = System.currentTimeMillis(),
                deletedAt = null
            )
        )
        syncScheduler.requestBookmarkSync()
    }

    override suspend fun removeBookmark(userId: UUID, recipeId: UUID) {
        dao.softDelete(userId, recipeId, System.currentTimeMillis())
        syncScheduler.requestBookmarkSync()
    }
}
