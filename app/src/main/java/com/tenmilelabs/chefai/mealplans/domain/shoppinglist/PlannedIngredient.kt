package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

import java.util.UUID

/** One ingredient of one planned recipe, as the shopping list needs it. */
data class PlannedIngredient(
    val recipeId: UUID,
    /**
     * Servings the recipe itself yields; `0` when the recipe never published one, in which case
     * [com.tenmilelabs.chefai.recipes.domain.scaling.RecipeScaling.DEFAULT_SERVINGS] is assumed —
     * the same assumption the recipe details screen makes.
     */
    val recipeServings: Int,
    val displayName: String,
    val quantity: Double,
    val unit: String,
)
