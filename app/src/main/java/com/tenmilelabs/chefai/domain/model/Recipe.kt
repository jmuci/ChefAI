package com.tenmilelabs.chefai.domain.model

import java.util.UUID

/**
 * A complete, business-oriented representation of a recipe, including all its details
 * and related entities. This is the source of truth for the UI and domain logic.
 */
data class Recipe(
    val uuid: UUID,
    val title: String,
    val description: String,
    val imageUrl: String,
    val imageUrlThumbnail: String,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    val creator: User,
    val recipeExternalUrl: String?,
    val ingredients: List<Ingredient>,
    val steps: List<RecipeStep>,
    val tags: List<Tag>,
    val labels: List<Label>,
    val updatedAt: Long,
)
