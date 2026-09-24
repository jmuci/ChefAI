package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.BookmarkedRecipeEntity
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeBookmarkedRecipeDao : BookmarkedRecipeDao {

    private val store = mutableMapOf<Pair<UUID, UUID>, BookmarkedRecipeEntity>()
    private val storeFlow = MutableStateFlow<Map<Pair<UUID, UUID>, BookmarkedRecipeEntity>>(emptyMap())

    private fun notifyChange() {
        storeFlow.value = store.toMap()
    }

    override fun observeBookmarkedRecipeIds(userId: UUID): Flow<List<UUID>> =
        storeFlow.map { map ->
            map.values.filter { it.userId == userId && it.deletedAt == null }.map { it.recipeId }
        }

    override suspend fun upsert(entity: BookmarkedRecipeEntity) {
        store[entity.userId to entity.recipeId] = entity
        notifyChange()
    }

    override suspend fun softDelete(userId: UUID, recipeId: UUID, ts: Long) {
        val key = userId to recipeId
        store[key]?.let {
            store[key] = it.copy(deletedAt = ts, syncState = SyncState.DELETED, updatedAt = ts)
        }
        notifyChange()
    }

    override suspend fun getAllDirty(): List<BookmarkedRecipeEntity> =
        store.values.filter { it.syncState == SyncState.PENDING || it.syncState == SyncState.DELETED }

    override suspend fun updateSyncState(userId: UUID, recipeId: UUID, state: SyncState, updatedAt: Long, expectedUpdatedAt: Long?): Int {
        val key = userId to recipeId
        val existing = store[key] ?: return 0
        if (expectedUpdatedAt != null && existing.updatedAt != expectedUpdatedAt) return 0
        store[key] = existing.copy(syncState = state, updatedAt = updatedAt)
        notifyChange()
        return 1
    }

    override suspend fun copyLiveBookmarksToUser(oldUserId: UUID, newUserId: UUID, updatedAt: Long) {
        store.entries.filter { it.key.first == oldUserId && it.value.deletedAt == null }.toList()
            .forEach { (key, entity) ->
                store[newUserId to key.second] = entity.copy(
                    userId = newUserId,
                    deletedAt = null,
                    syncState = SyncState.PENDING,
                    updatedAt = updatedAt
                )
            }
        notifyChange()
    }

    override suspend fun countLiveForUser(userId: UUID): Int =
        store.count { it.key.first == userId && it.value.deletedAt == null }

    override suspend fun deleteAllForUser(userId: UUID) {
        store.keys.filter { it.first == userId }.toList().forEach { store.remove(it) }
        notifyChange()
    }
}
