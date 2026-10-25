package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.RecipeCookEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeRecipeCookEventDao : RecipeCookEventDao {

    private val events = MutableStateFlow<List<RecipeCookEventEntity>>(emptyList())

    val all: List<RecipeCookEventEntity> get() = events.value

    override suspend fun insert(event: RecipeCookEventEntity) {
        events.value = events.value + event
    }

    override suspend fun deleteForSlot(mealPlanId: UUID, dayIndex: Int, slot: String, recipeId: UUID) {
        events.value = events.value.filterNot {
            it.mealPlanId == mealPlanId && it.dayIndex == dayIndex && it.slot == slot && it.recipeId == recipeId
        }
    }

    override fun observeCookedRecipeIds(): Flow<List<UUID>> =
        events.map { list -> list.map { it.recipeId }.distinct() }
}
