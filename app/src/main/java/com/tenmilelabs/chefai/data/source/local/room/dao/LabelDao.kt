package com.tenmilelabs.chefai.data.source.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.tenmilelabs.chefai.data.source.local.room.LabelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels")
    fun getAll(): Flow<List<LabelEntity>>
}
