package com.tenmilelabs.chefai.data.source.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.tenmilelabs.chefai.data.source.local.util.SyncState
import com.tenmilelabs.chefai.data.source.local.util.SyncableCrossRef
import java.util.UUID

@Entity(primaryKeys = ["recipeId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.RESTRICT
        ),
    ],
    indices = [
        Index(value = ["tagId"]),
        Index(value = ["syncState", "updatedAt"])
    ]
)
data class RecipeTagCrossRef(
    val recipeId: UUID,
    val tagId: UUID,
    override val updatedAt: Long,
    override val deletedAt: Long?,
    override val syncState: SyncState = SyncState.PENDING
) : SyncableCrossRef