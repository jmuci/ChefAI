package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.SavedMealPlanEntity
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeSavedMealPlanDao : SavedMealPlanDao {

    private val rows = MutableStateFlow<Map<UUID, SavedMealPlanEntity>>(emptyMap())

    /** Every stored row, tombstones included. */
    val all: List<SavedMealPlanEntity> get() = rows.value.values.toList()

    override fun observeForUser(userId: UUID): Flow<List<SavedMealPlanEntity>> =
        rows.map { map ->
            map.values.filter { it.userId == userId && it.deletedAt == null }.sortedByDescending { it.createdAt }
        }

    override suspend fun getById(uuid: UUID): SavedMealPlanEntity? = rows.value[uuid]

    override suspend fun upsert(plan: SavedMealPlanEntity) {
        rows.value = rows.value + (plan.uuid to plan)
    }

    override suspend fun softDelete(uuid: UUID, deletedAt: Long) {
        val existing = rows.value[uuid] ?: return
        rows.value = rows.value + (
            uuid to existing.copy(deletedAt = deletedAt, updatedAt = deletedAt, syncState = SyncState.PENDING)
            )
    }

    override suspend fun getAllDirty(): List<SavedMealPlanEntity> =
        rows.value.values.filter { it.syncState == SyncState.PENDING || it.syncState == SyncState.DELETED }

    override suspend fun updateSyncState(
        uuid: UUID, state: SyncState, updatedAt: Long, expectedUpdatedAt: Long?,
    ): Int {
        val existing = rows.value[uuid] ?: return 0
        if (expectedUpdatedAt != null && existing.updatedAt != expectedUpdatedAt) return 0
        rows.value = rows.value + (uuid to existing.copy(syncState = state, updatedAt = updatedAt))
        return 1
    }
}
