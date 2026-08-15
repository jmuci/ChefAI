package com.tenmilelabs.chefai.search.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class RecipeSearchResponseDto(
    val query: String,
    val results: List<RecipeSearchResultDto>,
    val hasMore: Boolean,
)

@Serializable
data class RecipeSearchResultDto(
    val uuid: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val imageUrlThumbnail: String,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    val creatorId: String,
    val privacy: String,
    val updatedAt: Long,
    val tags: List<RecipeSearchTagDto>,
    val labels: List<RecipeSearchLabelDto>,
)

@Serializable
data class RecipeSearchTagDto(val uuid: String, val displayName: String)

@Serializable
data class RecipeSearchLabelDto(val uuid: String, val displayName: String)
