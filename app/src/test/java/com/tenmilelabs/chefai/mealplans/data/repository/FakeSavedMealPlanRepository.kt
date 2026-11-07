package com.tenmilelabs.chefai.mealplans.data.repository

import com.tenmilelabs.chefai.mealplans.domain.model.SavedMealPlan
import com.tenmilelabs.chefai.mealplans.domain.repository.SavedMealPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** In-memory [SavedMealPlanRepository]: contract-shaped, newest first, deletes drop the row. */
class FakeSavedMealPlanRepository : SavedMealPlanRepository {

    private val plans = MutableStateFlow<List<SavedMealPlan>>(emptyList())

    var shouldThrowOnDelete: Boolean = false
    val deletedIds: MutableList<UUID> = mutableListOf()

    /** Replaces every saved plan, as if Room had emitted a new list. */
    fun emitPlans(vararg savedPlans: SavedMealPlan) {
        plans.value = savedPlans.toList()
    }

    override fun observeSavedPlans(userId: UUID): Flow<List<SavedMealPlan>> =
        plans.map { list -> list.filter { it.userId == userId }.sortedByDescending { it.createdAt } }

    override suspend fun getSavedPlan(uuid: UUID): SavedMealPlan? = plans.value.find { it.uuid == uuid }

    override suspend fun saveFromMealPlan(
        savedPlanId: UUID,
        userId: UUID,
        mealPlanId: UUID,
        title: String,
        heroImagePath: String?,
    ): Result<SavedMealPlan> = Result.failure(UnsupportedOperationException("Not needed by these tests"))

    override suspend fun delete(uuid: UUID) {
        if (shouldThrowOnDelete) throw RuntimeException("Fake delete error")
        deletedIds += uuid
        plans.value = plans.value.filterNot { it.uuid == uuid }
    }
}
