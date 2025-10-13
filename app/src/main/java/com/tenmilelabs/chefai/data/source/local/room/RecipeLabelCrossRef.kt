package com.tenmilelabs.chefai.data.source.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.tenmilelabs.chefai.data.source.local.util.SyncState
import com.tenmilelabs.chefai.data.source.local.util.SyncableCrossRef
import java.util.UUID

@Entity(
    primaryKeys = ["recipeId", "labelId"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LabelEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["labelId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["labelId"]),
        Index(value = ["syncState", "updatedAt"])
    ]
)
data class RecipeLabelCrossRef(
    val recipeId: UUID,
    val labelId: UUID,
    override val updatedAt: Long,
    override val deletedAt: Long?,
    override val syncState: SyncState = SyncState.PENDING
): SyncableCrossRef

