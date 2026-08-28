package com.tenmilelabs.chefai.core.domain.model

import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import java.util.UUID

/**
 * A simplified, business-oriented representation of a recipe, to be used in recipe lists.
 * Details like ingredients or steps have been stripped for efficiency.
 */
data class RecipePreview(
    val uuid: UUID,
    val title: String,
    val description: String,
    val imageUrlThumbnail: String,
    /** On-device copy of the thumbnail, if cached at import time. See [Recipe.localImagePath]. */
    val localImagePath: String? = null,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    /** See [Recipe.caloriesPerServing]. `null` means unknown. */
    val caloriesPerServing: Int? = null,
    /** See [Recipe.proteinGramsPerServing]. `null` means unknown. */
    val proteinGramsPerServing: Int? = null,
    val creatorId: UUID,
    val privacy: RecipePrivacy = RecipePrivacy.PUBLIC,
    val tags: List<Tag>,
    val labels: List<Label>,
)
