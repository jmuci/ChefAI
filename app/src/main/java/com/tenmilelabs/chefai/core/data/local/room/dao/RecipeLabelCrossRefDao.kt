package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import java.util.UUID

@Dao
interface RecipeLabelCrossRefDao {
    @Upsert
    suspend fun upsertCrossRef(crossRef: RecipeLabelCrossRef)

    @Query("SELECT * FROM recipe_labels WHERE recipeId = :recipeId")
    suspend fun getLabelsForRecipe(recipeId: UUID): List<RecipeLabelCrossRef>

    @Upsert
    suspend fun upsertAll(crossRefs: List<RecipeLabelCrossRef>)

    @Query("DELETE FROM recipe_labels WHERE recipeId = :recipeId")
    suspend fun deleteAllForRecipe(recipeId: UUID)

    @Query("UPDATE recipe_labels SET syncState = :syncState, updatedAt = :updatedAt WHERE recipeId = :recipeId")
    suspend fun updateSyncStateForRecipe(recipeId: UUID, syncState: SyncState, updatedAt: Long)

    @Query("UPDATE recipe_labels SET syncState = 'PENDING', updatedAt = :updatedAt WHERE recipeId IN (:recipeIds)")
    suspend fun markPendingForRecipes(recipeIds: List<UUID>, updatedAt: Long)
}
