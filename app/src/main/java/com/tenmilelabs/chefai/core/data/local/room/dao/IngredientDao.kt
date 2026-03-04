package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.relations.IngredientWithDetails
import com.tenmilelabs.chefai.core.data.local.room.relations.IngredientWithRecipes
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for the ingredients table.
 */
@Dao
interface IngredientDao {
    @Query("SELECT * FROM ingredients")
    suspend fun getAllIngredients(): List<IngredientEntity>

    @Query("SELECT * FROM ingredients")
    fun observeAll(): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients WHERE uuid = :uuid")
    suspend fun getIngredientById(uuid: UUID): IngredientEntity?

    @Query("SELECT * FROM ingredients WHERE uuid = :uuid")
    fun observeIngredientById(uuid: UUID): Flow<IngredientEntity?>

    @Transaction
    @Query("SELECT * FROM ingredients WHERE uuid = :uuid")
    suspend fun getIngredientWithRecipes(uuid: UUID): IngredientWithRecipes?

    @Transaction
    @Query("SELECT * FROM ingredients")
    fun observeIngredientsWithRecipes(): Flow<List<IngredientWithRecipes>>

    @Transaction
    @Query("SELECT * FROM ingredients WHERE uuid = :uuid")
    suspend fun getIngredientWithDetails(uuid: UUID): IngredientWithDetails?

    @Transaction
    @Query("SELECT * FROM ingredients")
    fun observeIngredientsWithDetails(): Flow<List<IngredientWithDetails>>

    @Query("DELETE FROM ingredients WHERE uuid = :uuid")
    suspend fun deleteIngredient(uuid: UUID)

    @Query("DELETE FROM ingredients")
    suspend fun deleteAllIngredients()

    @Upsert
    suspend fun upsertIngredient(ingredient: IngredientEntity)

    @Upsert
    suspend fun upsertAll(ingredients: List<IngredientEntity>)

    @Query("SELECT * FROM ingredients WHERE syncState = 'PENDING'")
    suspend fun getDirty(): List<IngredientEntity>

    @Query("SELECT uuid FROM ingredients WHERE uuid IN (:uuids)")
    suspend fun getExistingIds(uuids: List<UUID>): List<UUID>
}