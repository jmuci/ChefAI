package com.tenmilelabs.chefai.data.source.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tenmilelabs.chefai.data.source.local.util.SyncState
import com.tenmilelabs.chefai.data.source.local.util.SyncableEntity
import com.tenmilelabs.chefai.data.source.local.util.generateUuid7
import java.util.UUID

@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = AllergenEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["allergenId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SourceClassificationEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["sourcePrimaryId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SourceClassificationEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["sourceSecondaryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("allergenId"), 
        Index("sourcePrimaryId"), 
        Index("sourceSecondaryId"),
        Index(value = ["syncState", "updatedAt"])
    ]
)
data class IngredientEntity(
    @PrimaryKey override val uuid: UUID = generateUuid7(),
    val displayName: String,
    val allergenId: UUID?,
    val sourcePrimaryId: UUID?,   // e.g. "Animal"
    val sourceSecondaryId: UUID?,  // e.g. "Fish"
    override val updatedAt: Long,
    override val deletedAt: Long?,
    override val syncState: SyncState = SyncState.PENDING
): SyncableEntity