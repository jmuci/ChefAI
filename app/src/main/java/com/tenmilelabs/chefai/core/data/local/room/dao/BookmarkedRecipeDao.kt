package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
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

    @Query(
        "UPDATE bookmarked_recipes SET syncState = :state, updatedAt = :updatedAt " +
        "WHERE userId = :userId AND recipeId = :recipeId"
    )
    suspend fun updateSyncState(userId: UUID, recipeId: UUID, state: SyncState, updatedAt: Long)
}
