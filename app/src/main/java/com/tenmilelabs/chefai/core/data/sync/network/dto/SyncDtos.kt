package com.tenmilelabs.chefai.core.data.sync.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SyncPushRequest(
    val recipes: List<SyncRecipeDto>
)

@Serializable
data class SyncRecipeDto(
    val uuid: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val imageUrlThumbnail: String,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    val creatorId: String,
    val recipeExternalUrl: String?,
    val privacy: String,
    val updatedAt: Long,
    val deletedAt: Long?,
    val steps: List<SyncRecipeStepDto>,
    val ingredients: List<SyncRecipeIngredientDto>,
    val tagIds: List<String>,
    val labelIds: List<String>
)

@Serializable
data class SyncRecipeStepDto(
    val uuid: String,
    val orderIndex: Int,
    val instruction: String
)

@Serializable
data class SyncRecipeIngredientDto(
    val ingredientId: String,
    val quantity: Double,
    val unit: String
)

@Serializable
data class SyncPushResponse(
    val accepted: List<AcceptedEntityDto>,
    val conflicts: List<ConflictEntityDto>,
    val errors: List<SyncErrorDto>,
    val serverTimestamp: Long
)

@Serializable
data class AcceptedEntityDto(
    val uuid: String,
    val serverUpdatedAt: Long
)

@Serializable
data class ConflictEntityDto(
    val uuid: String,
    val reason: String,
    val serverVersion: SyncRecipeDto
)

@Serializable
data class SyncErrorDto(
    val uuid: String,
    val reason: String,
    val message: String
)

@Serializable
data class SyncPullResponse(
    val recipes: List<SyncRecipeDto>,
    val serverTimestamp: Long,
    val hasMore: Boolean
)
