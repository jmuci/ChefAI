package com.tenmilelabs.chefai.mealplans.data.repository

import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
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
    var shouldFailGeneration: Boolean = false
    var generationRequestedIds: MutableList<UUID> = mutableListOf()
    var locallyGeneratedIds: MutableList<UUID> = mutableListOf()

    /** Timestamp [setMealCooked] writes, so tests can assert on a known value. */
    var cookedAtToApply: Long = 1_000L

    /** Days [requestGeneration] pretends the server produced, applied on a successful call. */
    var daysFromServer: List<MealPlanDay> = emptyList()

    override fun observeMealPlan(uuid: UUID): Flow<MealPlan?> {
        return plans.map { list -> list.find { it.uuid == uuid } }
    }

    override fun observeMealPlanDay(dayId: UUID): Flow<MealPlanDay?> {
        return plans.map { list -> list.flatMap { it.days }.find { it.uuid == dayId } }
    }

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

    override suspend fun requestGeneration(planId: UUID): Result<Unit> {
        generationRequestedIds.add(planId)
        if (shouldFailGeneration) return Result.failure(RuntimeException("Fake generation error"))
        if (daysFromServer.isNotEmpty()) {
            plans.value = plans.value.map { plan ->
                if (plan.uuid == planId) {
                    plan.copy(days = daysFromServer, status = MealPlanStatus.READY)
                } else {
                    plan
                }
            }
        }
        return Result.success(Unit)
    }

    override suspend fun setMealCooked(dayId: UUID, slot: MealSlot, cooked: Boolean) {
        val cookedAt = if (cooked) cookedAtToApply else null
        plans.value = plans.value.map { plan ->
            plan.copy(
                days = plan.days.map { day ->
                    if (day.uuid != dayId) {
                        day
                    } else {
                        when (slot) {
                            MealSlot.LUNCH -> day.copy(lunchCookedAt = cookedAt)
                            MealSlot.DINNER -> day.copy(dinnerCookedAt = cookedAt)
                        }
                    }
                }
            )
        }
    }

    override suspend fun saveLocallyGeneratedDays(planId: UUID, days: List<MealPlanDay>) {
        locallyGeneratedIds.add(planId)
        plans.value = plans.value.map { plan ->
            if (plan.uuid == planId) {
                plan.copy(days = days, status = MealPlanStatus.READY)
            } else {
                plan
            }
        }
    }

    fun emitPlans(vararg mealPlans: MealPlan) {
        plans.value = mealPlans.toList()
    }
}
