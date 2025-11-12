package com.tenmilelabs.chefai.data.source.local

import androidx.room.Dao
import androidx.room.Upsert
import com.tenmilelabs.chefai.data.source.local.room.RecipeLabelCrossRef

@Dao
interface RecipeLabelCrossRefDao {
    @Upsert
    suspend fun upsertCrossRef(crossRef: RecipeLabelCrossRef)
}
