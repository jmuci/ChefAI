package com.tenmilelabs.chefai.data.source.local.room.relations

import java.util.UUID

// TODO Rename to Recipe Ingredients
data class RecipeIngredient (
    val ingredientId: UUID,
    val ingredientDisplayName: String,
    val quantity: Double,
    val unit: String,
    val allergenName: String?,
    val srcCategory: String?,
    val srcSubcategory: String?
)
