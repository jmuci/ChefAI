package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.local.util.SyncableEntity
import java.util.UUID

@Entity(
    tableName = "labels",
    indices = [
        Index(value = ["syncState", "updatedAt"])
    ]
)
data class LabelEntity(
    @PrimaryKey override val uuid: UUID,
    val displayName: String,

    override val updatedAt: Long,
    override val deletedAt: Long?,
    override val syncState: SyncState = SyncState.PENDING
): SyncableEntity
