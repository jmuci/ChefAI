package com.tenmilelabs.chefai.data.source.local.room.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.tenmilelabs.chefai.data.source.local.room.RecipeEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.data.source.local.room.TagEntity

data class TagWithRecipes(
    @Embedded val tag: TagEntity,
    @Relation(
        parentColumn = "uuid",
        entityColumn = "uuid",
        associateBy = Junction(
            value = RecipeTagCrossRef::class,
            parentColumn = "tagId",
            entityColumn = "recipeId"
        )
    )
    val recipes: List<RecipeEntity>
)
