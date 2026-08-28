package com.tenmilelabs.chefai.mealplans.data.repository

import com.tenmilelabs.chefai.core.data.local.room.carryForwardCookedMarks
import com.tenmilelabs.chefai.core.data.local.room.TransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.MealPlanDao
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.SyncOrchestrator
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.mealplans.data.mapper.toDomain
import com.tenmilelabs.chefai.mealplans.data.mapper.toEntity
import com.tenmilelabs.chefai.mealplans.data.mapper.toJson
import com.tenmilelabs.chefai.mealplans.data.network.GenerateStatelessResult
import com.tenmilelabs.chefai.mealplans.data.network.MealPlanNetworkDataSource
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultMealPlanRepository @Inject constructor(
    private val mealPlanDao: MealPlanDao,
    private val mealPlanNetworkDataSource: MealPlanNetworkDataSource,
    private val syncScheduler: SyncScheduler,
    private val syncOrchestrator: SyncOrchestrator,
    private val transactionRunner: TransactionRunner,
) : MealPlanRepository {

    // Days are combined from their own Room flow rather than read once per plan emission: marking
    // a meal cooked only touches meal_plan_days, which would not re-trigger a meal_plans query.
    override fun observeMealPlan(uuid: UUID): Flow<MealPlan?> =
        combine(
            mealPlanDao.observeMealPlanById(uuid),
            mealPlanDao.observeDaysForMealPlan(uuid),
        ) { entity, days -> entity?.toDomain(days) }

    override fun observeMealPlanDay(dayId: UUID): Flow<MealPlanDay?> =
        mealPlanDao.observeDayById(dayId).map { it?.toDomain() }

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

    override suspend fun generateStatelessAndSave(
        planId: UUID,
        preferences: MealPlanPreferences,
    ): Result<Int> {
        return when (val result = mealPlanNetworkDataSource.generateStateless(preferences.toJson())) {
            is GenerateStatelessResult.Success -> {
                syncOrchestrator.persistGeneratedRecipes(result.response)

                val days = result.response.days.map { it.toDomain() }
                val written = applyGeneratedDays(planId, days) ?: run {
                    Timber.w("generateStatelessAndSave: plan $planId not found")
                    return Result.failure(IllegalStateException("Meal plan $planId not found locally"))
                }

                Timber.d("generateStatelessAndSave: filled plan $planId with $written day(s)")
                Result.success(written)
            }
            is GenerateStatelessResult.Error -> {
                Timber.w("generateStatelessAndSave: failed for plan $planId: ${result.message}")
                Result.failure(Exception(result.message))
            }
        }
    }

    /**
     * Replaces [planId]'s days with [days] and marks the plan READY, in one transaction, carrying
     * cooked marks forward by day index (see [carryForwardCookedMarks]) so a regenerated schedule
     * doesn't silently un-cook meals whose recipe didn't change.
     *
     * A no-op that returns `0` without touching Room when [days] is empty: an unsuccessful
     * generation attempt (a server response with no candidates, an on-device scheduler that found
     * nothing) must never wipe an existing, working schedule just because this call produced
     * nothing to replace it with.
     *
     * @return the number of days written, or `null` if [planId] has no local row to attach them to.
     */
    private suspend fun applyGeneratedDays(planId: UUID, days: List<MealPlanDay>): Int? {
        val existing = mealPlanDao.getMealPlanById(planId) ?: return null
        if (days.isEmpty()) return 0

        transactionRunner {
            val previousByDayIndex = mealPlanDao.getDaysForMealPlan(planId).associateBy { it.dayIndex }
            mealPlanDao.deleteDaysForMealPlan(planId)
            mealPlanDao.upsertDays(
                days.map { it.toEntity(planId).carryForwardCookedMarks(previousByDayIndex) }
            )
            mealPlanDao.upsertMealPlan(
                existing.copy(
                    status = MealPlanStatus.READY.name,
                    syncState = SyncState.PENDING,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }

        syncScheduler.requestMutationSync()
        return days.size
    }

    override suspend fun setMealCooked(dayId: UUID, slot: MealSlot, cooked: Boolean) {
        val cookedAt = if (cooked) System.currentTimeMillis() else null
        when (slot) {
            MealSlot.LUNCH -> mealPlanDao.setLunchCookedAt(dayId, cookedAt)
            MealSlot.DINNER -> mealPlanDao.setDinnerCookedAt(dayId, cookedAt)
        }
    }

    override suspend fun saveLocallyGeneratedDays(planId: UUID, days: List<MealPlanDay>) {
        val written = applyGeneratedDays(planId, days) ?: run {
            Timber.w("saveLocallyGeneratedDays: plan $planId not found")
            return
        }
        Timber.d("saveLocallyGeneratedDays: filled plan $planId with $written day(s) on device")
    }
}
