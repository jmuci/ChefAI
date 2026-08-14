package com.tenmilelabs.chefai.core.domain.model

import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
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
    /**
     * Absolute path to an on-device copy of [imageUrl], downloaded at import time for sources whose
     * CDN 403s a plain HTTP client (see [com.tenmilelabs.chefai.recipes.domain.usecase.CacheRecipeImage]).
     * Device-local, so it is never synced — see https://github.com/jmuci/ChefAI/issues/132.
     */
    val localImagePath: String? = null,
    /**
     * Content hash of this recipe's image on the backend, or `null` if it was never uploaded.
     *
     * Carried on the domain model only so it survives a round trip through
     * [com.tenmilelabs.chefai.recipes.data.mapper.toRoomEntity], whose full-row write would
     * otherwise reset it. Nothing in the UI reads it.
     */
    val imageBlobId: String? = null,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    val creator: User,
    val recipeExternalUrl: String?,
    val privacy: RecipePrivacy = RecipePrivacy.PUBLIC,
    val version: Int = 1,
    val ingredients: List<RecipeIngredient>,
    val steps: List<RecipeStep>,
    val tags: List<Tag>,
    val labels: List<Label>,
    val updatedAt: Long,
)
