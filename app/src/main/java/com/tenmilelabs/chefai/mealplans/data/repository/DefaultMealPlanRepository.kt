package com.tenmilelabs.chefai.mealplans.data.repository

import com.tenmilelabs.chefai.core.data.local.room.dao.MealPlanDao
import com.tenmilelabs.chefai.mealplans.data.mapper.toDomain
import com.tenmilelabs.chefai.mealplans.data.mapper.toEntity
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultMealPlanRepository @Inject constructor(
    private val mealPlanDao: MealPlanDao,
) : MealPlanRepository {

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
    }

    override suspend fun deleteMealPlan(uuid: UUID) {
        mealPlanDao.softDelete(uuid, System.currentTimeMillis())
    }
}
