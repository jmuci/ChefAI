package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import java.util.UUID

@Dao
interface RecipeTagCrossRefDao {
    @Upsert
    suspend fun upsertCrossRef(crossRef: RecipeTagCrossRef)

    @Query("SELECT * FROM recipe_tags WHERE recipeId = :recipeId")
    suspend fun getTagsForRecipe(recipeId: UUID): List<RecipeTagCrossRef>

    @Upsert
    suspend fun upsertAll(crossRefs: List<RecipeTagCrossRef>)

    @Query("DELETE FROM recipe_tags WHERE recipeId = :recipeId")
    suspend fun deleteAllForRecipe(recipeId: UUID)

    @Query("UPDATE recipe_tags SET syncState = :syncState, updatedAt = :updatedAt WHERE recipeId = :recipeId")
    suspend fun updateSyncStateForRecipe(recipeId: UUID, syncState: SyncState, updatedAt: Long)

    @Query("UPDATE recipe_tags SET syncState = 'PENDING', updatedAt = :updatedAt WHERE recipeId IN (:recipeIds)")
    suspend fun markPendingForRecipes(recipeIds: List<UUID>, updatedAt: Long)
}
