package com.tenmilelabs.chefai.core.data.local.room.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.tenmilelabs.chefai.core.data.local.room.LabelEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef

data class LabelWithRecipes(
    @Embedded val label: LabelEntity,
    @Relation(
        parentColumn = "uuid",
        entityColumn = "uuid",
        associateBy = Junction(
            value = RecipeLabelCrossRef::class,
            parentColumn = "labelId",
            entityColumn = "recipeId"
        )
    )
    val recipes: List<RecipeEntity>
)
