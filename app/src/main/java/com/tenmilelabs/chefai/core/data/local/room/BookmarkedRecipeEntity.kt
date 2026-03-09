package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.local.util.SyncableCrossRef
import java.util.UUID

@Entity(
    tableName = "bookmarked_recipes",
    primaryKeys = ["userId", "recipeId"],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("recipeId"),
        Index(value = ["syncState", "updatedAt"])
    ]
)
data class BookmarkedRecipeEntity(
    val userId: UUID,
    val recipeId: UUID,
    override val updatedAt: Long,
    override val deletedAt: Long?,
    override val syncState: SyncState = SyncState.PENDING
) : SyncableCrossRef
