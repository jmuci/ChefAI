package com.tenmilelabs.chefai.core.data.local.room.relations

import java.util.UUID

/**
 * One ingredient of one recipe, with the recipe's own serving count so a shopping list can scale
 * the quantity to the servings a meal plan asked for.
 */
data class PlanIngredientRow(
    val recipeId: UUID,
    val recipeServings: Int,
    val ingredientId: UUID,
    val ingredientDisplayName: String,
    val quantity: Double,
    val unit: String,
)
