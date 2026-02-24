package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import java.util.UUID

@Dao
interface RecipeLabelCrossRefDao {
    @Upsert
    suspend fun upsertCrossRef(crossRef: RecipeLabelCrossRef)

    @Query("UPDATE recipe_labels SET syncState = 'PENDING', updatedAt = :updatedAt WHERE recipeId IN (:recipeIds)")
    suspend fun markPendingForRecipes(recipeIds: List<UUID>, updatedAt: Long)
}
