package com.tenmilelabs.chefai.core.data.local.room.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity

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
