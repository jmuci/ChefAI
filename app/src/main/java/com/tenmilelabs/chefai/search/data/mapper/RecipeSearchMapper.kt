package com.tenmilelabs.chefai.search.data.mapper

import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.search.data.network.dto.RecipeSearchResultDto
import java.util.UUID

/**
 * Maps a search result directly to the domain model — no Room write, no sidecar upsert. Room is
 * only consulted reactively, on tap or bookmark (see `RecipeSearchViewModel.onSaveToCollection`).
 * [RecipePreview.localImagePath] is always null here: a search result was never cached on this
 * device, so `recipeImageModel()` falls back to the remote thumbnail, exactly as intended.
 */
fun RecipeSearchResultDto.toRecipePreview(): RecipePreview = RecipePreview(
    uuid = UUID.fromString(uuid),
    title = title,
    description = description,
    imageUrlThumbnail = imageUrlThumbnail,
    localImagePath = null,
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    servings = servings,
    creatorId = UUID.fromString(creatorId),
    privacy = RecipePrivacy.valueOf(privacy),
    tags = tags.map { Tag(UUID.fromString(it.uuid), it.displayName) },
    labels = labels.map { Label(UUID.fromString(it.uuid), it.displayName) },
)
