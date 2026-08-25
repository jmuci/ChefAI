package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.ShoppingListCheckEntity
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
        trigger.map { checks.keys.filter { it.first == mealPlanId }.map { it.second } }

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
}
