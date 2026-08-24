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

    @Query("UPDATE meal_plans SET userId = :newUserId, syncState = 'PENDING', updatedAt = :updatedAt WHERE userId = :oldUserId")
    suspend fun reassignUserAndMarkPending(oldUserId: UUID, newUserId: UUID, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM meal_plans WHERE userId = :userId AND deletedAt IS NULL")
    suspend fun countMealPlansForUser(userId: UUID): Int

    // Days

    @Query("SELECT * FROM meal_plan_days WHERE mealPlanId = :mealPlanId ORDER BY dayIndex ASC")
    fun observeDaysForMealPlan(mealPlanId: UUID): Flow<List<MealPlanDayEntity>>

    /**
     * Every day row belonging to a user's live plans, so the list screen can show per-plan cooked
     * progress without opening a Room flow per plan.
     */
    @Query(
        """
        SELECT d.* FROM meal_plan_days d
        INNER JOIN meal_plans p ON p.uuid = d.mealPlanId
        WHERE p.userId = :userId AND p.deletedAt IS NULL
        ORDER BY d.dayIndex ASC
        """
    )
    fun observeDaysForUser(userId: UUID): Flow<List<MealPlanDayEntity>>

    @Query("SELECT * FROM meal_plan_days WHERE mealPlanId = :mealPlanId ORDER BY dayIndex ASC")
    suspend fun getDaysForMealPlan(mealPlanId: UUID): List<MealPlanDayEntity>

    /**
     * A single day by its own id, for screens that only know which slot they were opened from
     * (e.g. a recipe opened from a meal plan) rather than the whole plan.
     */
    @Query("SELECT * FROM meal_plan_days WHERE uuid = :dayId")
    fun observeDayById(dayId: UUID): Flow<MealPlanDayEntity?>

    @Upsert
    suspend fun upsertDays(days: List<MealPlanDayEntity>)

    @Query("DELETE FROM meal_plan_days WHERE mealPlanId = :mealPlanId")
    suspend fun deleteDaysForMealPlan(mealPlanId: UUID)

    /** Marks (or, with a `null` [cookedAt], unmarks) a day's dinner as cooked. */
    @Query("UPDATE meal_plan_days SET dinnerCookedAt = :cookedAt WHERE uuid = :dayId")
    suspend fun setDinnerCookedAt(dayId: UUID, cookedAt: Long?)

    /** Marks (or, with a `null` [cookedAt], unmarks) a day's lunch as cooked. */
    @Query("UPDATE meal_plan_days SET lunchCookedAt = :cookedAt WHERE uuid = :dayId")
    suspend fun setLunchCookedAt(dayId: UUID, cookedAt: Long?)
}
