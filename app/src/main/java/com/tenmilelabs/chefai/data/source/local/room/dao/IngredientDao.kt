package com.tenmilelabs.chefai.data.source.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.tenmilelabs.chefai.data.source.local.room.IngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {
    @Query("SELECT * FROM ingredients")
    fun getAll(): Flow<List<IngredientEntity>>
}
