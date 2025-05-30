package com.tenmilelabs.chefai.data.source.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the task table.
 */
@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes")
    suspend fun getAllRecipes(): List<RecipeEntity>

    @Query("SELECT * FROM recipes")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE uuid = :uuid")
    fun getRecipeById(uuid: String): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE uuid = :uuid")
    fun observeRecipeById(uuid: String): Flow<RecipeEntity>

    @Query("DELETE FROM recipes WHERE uuid = :uuid")
    suspend fun deleteRecipe(uuid: String)

    @Query("DELETE FROM recipes")
    suspend fun deleteAllRecipes()

    @Upsert
    suspend fun upsertRecipe(recipe: RecipeEntity)

    @Upsert
    fun upsertAll(recipes: List<RecipeEntity>)

}