package com.tenmilelabs.chefai.mealplans.data.repository

import com.tenmilelabs.chefai.core.data.local.room.dao.MealPlanDao
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.mealplans.data.mapper.toDomain
import com.tenmilelabs.chefai.mealplans.data.mapper.toEntity
import com.tenmilelabs.chefai.mealplans.data.network.MealPlanNetworkDataSource
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import kotlinx.coroutines.flow.Flow
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
) : MealPlanRepository {

    override fun observeMealPlan(uuid: UUID): Flow<MealPlan?> =
        mealPlanDao.observeMealPlanById(uuid).map { entity ->
            entity?.let {
                val days = mealPlanDao.getDaysForMealPlan(it.uuid)
                it.toDomain(days)
            }
        }

    override fun observeMealPlansForUser(userId: UUID): Flow<List<MealPlan>> =
        mealPlanDao.observeMealPlansForUser(userId).map { entities ->
            entities.map { entity ->
                val days = mealPlanDao.getDaysForMealPlan(entity.uuid)
                entity.toDomain(days)
            }
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
}
