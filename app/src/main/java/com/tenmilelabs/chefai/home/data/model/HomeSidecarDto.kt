package com.tenmilelabs.chefai.home.data.model

import kotlinx.serialization.Serializable

/**
 * Sidecar payload bundled alongside the SDUI home layout response.
 *
 * The layout references recipes by [SidecarRecipeDto.uuid]. The client upserts
 * these recipes (plus their tags, labels, and creator) into Room before rendering,
 * guaranteeing that every card has data on a fresh install without waiting for
 * the user's own recipe sync to complete.
 */
@Serializable
data class HomeSidecarDto(
    val recipes: List<SidecarRecipeDto> = emptyList(),
    val tags: List<SidecarTagDto> = emptyList(),
    val labels: List<SidecarLabelDto> = emptyList(),
    val creators: List<SidecarCreatorDto> = emptyList(),
)

@Serializable
data class SidecarRecipeDto(
    val uuid: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val imageUrlThumbnail: String,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    val creatorId: String,
    val privacy: String = "PUBLIC",
    /** Epoch-millis of the layout publish timestamp. Used as [RecipeEntity.updatedAt]. */
    val updatedAt: Long,
    val tagIds: List<String> = emptyList(),
    val labelIds: List<String> = emptyList(),
    val ingredients: List<SidecarIngredient> = emptyList(),
    val steps: List<SidecarStep> = emptyList(),
)

@Serializable
data class SidecarIngredient(
    val name: String,
    val quantity: Double,
    val unit: String,
)

@Serializable
data class SidecarStep(
    val orderIndex: Int,
    val instruction: String,
)

@Serializable
data class SidecarTagDto(val uuid: String, val displayName: String)

@Serializable
data class SidecarLabelDto(val uuid: String, val displayName: String)

@Serializable
data class SidecarCreatorDto(
    val uuid: String,
    val displayName: String,
    val avatarUrl: String? = null,
)
