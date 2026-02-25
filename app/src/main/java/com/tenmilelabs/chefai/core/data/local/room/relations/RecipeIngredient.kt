package com.tenmilelabs.chefai.core.data.local.room.relations

import java.util.UUID

data class RecipeIngredient(
    val ingredientId: UUID,
    val ingredientDisplayName: String,
    val quantity: Double,
    val unit: String,
    val allergenName: String?,
    val srcCategory: String?,
    val srcSubcategory: String?
)
