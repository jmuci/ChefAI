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

    override suspend fun updateSyncState(userId: UUID, recipeId: UUID, state: SyncState, updatedAt: Long) {
        val key = userId to recipeId
        store[key]?.let {
            store[key] = it.copy(syncState = state, updatedAt = updatedAt)
        }
        notifyChange()
    }

    override suspend fun reassignUserAndMarkPending(oldUserId: UUID, newUserId: UUID, updatedAt: Long) {
        val toReassign = store.entries.filter { it.key.first == oldUserId }.toList()
        for ((key, entity) in toReassign) {
            store.remove(key)
            store[newUserId to key.second] = entity.copy(
                userId = newUserId,
                syncState = SyncState.PENDING,
                updatedAt = updatedAt
            )
        }
        notifyChange()
    }
}
