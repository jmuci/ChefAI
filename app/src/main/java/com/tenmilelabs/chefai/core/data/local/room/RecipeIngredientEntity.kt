package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.local.util.SyncableCrossRef
import java.util.UUID

@Entity(
    tableName = "recipe_ingredients",
    primaryKeys = ["recipeId", "ingredientId"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("recipeId"),
        Index("ingredientId"),
        Index(value = ["syncState", "updatedAt"])
    ]
)
data class RecipeIngredientEntity(
    val recipeId: UUID,
    val ingredientId: UUID,
    val quantity: Double,
    val unit: String, //TODO Make enum
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
    override val syncState: SyncState = SyncState.PENDING
): SyncableCrossRef
