package com.tenmilelabs.chefai.recipes.ui.create

import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.model.Tag

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

data class RecipeFields(
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val selectedImageUri: String? = null, // Local image URI from picker
    val prepTimeMinutes: String = "",
    val cookTimeMinutes: String = "",
    val servings: String = "",
    val externalUrl: String = "",
)

/**
 * UiState for the Create Recipe screen
 */
data class CreateRecipeUiState(
    val recipeFields: RecipeFields = RecipeFields(),
    val ingredients: IngredientsFields = IngredientsFields(),
    val steps: StepsFields = StepsFields(),
    val tags: TagsFields = TagsFields(),
    val labels: LabelsFields = LabelsFields(),
    // Validation & Status
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isFormValid: Boolean = false
)
