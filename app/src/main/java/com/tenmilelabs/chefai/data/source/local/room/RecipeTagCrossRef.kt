package com.tenmilelabs.chefai.data.source.local.room

import androidx.room.Entity
import androidx.room.ForeignKey

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
        )
    ])
data class RecipeTagCrossRef(
    val recipeId: String,
    val tagId: String
)