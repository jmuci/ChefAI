package com.tenmilelabs.chefai.mealplans.data.repository

import com.tenmilelabs.chefai.core.data.local.room.dao.MealPlanDao
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.mealplans.data.mapper.toDomain
import com.tenmilelabs.chefai.mealplans.data.mapper.toEntity
import com.tenmilelabs.chefai.mealplans.data.network.MealPlanNetworkDataSource
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultMealPlanRepository @Inject constructor(
    private val mealPlanDao: MealPlanDao,
    private val mealPlanNetworkDataSource: MealPlanNetworkDataSource,
    private val syncScheduler: SyncScheduler,
) : MealPlanRepository {

    // Days are combined from their own Room flow rather than read once per plan emission: marking
    // a meal cooked only touches meal_plan_days, which would not re-trigger a meal_plans query.
    override fun observeMealPlan(uuid: UUID): Flow<MealPlan?> =
        combine(
            mealPlanDao.observeMealPlanById(uuid),
            mealPlanDao.observeDaysForMealPlan(uuid),
        ) { entity, days -> entity?.toDomain(days) }

    override fun observeMealPlansForUser(userId: UUID): Flow<List<MealPlan>> =
        combine(
            mealPlanDao.observeMealPlansForUser(userId),
            mealPlanDao.observeDaysForUser(userId),
        ) { entities, allDays ->
            val daysByPlan = allDays.groupBy { it.mealPlanId }
            entities.map { entity -> entity.toDomain(daysByPlan[entity.uuid].orEmpty()) }
        }

    override suspend fun createMealPlan(mealPlan: MealPlan) {
        mealPlanDao.upsertMealPlan(mealPlan.toEntity())
        if (mealPlan.days.isNotEmpty()) {
            mealPlanDao.upsertDays(mealPlan.days.map { it.toEntity(mealPlan.uuid) })
        }
        syncScheduler.requestMutationSync()
    }

    override suspend fun deleteMealPlan(uuid: UUID) {
        mealPlanDao.softDelete(uuid, System.currentTimeMillis())
        syncScheduler.requestMutationSync()
    }

    override suspend fun requestGeneration(planId: UUID): Result<Unit> {
        return try {
            val response = mealPlanNetworkDataSource.generateMealPlan(planId.toString())
            // Immediately update local status to GENERATING so UI reflects it
            mealPlanDao.getMealPlanById(planId)?.let { existing ->
                mealPlanDao.upsertMealPlan(
                    existing.copy(
                        status = response.status,
                        syncState = SyncState.SYNCED
                    )
                )
            }
            Timber.d("requestGeneration: plan $planId accepted, status=${response.status}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "requestGeneration: failed for plan $planId")
            Result.failure(e)
        }
    }

    override suspend fun setMealCooked(dayId: UUID, slot: MealSlot, cooked: Boolean) {
        val cookedAt = if (cooked) System.currentTimeMillis() else null
        when (slot) {
            MealSlot.LUNCH -> mealPlanDao.setLunchCookedAt(dayId, cookedAt)
            MealSlot.DINNER -> mealPlanDao.setDinnerCookedAt(dayId, cookedAt)
        }
    }

    override suspend fun saveLocallyGeneratedDays(planId: UUID, days: List<MealPlanDay>) {
        val existing = mealPlanDao.getMealPlanById(planId) ?: run {
            Timber.w("saveLocallyGeneratedDays: plan $planId not found")
            return
        }
        mealPlanDao.deleteDaysForMealPlan(planId)
        mealPlanDao.upsertDays(days.map { it.toEntity(planId) })
        mealPlanDao.upsertMealPlan(
            existing.copy(
                status = MealPlanStatus.READY.name,
                syncState = SyncState.PENDING,
                updatedAt = System.currentTimeMillis(),
            )
        )
        Timber.d("saveLocallyGeneratedDays: filled plan $planId with ${days.size} day(s) on device")
        syncScheduler.requestMutationSync()
    }
}
