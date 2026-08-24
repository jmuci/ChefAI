package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.MealPlanDayEntity
import com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeMealPlanDao : MealPlanDao {

    private val plans = mutableMapOf<UUID, MealPlanEntity>()
    private val days = mutableMapOf<UUID, MealPlanDayEntity>()
    private val plansFlow = MutableStateFlow<Map<UUID, MealPlanEntity>>(emptyMap())
    private val daysFlow = MutableStateFlow<Map<UUID, MealPlanDayEntity>>(emptyMap())

    private fun notifyChange() {
        plansFlow.value = plans.toMap()
    }

    private fun notifyDaysChange() {
        daysFlow.value = days.toMap()
    }

    override fun observeMealPlansForUser(userId: UUID): Flow<List<MealPlanEntity>> =
        plansFlow.map { map ->
            map.values.filter { it.userId == userId && it.deletedAt == null }
                .sortedByDescending { it.createdAt }
        }

    override suspend fun getMealPlanById(uuid: UUID): MealPlanEntity? = plans[uuid]

    override fun observeMealPlanById(uuid: UUID): Flow<MealPlanEntity?> =
        plansFlow.map { it[uuid] }

    override suspend fun upsertMealPlan(mealPlan: MealPlanEntity) {
        plans[mealPlan.uuid] = mealPlan
        notifyChange()
    }

    override suspend fun softDelete(uuid: UUID, deletedAt: Long) {
        plans[uuid]?.let {
            plans[uuid] = it.copy(deletedAt = deletedAt, syncState = SyncState.DELETED, updatedAt = deletedAt)
        }
        notifyChange()
    }

    override suspend fun getAllDirty(): List<MealPlanEntity> =
        plans.values.filter { it.syncState == SyncState.PENDING || it.syncState == SyncState.DELETED }

    override suspend fun updateSyncState(uuid: UUID, syncState: SyncState, updatedAt: Long) {
        plans[uuid]?.let {
            plans[uuid] = it.copy(syncState = syncState, updatedAt = updatedAt)
        }
        notifyChange()
    }

    override suspend fun reassignUserAndMarkPending(oldUserId: UUID, newUserId: UUID, updatedAt: Long) {
        val toReassign = plans.values.filter { it.userId == oldUserId }.toList()
        for (entity in toReassign) {
            plans[entity.uuid] = entity.copy(
                userId = newUserId,
                syncState = SyncState.PENDING,
                updatedAt = updatedAt
            )
        }
        notifyChange()
    }

    override suspend fun countMealPlansForUser(userId: UUID): Int =
        plans.values.count { it.userId == userId && it.deletedAt == null }

    override fun observeDaysForMealPlan(mealPlanId: UUID): Flow<List<MealPlanDayEntity>> =
        daysFlow.map { it.values.filter { day -> day.mealPlanId == mealPlanId }.sortedBy { day -> day.dayIndex } }

    override fun observeDaysForUser(userId: UUID): Flow<List<MealPlanDayEntity>> =
        combine(plansFlow, daysFlow) { plansMap, daysMap ->
            val planIds = plansMap.values
                .filter { it.userId == userId && it.deletedAt == null }
                .map { it.uuid }
                .toSet()
            daysMap.values.filter { it.mealPlanId in planIds }.sortedBy { it.dayIndex }
        }

    override suspend fun getDaysForMealPlan(mealPlanId: UUID): List<MealPlanDayEntity> =
        days.values.filter { it.mealPlanId == mealPlanId }.sortedBy { it.dayIndex }

    override fun observeDayById(dayId: UUID): Flow<MealPlanDayEntity?> =
        daysFlow.map { it[dayId] }

    override suspend fun upsertDays(days: List<MealPlanDayEntity>) {
        days.forEach { this.days[it.uuid] = it }
        notifyDaysChange()
    }

    override suspend fun deleteDaysForMealPlan(mealPlanId: UUID) {
        days.keys.removeAll(days.values.filter { it.mealPlanId == mealPlanId }.map { it.uuid }.toSet())
        notifyDaysChange()
    }

    override suspend fun setDinnerCookedAt(dayId: UUID, cookedAt: Long?) {
        days[dayId]?.let { days[dayId] = it.copy(dinnerCookedAt = cookedAt) }
        notifyDaysChange()
    }

    override suspend fun setLunchCookedAt(dayId: UUID, cookedAt: Long?) {
        days[dayId]?.let { days[dayId] = it.copy(lunchCookedAt = cookedAt) }
        notifyDaysChange()
    }
}
