package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface RecipeStepDao {
    @Query("SELECT * FROM recipe_steps")
    suspend fun getAllRecipeSteps(): List<RecipeStepEntity>

    @Query("SELECT * FROM recipe_steps")
    fun observeAll(): Flow<List<RecipeStepEntity>>

    @Query("SELECT * FROM recipe_steps WHERE uuid = :uuid")
    suspend fun getRecipeStepById(uuid: UUID): RecipeStepEntity?

    @Query("SELECT * FROM recipe_steps WHERE uuid = :uuid")
    fun observeRecipeStepById(uuid: UUID): Flow<RecipeStepEntity?>

    @Query("DELETE FROM recipe_steps WHERE uuid = :uuid")
    suspend fun deleteRecipeStep(uuid: UUID)

    @Query("DELETE FROM recipe_steps")
    suspend fun deleteAllRecipeSteps()

    @Upsert
    suspend fun upsertStep(step: RecipeStepEntity)

    @Query("UPDATE recipe_steps SET syncState = 'PENDING', updatedAt = :updatedAt WHERE recipeId IN (:recipeIds)")
    suspend fun markPendingForRecipes(recipeIds: List<UUID>, updatedAt: Long)
}
