package com.tenmilelabs.chefai.recipes.domain.model

import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
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
    /** On-device copy of [imageUrl], if cached at import time. See [com.tenmilelabs.chefai.core.domain.model.Recipe.localImagePath]. */
    val localImagePath: String? = null,
    val prepTimeMinutes: String = "",
    val cookTimeMinutes: String = "",
    val servings: String = "",
    /** Form-input string for [com.tenmilelabs.chefai.core.domain.model.Recipe.caloriesPerServing]; blank means not entered. */
    val caloriesPerServing: String = "",
    /** Form-input string for [com.tenmilelabs.chefai.core.domain.model.Recipe.proteinGramsPerServing]; blank means not entered. */
    val proteinGramsPerServing: String = "",
    /** See [com.tenmilelabs.chefai.core.domain.model.Recipe.languageCode]. */
    val languageCode: String? = null,
    /**
     * `true` when [languageCode] is a guess the user hasn't confirmed yet — an import whose page
     * gave no confident language signal. The editor asks before saving. Never set for a recipe
     * written by hand or one already saved.
     */
    val languageNeedsConfirmation: Boolean = false,
    val externalUrl: String = "",
    /**
     * Defaults to [RecipePrivacy.PRIVATE]: a recipe the user writes stays theirs until they say
     * otherwise. A URL import uses the same default — it is third-party content the user did not
     * write — see [com.tenmilelabs.chefai.recipes.data.mapper.toRecipeDraft].
     */
    val privacy: RecipePrivacy = RecipePrivacy.PRIVATE,
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val labels: List<Label> = emptyList(),
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
)
