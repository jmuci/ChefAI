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
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    val creatorId: UUID,
    val privacy: RecipePrivacy = RecipePrivacy.PUBLIC,
    val tags: List<Tag>,
    val labels: List<Label>,
)
