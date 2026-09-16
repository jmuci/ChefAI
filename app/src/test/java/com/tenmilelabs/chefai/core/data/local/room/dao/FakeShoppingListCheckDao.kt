package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.ShoppingListCheckEntity
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeShoppingListCheckDao : ShoppingListCheckDao {

    private val checks = mutableMapOf<Pair<UUID, String>, ShoppingListCheckEntity>()
    private val trigger = MutableStateFlow(0)

    private fun notifyChange() {
        trigger.value++
    }

    override fun observeCheckedKeys(mealPlanId: UUID): Flow<List<String>> =
        trigger.map {
            checks.values
                .filter { it.mealPlanId == mealPlanId && it.checked && it.deletedAt == null }
                .map { it.itemKey }
        }

    override suspend fun upsert(check: ShoppingListCheckEntity) {
        checks[check.mealPlanId to check.itemKey] = check
        notifyChange()
    }

    override suspend fun delete(mealPlanId: UUID, itemKey: String) {
        checks.remove(mealPlanId to itemKey)
        notifyChange()
    }

    override suspend fun clearForPlan(mealPlanId: UUID) {
        checks.keys.filter { it.first == mealPlanId }.forEach { checks.remove(it) }
        notifyChange()
    }

    override suspend fun getCheck(mealPlanId: UUID, itemKey: String): ShoppingListCheckEntity? =
        checks[mealPlanId to itemKey]

    override suspend fun getAllDirty(): List<ShoppingListCheckEntity> =
        checks.values.filter { it.syncState == SyncState.PENDING || it.syncState == SyncState.DELETED }

    override suspend fun updateSyncState(mealPlanId: UUID, itemKey: String, state: SyncState, updatedAt: Long) {
        val key = mealPlanId to itemKey
        val existing = checks[key] ?: return
        checks[key] = existing.copy(syncState = state, updatedAt = updatedAt)
        notifyChange()
    }
}
