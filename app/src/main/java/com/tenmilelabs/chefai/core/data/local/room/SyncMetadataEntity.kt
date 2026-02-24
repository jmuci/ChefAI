package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val entityType: String,
    val lastSyncedAt: Long = 0
)
