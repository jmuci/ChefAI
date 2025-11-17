package com.tenmilelabs.chefai.data.source.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tenmilelabs.chefai.data.source.local.util.SyncState
import com.tenmilelabs.chefai.data.source.local.util.SyncableEntity
import java.util.UUID

@Entity(
    tableName = "source_classifications",
    indices = [
        Index(value = ["syncState", "updatedAt"])
    ]
)
data class SourceClassificationEntity(
    @PrimaryKey override val uuid: UUID,
    val category: String,      // e.g. "Animal", "Plant", "Fungal"
    val subcategory: String?,   // e.g. "Fish", "Meat", "Egg", "Legume"
    override val updatedAt: Long,
    override val deletedAt: Long?,
    override val syncState: SyncState = SyncState.PENDING
): SyncableEntity
