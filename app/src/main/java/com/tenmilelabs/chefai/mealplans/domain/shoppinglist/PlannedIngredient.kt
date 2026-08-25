package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

import java.util.UUID

/** One ingredient of one planned recipe, as the shopping list needs it. */
data class PlannedIngredient(
    val recipeId: UUID,
    /** Servings the recipe itself yields; `0` when unknown, which disables scaling for this row. */
    val recipeServings: Int,
    val displayName: String,
    val quantity: Double,
    val unit: String,
)
