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

    /** Forgets every pull cursor, so the next pull starts from `since=0`. */
    @Query("DELETE FROM sync_metadata")
    suspend fun clear()
}
