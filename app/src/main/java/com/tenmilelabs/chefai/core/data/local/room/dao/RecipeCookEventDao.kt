package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tenmilelabs.chefai.core.data.local.room.RecipeCookEventEntity
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface RecipeCookEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: RecipeCookEventEntity)

    @Query("SELECT * FROM recipe_cook_events WHERE uuid = :uuid")
    suspend fun getById(uuid: UUID): RecipeCookEventEntity?

    /** The live (not un-marked) event for one slot's [recipeId], if any. */
    @Query(
        """
        SELECT * FROM recipe_cook_events
        WHERE mealPlanId = :mealPlanId AND dayIndex = :dayIndex AND slot = :slot AND recipeId = :recipeId
            AND deletedAt IS NULL
        LIMIT 1
        """
    )
    suspend fun getLiveForSlot(mealPlanId: UUID, dayIndex: Int, slot: String, recipeId: UUID): RecipeCookEventEntity?

    /**
     * Tombstones the slot's live event(s) for [recipeId] — un-marking a meal. The row stays (with
     * [deletedAt] set and `PENDING`) so the deletion can sync; it is excluded from
     * [observeCookedRecipeIds]. Scoped by recipe so un-marking a slot that has since been given a
     * different recipe cannot erase the history of the one cooked before.
     */
    @Query(
        """
        UPDATE recipe_cook_events
        SET deletedAt = :deletedAt, updatedAt = :deletedAt, syncState = 'PENDING'
        WHERE mealPlanId = :mealPlanId AND dayIndex = :dayIndex AND slot = :slot AND recipeId = :recipeId
            AND deletedAt IS NULL
        """
    )
    suspend fun tombstoneForSlot(mealPlanId: UUID, dayIndex: Int, slot: String, recipeId: UUID, deletedAt: Long)

    /** Ids of every recipe cooked at least once, ignoring un-marked (tombstoned) events. */
    @Query("SELECT DISTINCT recipeId FROM recipe_cook_events WHERE deletedAt IS NULL")
    fun observeCookedRecipeIds(): Flow<List<UUID>>

    @Query("SELECT * FROM recipe_cook_events WHERE syncState IN ('PENDING', 'DELETED')")
    suspend fun getAllDirty(): List<RecipeCookEventEntity>

    /**
     * Marks a pushed event with the server's timestamp. Pass [expectedUpdatedAt] — the `updatedAt`
     * the row had when it was pushed — so a tombstone written while the push was in flight is left
     * `PENDING` for the next push instead of being marked `SYNCED`.
     * @return the number of rows updated (0 when the row changed since it was pushed).
     */
    @Query(
        "UPDATE recipe_cook_events SET syncState = :state, updatedAt = :updatedAt " +
            "WHERE uuid = :uuid AND (:expectedUpdatedAt IS NULL OR updatedAt = :expectedUpdatedAt)"
    )
    suspend fun updateSyncState(uuid: UUID, state: SyncState, updatedAt: Long, expectedUpdatedAt: Long? = null): Int

    /**
     * Applies a pulled tombstone to a local event: final, so it overrides even a `PENDING` live
     * row (the server will not take the live version back).
     */
    @Query(
        "UPDATE recipe_cook_events SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncState = 'SYNCED' " +
            "WHERE uuid = :uuid"
    )
    suspend fun applyTombstone(uuid: UUID, deletedAt: Long, updatedAt: Long)

    /** Re-keys an event whose id the server refused (`EVENT_ID_TAKEN`) and queues it for push. */
    @Query("UPDATE recipe_cook_events SET uuid = :newUuid, syncState = 'PENDING' WHERE uuid = :oldUuid")
    suspend fun reassignId(oldUuid: UUID, newUuid: UUID)

    @Query("UPDATE recipe_cook_events SET syncState = :state WHERE uuid = :uuid")
    suspend fun setSyncState(uuid: UUID, state: SyncState)

    @Query("DELETE FROM recipe_cook_events WHERE uuid = :uuid")
    suspend fun delete(uuid: UUID)
}
