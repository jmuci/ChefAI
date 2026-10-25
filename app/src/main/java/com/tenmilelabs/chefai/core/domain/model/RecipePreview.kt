package com.tenmilelabs.chefai.core.domain.model

import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import java.util.UUID

/**
 * Total (prep + cook) time, in minutes, at or under which a recipe counts as "Quick & Easy" — the one
 * definition shared by the Recipes tab's `RecipeFilter.QUICK_EASY` chip and the Search tab's
 * `SearchCategory.QUICK_EASY`. Inclusive: a 30-minute recipe qualifies. A recipe with no time recorded
 * (total `0`) never qualifies; callers check `total in 1..QUICK_EASY_MAX_TOTAL_MINUTES`.
 */
const val QUICK_EASY_MAX_TOTAL_MINUTES = 30

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
