package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.SyncMetadataEntity

@Dao
interface SyncMetadataDao {
    @Query("SELECT lastSyncedAt FROM sync_metadata WHERE entityType = :entityType")
    suspend fun getLastSyncedAt(entityType: String): Long?

    @Upsert
    suspend fun upsert(metadata: SyncMetadataEntity)
}
