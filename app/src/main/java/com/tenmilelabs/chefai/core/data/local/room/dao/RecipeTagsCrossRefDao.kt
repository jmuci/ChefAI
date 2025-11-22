package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef

@Dao
interface RecipeTagCrossRefDao {
    @Upsert
    suspend fun upsertCrossRef(crossRef: RecipeTagCrossRef)
}