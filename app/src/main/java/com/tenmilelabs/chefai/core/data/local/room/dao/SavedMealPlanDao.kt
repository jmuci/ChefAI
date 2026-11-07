package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.SavedMealPlanEntity
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface SavedMealPlanDao {

    /** The user's live (not deleted) saved plans, newest first. */
    @Query("SELECT * FROM saved_meal_plans WHERE userId = :userId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeForUser(userId: UUID): Flow<List<SavedMealPlanEntity>>

    /** Returns the row even when tombstoned; callers decide whether a deleted plan counts. */
    @Query("SELECT * FROM saved_meal_plans WHERE uuid = :uuid")
    suspend fun getById(uuid: UUID): SavedMealPlanEntity?

    @Upsert
    suspend fun upsert(plan: SavedMealPlanEntity)

    /** Tombstones the row and queues the deletion for push. */
    @Query("UPDATE saved_meal_plans SET deletedAt = :deletedAt, updatedAt = :deletedAt, syncState = 'PENDING' WHERE uuid = :uuid")
    suspend fun softDelete(uuid: UUID, deletedAt: Long)

    @Query("SELECT * FROM saved_meal_plans WHERE syncState IN ('PENDING', 'DELETED')")
    suspend fun getAllDirty(): List<SavedMealPlanEntity>

    /**
     * Marks a pushed row with the server's timestamp. Pass [expectedUpdatedAt] — the `updatedAt`
     * the row had when it was pushed — so an edit or deletion made while the push was in flight is
     * left `PENDING` for the next push instead of being marked `SYNCED`.
     * @return the number of rows updated (0 when the row changed since it was pushed).
     */
    @Query(
        "UPDATE saved_meal_plans SET syncState = :state, updatedAt = :updatedAt " +
            "WHERE uuid = :uuid AND (:expectedUpdatedAt IS NULL OR updatedAt = :expectedUpdatedAt)"
    )
    suspend fun updateSyncState(uuid: UUID, state: SyncState, updatedAt: Long, expectedUpdatedAt: Long? = null): Int
}
