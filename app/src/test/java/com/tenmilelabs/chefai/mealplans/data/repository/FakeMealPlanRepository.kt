package com.tenmilelabs.chefai.mealplans.data.repository

import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeMealPlanRepository : MealPlanRepository {

    private val plans = MutableStateFlow<List<MealPlan>>(emptyList())

    var shouldThrowOnCreate: Boolean = false
    var shouldThrowOnDelete: Boolean = false
    var shouldThrowOnObserve: Boolean = false

    override fun observeMealPlansForUser(userId: UUID): Flow<List<MealPlan>> {
        if (shouldThrowOnObserve) throw RuntimeException("Fake observe error")
        return plans.map { list -> list.filter { it.userId == userId } }
    }

    override suspend fun createMealPlan(mealPlan: MealPlan) {
        if (shouldThrowOnCreate) throw RuntimeException("Fake create error")
        plans.value = plans.value + mealPlan
    }

    override suspend fun deleteMealPlan(uuid: UUID) {
        if (shouldThrowOnDelete) throw RuntimeException("Fake delete error")
        plans.value = plans.value.filter { it.uuid != uuid }
    }

    fun emitPlans(vararg mealPlans: MealPlan) {
        plans.value = mealPlans.toList()
    }
}
