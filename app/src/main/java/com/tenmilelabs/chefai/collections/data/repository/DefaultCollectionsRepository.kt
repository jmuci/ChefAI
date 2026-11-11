package com.tenmilelabs.chefai.collections.data.repository

import com.tenmilelabs.chefai.collections.domain.model.BookmarkUpdate
import com.tenmilelabs.chefai.collections.domain.model.RecipeBookmark
import com.tenmilelabs.chefai.collections.domain.repository.CollectionsRepository
import com.tenmilelabs.chefai.core.data.local.room.BookmarkedRecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.BookmarkedRecipeDao
import com.tenmilelabs.chefai.core.data.local.util.nextUpdatedAt
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

    override fun observeBookmarks(userId: UUID): Flow<Map<UUID, RecipeBookmark>> =
        dao.observeLiveBookmarks(userId).map { rows ->
            rows.associate { it.recipeId to RecipeBookmark(it.recipeId, it.isStarred, it.isGoTo) }
        }

    override suspend fun update(userId: UUID, recipeId: UUID, update: BookmarkUpdate) {
        val now = System.currentTimeMillis()
        val changed = when (update) {
            BookmarkUpdate.Bookmark -> upsertIfMissing(userId, recipeId, starred = false, now)
            BookmarkUpdate.Star ->
                dao.setStarred(userId, recipeId, starred = true, ts = now) > 0 ||
                    upsertIfMissing(userId, recipeId, starred = true, now)
            BookmarkUpdate.Unstar -> dao.setStarred(userId, recipeId, starred = false, ts = now) > 0
            BookmarkUpdate.Remove -> {
                dao.softDelete(userId, recipeId, now)
                true
            }
            is BookmarkUpdate.SetGoTo -> dao.setGoTo(userId, recipeId, update.goTo, now) > 0
        }
        if (changed) syncScheduler.requestBookmarkSync()
    }

    /**
     * Writes a fresh bookmark unless a live one exists — re-bookmarking must not wipe the marks an
     * existing bookmark already carries. A soft-deleted row is revived with cleared marks.
     */
    private suspend fun upsertIfMissing(userId: UUID, recipeId: UUID, starred: Boolean, now: Long): Boolean {
        if (dao.getLive(userId, recipeId) != null) return false
        dao.upsert(
            BookmarkedRecipeEntity(
                userId = userId,
                recipeId = recipeId,
                // A revived tombstone's stamp must sort after the deletion it undoes.
                updatedAt = nextUpdatedAt(dao.get(userId, recipeId)?.updatedAt, now),
                deletedAt = null,
                isStarred = starred,
            )
        )
        return true
    }
}
