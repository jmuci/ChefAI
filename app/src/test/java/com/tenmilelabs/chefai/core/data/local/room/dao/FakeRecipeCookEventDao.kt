package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.RecipeCookEventEntity
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeRecipeCookEventDao : RecipeCookEventDao {

    private val events = MutableStateFlow<List<RecipeCookEventEntity>>(emptyList())

    val all: List<RecipeCookEventEntity> get() = events.value

    private fun update(uuid: UUID, transform: (RecipeCookEventEntity) -> RecipeCookEventEntity) {
        events.value = events.value.map { if (it.uuid == uuid) transform(it) else it }
    }

    override suspend fun insert(event: RecipeCookEventEntity) {
        events.value = events.value.filterNot { it.uuid == event.uuid } + event
    }

    override suspend fun getById(uuid: UUID): RecipeCookEventEntity? = events.value.firstOrNull { it.uuid == uuid }

    private fun matchesSlot(e: RecipeCookEventEntity, mealPlanId: UUID, dayIndex: Int, slot: String, recipeId: UUID) =
        e.mealPlanId == mealPlanId && e.dayIndex == dayIndex && e.slot == slot && e.recipeId == recipeId

    override suspend fun getLiveForSlot(
        mealPlanId: UUID, dayIndex: Int, slot: String, recipeId: UUID,
    ): RecipeCookEventEntity? = events.value.firstOrNull {
        matchesSlot(it, mealPlanId, dayIndex, slot, recipeId) && it.deletedAt == null
    }

    override suspend fun tombstoneForSlot(
        mealPlanId: UUID, dayIndex: Int, slot: String, recipeId: UUID, deletedAt: Long,
    ) {
        events.value = events.value.map {
            if (matchesSlot(it, mealPlanId, dayIndex, slot, recipeId) && it.deletedAt == null) {
                it.copy(deletedAt = deletedAt, updatedAt = deletedAt, syncState = SyncState.PENDING)
            } else it
        }
    }

    override fun observeCookedRecipeIds(): Flow<List<UUID>> =
        events.map { list -> list.filter { it.deletedAt == null }.map { it.recipeId }.distinct() }

    override suspend fun getAllDirty(): List<RecipeCookEventEntity> =
        events.value.filter { it.syncState == SyncState.PENDING || it.syncState == SyncState.DELETED }

    override suspend fun updateSyncState(
        uuid: UUID, state: SyncState, updatedAt: Long, expectedUpdatedAt: Long?,
    ): Int {
        val current = getById(uuid) ?: return 0
        if (expectedUpdatedAt != null && current.updatedAt != expectedUpdatedAt) return 0
        update(uuid) { it.copy(syncState = state, updatedAt = updatedAt) }
        return 1
    }

    override suspend fun applyTombstone(uuid: UUID, deletedAt: Long, updatedAt: Long) {
        update(uuid) { it.copy(deletedAt = deletedAt, updatedAt = updatedAt, syncState = SyncState.SYNCED) }
    }

    override suspend fun reassignId(oldUuid: UUID, newUuid: UUID) {
        update(oldUuid) { it.copy(uuid = newUuid, syncState = SyncState.PENDING) }
    }

    override suspend fun setSyncState(uuid: UUID, state: SyncState) {
        update(uuid) { it.copy(syncState = state) }
    }

    override suspend fun delete(uuid: UUID) {
        events.value = events.value.filterNot { it.uuid == uuid }
    }
}
