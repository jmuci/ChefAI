package com.tenmilelabs.chefai.recipes.ui.editor

import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.model.Tag

data class RecipeFields(
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val selectedImageUri: String? = null,
    /** On-device copy of [imageUrl], if cached at import time; cleared when the user edits [imageUrl]. */
    val localImagePath: String? = null,
    val prepTimeMinutes: String = "",
    val cookTimeMinutes: String = "",
    val servings: String = "",
    val externalUrl: String = "",
)

data class IngredientsFields(
    val input: String = "",
    val quantity: String = "",
    val unit: String = "",
    val selectedIngredients: List<RecipeIngredient> = emptyList(),
    val suggestions: List<String> = emptyList(),
)

data class StepsFields(
    val input: String = "",
    val steps: List<RecipeStep> = emptyList(),
)

data class TagsFields(
    val input: String = "",
    val selectedTags: List<Tag> = emptyList(),
    val suggestions: List<String> = emptyList(),
)

data class LabelsFields(
    val input: String = "",
    val selectedLabels: List<Label> = emptyList(),
    val suggestions: List<String> = emptyList(),
)
