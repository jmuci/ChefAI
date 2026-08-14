package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.RecipeImageStateEntity
import java.util.UUID

@Dao
interface RecipeImageStateDao {

    @Upsert
    suspend fun upsert(state: RecipeImageStateEntity)

    @Query("SELECT * FROM recipe_image_state WHERE recipeId = :recipeId")
    suspend fun getByRecipeId(recipeId: UUID): RecipeImageStateEntity?

    @Query("DELETE FROM recipe_image_state WHERE recipeId = :recipeId")
    suspend fun delete(recipeId: UUID)

    /**
     * Counts a download attempt, before it is made.
     *
     * A targeted upsert rather than [upsert], which writes the whole row: now that this table also
     * carries upload bookkeeping, a full-row write from the download sweep would reset
     * `uploadAttempts` and `uploadedFileModifiedAt` to their defaults. That would silently
     * resurrect an upload the backend had permanently rejected, and make an already-uploaded image
     * look like it needed sending again — the same full-row-write hazard as gotcha #24, one table
     * further in.
     */
    @Query(
        """
        INSERT INTO recipe_image_state
            (recipeId, attempts, lastAttemptAt, uploadAttempts, lastUploadAttemptAt, uploadedFileModifiedAt)
        VALUES (:recipeId, 1, :at, 0, 0, NULL)
        ON CONFLICT(recipeId) DO UPDATE SET
            attempts = attempts + 1,
            lastAttemptAt = :at
        """
    )
    suspend fun recordDownloadAttempt(recipeId: UUID, at: Long)

    // --- Upload half of the ladder ---
    //
    // Upserts rather than read-modify-write, because the bookkeeping row may not exist: the download
    // sweep creates one only when a fetch fails, and a recipe whose image uploads on the first try
    // has never needed one.

    /**
     * Counts an upload attempt, before it is made.
     *
     * Recorded up front for the same reason the download side does it: a run killed mid-request —
     * the process dying, the network dropping, WorkManager's budget expiring — must still count, or
     * a request that reliably hangs is retried forever.
     */
    @Query(
        """
        INSERT INTO recipe_image_state
            (recipeId, attempts, lastAttemptAt, uploadAttempts, lastUploadAttemptAt, uploadedFileModifiedAt)
        VALUES (:recipeId, 0, 0, 1, :at, NULL)
        ON CONFLICT(recipeId) DO UPDATE SET
            uploadAttempts = uploadAttempts + 1,
            lastUploadAttemptAt = :at
        """
    )
    suspend fun recordUploadAttempt(recipeId: UUID, at: Long)

    /**
     * Remembers which bytes are on the backend, and clears the attempt count so a future
     * replacement of this image starts with a full budget.
     *
     * The row is kept rather than deleted — unlike the download side, where a row's absence means
     * "resolved". Here [RecipeImageStateEntity.uploadedFileModifiedAt] is the only record of *what*
     * was uploaded, and losing it would make a replaced photo indistinguishable from an unchanged
     * one.
     */
    @Query(
        """
        INSERT INTO recipe_image_state
            (recipeId, attempts, lastAttemptAt, uploadAttempts, lastUploadAttemptAt, uploadedFileModifiedAt)
        VALUES (:recipeId, 0, 0, 0, 0, :modifiedAt)
        ON CONFLICT(recipeId) DO UPDATE SET
            uploadAttempts = 0,
            uploadedFileModifiedAt = :modifiedAt
        """
    )
    suspend fun recordUploadSuccess(recipeId: UUID, modifiedAt: Long?)

    /**
     * Spends the whole attempt budget at once, for a rejection that will fail identically forever —
     * a body the server refuses, a recipe that isn't ours, an exhausted quota.
     *
     * Retrying those two more times only produces two more identical rejections.
     */
    @Query(
        """
        INSERT INTO recipe_image_state
            (recipeId, attempts, lastAttemptAt, uploadAttempts, lastUploadAttemptAt, uploadedFileModifiedAt)
        VALUES (:recipeId, 0, 0, :cap, :at, NULL)
        ON CONFLICT(recipeId) DO UPDATE SET
            uploadAttempts = :cap,
            lastUploadAttemptAt = :at
        """
    )
    suspend fun burnUploadAttempts(recipeId: UUID, at: Long, cap: Int)
}
