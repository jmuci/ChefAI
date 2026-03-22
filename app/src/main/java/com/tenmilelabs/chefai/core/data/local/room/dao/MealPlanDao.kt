package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.MealPlanDayEntity
import com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface MealPlanDao {

    @Query("SELECT * FROM meal_plans WHERE userId = :userId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeMealPlansForUser(userId: UUID): Flow<List<MealPlanEntity>>

    @Query("SELECT * FROM meal_plans WHERE uuid = :uuid")
    suspend fun getMealPlanById(uuid: UUID): MealPlanEntity?

    @Query("SELECT * FROM meal_plans WHERE uuid = :uuid")
    fun observeMealPlanById(uuid: UUID): Flow<MealPlanEntity?>

    @Upsert
    suspend fun upsertMealPlan(mealPlan: MealPlanEntity)

    @Query("UPDATE meal_plans SET deletedAt = :deletedAt, syncState = 'DELETED', updatedAt = :deletedAt WHERE uuid = :uuid")
    suspend fun softDelete(uuid: UUID, deletedAt: Long)

    @Query("SELECT * FROM meal_plans WHERE syncState IN ('PENDING', 'DELETED')")
    suspend fun getAllDirty(): List<MealPlanEntity>

    @Query("UPDATE meal_plans SET syncState = :syncState, updatedAt = :updatedAt WHERE uuid = :uuid")
    suspend fun updateSyncState(uuid: UUID, syncState: SyncState, updatedAt: Long)

    // Days

    @Query("SELECT * FROM meal_plan_days WHERE mealPlanId = :mealPlanId ORDER BY dayIndex ASC")
    fun observeDaysForMealPlan(mealPlanId: UUID): Flow<List<MealPlanDayEntity>>

    @Query("SELECT * FROM meal_plan_days WHERE mealPlanId = :mealPlanId ORDER BY dayIndex ASC")
    suspend fun getDaysForMealPlan(mealPlanId: UUID): List<MealPlanDayEntity>

    @Upsert
    suspend fun upsertDays(days: List<MealPlanDayEntity>)

    @Query("DELETE FROM meal_plan_days WHERE mealPlanId = :mealPlanId")
    suspend fun deleteDaysForMealPlan(mealPlanId: UUID)
}
