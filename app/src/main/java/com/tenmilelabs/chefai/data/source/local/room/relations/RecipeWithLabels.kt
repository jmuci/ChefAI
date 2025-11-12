package com.tenmilelabs.chefai.data.source.local.room.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.tenmilelabs.chefai.data.source.local.room.LabelEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeLabelCrossRef

data class RecipeWithLabels(
    @Embedded val recipe: RecipeEntity,
    @Relation(
        parentColumn = "uuid",
        entityColumn = "uuid",
        associateBy = Junction(
            value = RecipeLabelCrossRef::class,
            parentColumn = "recipeId",
            entityColumn = "labelId"
        )
    )
    val labels: List<LabelEntity>
)
