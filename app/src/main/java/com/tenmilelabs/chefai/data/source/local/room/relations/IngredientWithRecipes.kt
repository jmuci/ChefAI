package com.tenmilelabs.chefai.data.source.local.room.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.tenmilelabs.chefai.data.source.local.room.IngredientEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeIngredientEntity

data class IngredientWithRecipes(
    @Embedded val ingredient: IngredientEntity,
    @Relation(
        parentColumn = "uuid",
        entityColumn = "uuid",
        associateBy = Junction(
            value = RecipeIngredientEntity::class,
            parentColumn = "ingredientId",
            entityColumn = "recipeId"
        )
    )
    val recipes: List<RecipeEntity>
)
