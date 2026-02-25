package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.SyncMetadataEntity

class FakeSyncMetadataDao : SyncMetadataDao {

    private val metadata = mutableMapOf<String, SyncMetadataEntity>()

    override suspend fun getLastSyncedAt(entityType: String): Long? {
        return metadata[entityType]?.lastSyncedAt
    }

    override suspend fun upsert(metadata: SyncMetadataEntity) {
        this.metadata[metadata.entityType] = metadata
    }
}
