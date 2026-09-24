package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.BookmarkedRecipeEntity
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface BookmarkedRecipeDao {

    @Query("SELECT recipeId FROM bookmarked_recipes WHERE userId = :userId AND deletedAt IS NULL")
    fun observeBookmarkedRecipeIds(userId: UUID): Flow<List<UUID>>

    @Upsert
    suspend fun upsert(entity: BookmarkedRecipeEntity)

    @Query(
        "UPDATE bookmarked_recipes SET deletedAt = :ts, syncState = 'DELETED', updatedAt = :ts " +
        "WHERE userId = :userId AND recipeId = :recipeId"
    )
    suspend fun softDelete(userId: UUID, recipeId: UUID, ts: Long)

    @Query("SELECT * FROM bookmarked_recipes WHERE syncState IN ('PENDING', 'DELETED')")
    suspend fun getAllDirty(): List<BookmarkedRecipeEntity>

    /**
     * Marks a pushed row with the server's timestamp. Pass [expectedUpdatedAt] — the `updatedAt`
     * the row had when it was pushed — so an edit made while the push was in flight (which bumps
     * `updatedAt`) is left PENDING for the next push instead of being silently marked SYNCED.
     * @return the number of rows updated (0 when the row changed since it was pushed).
     */
    @Query(
        "UPDATE bookmarked_recipes SET syncState = :state, updatedAt = :updatedAt " +
        "WHERE userId = :userId AND recipeId = :recipeId AND (:expectedUpdatedAt IS NULL OR updatedAt = :expectedUpdatedAt)"
    )
    suspend fun updateSyncState(userId: UUID, recipeId: UUID, state: SyncState, updatedAt: Long, expectedUpdatedAt: Long? = null): Int

    /**
     * Moves [oldUserId]'s live bookmarks to [newUserId], marked PENDING.
     *
     * Not a plain `UPDATE … SET userId`: the primary key is `(userId, recipeId)`, so when the
     * account already has a row for the same recipe (e.g. a soft-deleted one from before a logout)
     * the update would violate it and roll back the caller's whole account-upgrade transaction.
     * Copying with REPLACE revives the account's row instead. Soft-deleted anonymous rows are
     * dropped — an anonymous "unbookmark" has nothing to tell the account's server state.
     */
    @Transaction
    suspend fun reassignUserAndMarkPending(oldUserId: UUID, newUserId: UUID, updatedAt: Long) {
        copyLiveBookmarksToUser(oldUserId, newUserId, updatedAt)
        deleteAllForUser(oldUserId)
    }

    @Query(
        "INSERT OR REPLACE INTO bookmarked_recipes (userId, recipeId, updatedAt, deletedAt, syncState) " +
        "SELECT :newUserId, recipeId, :updatedAt, NULL, 'PENDING' FROM bookmarked_recipes " +
        "WHERE userId = :oldUserId AND deletedAt IS NULL"
    )
    suspend fun copyLiveBookmarksToUser(oldUserId: UUID, newUserId: UUID, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM bookmarked_recipes WHERE userId = :userId AND deletedAt IS NULL")
    suspend fun countLiveForUser(userId: UUID): Int

    @Query("DELETE FROM bookmarked_recipes WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: UUID)
}
