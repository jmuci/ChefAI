package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import java.util.UUID

@Dao
interface RecipeTagCrossRefDao {
    @Upsert
    suspend fun upsertCrossRef(crossRef: RecipeTagCrossRef)

    @Query("UPDATE recipe_tags SET syncState = 'PENDING', updatedAt = :updatedAt WHERE recipeId IN (:recipeIds)")
    suspend fun markPendingForRecipes(recipeIds: List<UUID>, updatedAt: Long)
}