package com.tenmilelabs.chefai.data.source.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.tenmilelabs.chefai.data.source.local.room.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags")
    fun getAll(): Flow<List<TagEntity>>
}
