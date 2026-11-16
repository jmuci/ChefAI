package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.CustomShoppingItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeCustomShoppingItemDao : CustomShoppingItemDao {

    private val items = MutableStateFlow<List<CustomShoppingItemEntity>>(emptyList())

    override fun observeForPlan(mealPlanId: UUID): Flow<List<CustomShoppingItemEntity>> =
        items.map { all -> all.filter { it.mealPlanId == mealPlanId && !it.removed }.sortedBy { it.createdAt } }

    override suspend fun insert(item: CustomShoppingItemEntity) {
        items.value += item
    }

    override suspend fun setChecked(uuid: UUID, checked: Boolean) {
        items.value = items.value.map { if (it.uuid == uuid) it.copy(checked = checked) else it }
    }

    override suspend fun uncheckAllForPlan(mealPlanId: UUID) {
        items.value = items.value.map { if (it.mealPlanId == mealPlanId) it.copy(checked = false) else it }
    }

    override suspend fun rename(uuid: UUID, name: String) {
        items.value = items.value.map { if (it.uuid == uuid) it.copy(name = name) else it }
    }

    override suspend fun setRemoved(uuid: UUID, removed: Boolean) {
        items.value = items.value.map { if (it.uuid == uuid) it.copy(removed = removed) else it }
    }
}
