package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.RecipeDraftEntity
import java.util.UUID

/**
 * Data Access Object for recipe drafts (auto-save / process-death recovery).
 */
@Dao
interface RecipeDraftDao {

    @Upsert
    suspend fun saveDraft(draft: RecipeDraftEntity)

    @Query("SELECT * FROM recipe_drafts WHERE recipeId = :recipeId")
    suspend fun getDraft(recipeId: UUID): RecipeDraftEntity?

    @Query("DELETE FROM recipe_drafts WHERE recipeId = :recipeId")
    suspend fun deleteDraft(recipeId: UUID)

    @Query("DELETE FROM recipe_drafts")
    suspend fun deleteAllDrafts()
}
