package com.tenmilelabs.chefai.data.source.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.data.source.local.room.RecipeStepEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for the recipe_steps table.
 */
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
    suspend fun upsertRecipeStep(recipeStep: RecipeStepEntity)

    @Upsert
    suspend fun upsertAll(recipeSteps: List<RecipeStepEntity>)

    @Query("SELECT * FROM recipe_steps WHERE syncState = 'PENDING'")
    suspend fun getDirty(): List<RecipeStepEntity>
}
