package com.tenmilelabs.chefai.data.source.local.room

import androidx.room.Entity
import androidx.room.ForeignKey

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
    ]
)
data class RecipeLabelCrossRef(
    val recipeId: String,
    val labelId: String
)

