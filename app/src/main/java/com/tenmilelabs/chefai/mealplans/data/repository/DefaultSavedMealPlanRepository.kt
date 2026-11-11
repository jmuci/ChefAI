package com.tenmilelabs.chefai.mealplans.data.repository

import com.tenmilelabs.chefai.core.data.local.room.dao.MealPlanDao
import com.tenmilelabs.chefai.core.data.local.room.dao.SavedMealPlanDao
import com.tenmilelabs.chefai.core.data.local.util.nextUpdatedAt
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.mealplans.data.local.SavedPlanImageStore
import com.tenmilelabs.chefai.mealplans.data.mapper.toDomain
import com.tenmilelabs.chefai.mealplans.data.mapper.toEntity
import com.tenmilelabs.chefai.mealplans.domain.model.SavedMealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.SavedMealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.repository.SavedMealPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSavedMealPlanRepository @Inject constructor(
    private val savedMealPlanDao: SavedMealPlanDao,
    private val mealPlanDao: MealPlanDao,
    private val syncScheduler: SyncScheduler,
    private val imageStore: SavedPlanImageStore,
) : SavedMealPlanRepository {

    override fun observeSavedPlans(userId: UUID): Flow<List<SavedMealPlan>> =
        savedMealPlanDao.observeForUser(userId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getSavedPlan(uuid: UUID): SavedMealPlan? =
        savedMealPlanDao.getById(uuid)?.takeIf { it.deletedAt == null }?.toDomain()

    override suspend fun saveFromMealPlan(
        savedPlanId: UUID,
        userId: UUID,
        mealPlanId: UUID,
        title: String,
        heroImagePath: String?,
    ): Result<SavedMealPlan> {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Saved plan title must not be blank"))
        }
        if (trimmed.length > SavedMealPlan.MAX_TITLE_LENGTH) {
            return Result.failure(
                IllegalArgumentException("Saved plan title exceeds ${SavedMealPlan.MAX_TITLE_LENGTH} characters")
            )
        }

        val plan = mealPlanDao.getMealPlanById(mealPlanId)?.takeIf { it.deletedAt == null }
            ?: return Result.failure(IllegalStateException("Meal plan $mealPlanId not found"))
        val days = mealPlanDao.getDaysForMealPlan(mealPlanId)
        if (days.isEmpty()) {
            return Result.failure(IllegalStateException("Meal plan $mealPlanId has no days to save"))
        }

        val now = System.currentTimeMillis()
        val snapshot = SavedMealPlan(
            uuid = savedPlanId,
            userId = userId,
            title = trimmed,
            heroImagePath = heroImagePath,
            preferences = plan.toDomain(emptyList()).preferences.copy(planLengthDays = days.size),
            days = days.sortedBy { it.dayIndex }.map {
                SavedMealPlanDay(
                    dayIndex = it.dayIndex,
                    lunchRecipeId = it.lunchRecipeId,
                    dinnerRecipeId = it.dinnerRecipeId,
                )
            },
            sourceMealPlanId = mealPlanId,
            createdAt = now,
            // A retried save reuses its id; the stamp must still sort after the stored version.
            updatedAt = nextUpdatedAt(savedMealPlanDao.getById(savedPlanId)?.updatedAt, now),
        )
        savedMealPlanDao.upsert(snapshot.toEntity())
        syncScheduler.requestMutationSync()
        Timber.d("Saved meal plan %s from plan %s (%d days)", savedPlanId, mealPlanId, days.size)
        return Result.success(snapshot)
    }

    override suspend fun delete(uuid: UUID) {
        savedMealPlanDao.softDelete(uuid, System.currentTimeMillis())
        imageStore.delete(uuid)
        syncScheduler.requestMutationSync()
    }
}
