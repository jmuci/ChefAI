package com.tenmilelabs.chefai.recipes.domain.model

import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.model.Tag
import java.util.UUID

/**
 * Transient representation of an in-progress recipe edit.
 * Separate from [com.tenmilelabs.chefai.core.domain.model.Recipe] to avoid corrupting
 * persisted data during editing.
 *
 * String fields for numeric inputs (prepTimeMinutes, cookTimeMinutes, servings) match the form
 * input state so partial/invalid input can be preserved across auto-saves and process death.
 */
data class RecipeDraft(
    val recipeId: UUID,
    val isNewRecipe: Boolean,
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val selectedImageUri: String? = null,
    /** On-device copy of [imageUrl], if cached at import time. See [com.tenmilelabs.chefai.core.domain.model.Recipe.localImagePath]. */
    val localImagePath: String? = null,
    val prepTimeMinutes: String = "",
    val cookTimeMinutes: String = "",
    val servings: String = "",
    val externalUrl: String = "",
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val labels: List<Label> = emptyList(),
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
)
