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
}
