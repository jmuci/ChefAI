package com.tenmilelabs.chefai.core.data.local.room.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.tenmilelabs.chefai.core.data.local.room.LabelEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.room.TagEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity

data class RecipeWithDetails(
    @Embedded val recipe: RecipeEntity,

    @Relation(parentColumn = "creatorId", entityColumn = "uuid")
    val creator: UserEntity,

    @Relation(parentColumn = "uuid", entityColumn = "recipeId")
    val steps: List<RecipeStepEntity>,

    @Relation(
        parentColumn = "uuid",
        entityColumn = "uuid",
        associateBy = Junction(
            value = RecipeTagCrossRef::class,
            parentColumn = "recipeId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>,

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
