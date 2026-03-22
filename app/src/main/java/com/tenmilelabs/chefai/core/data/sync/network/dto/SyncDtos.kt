package com.tenmilelabs.chefai.core.data.sync.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SyncPushRequest(
    val recipes: List<SyncRecipeDto>,
    val bookmarkedRecipes: List<SyncBookmarkPushDto> = emptyList()
)

@Serializable
data class SyncBookmarkPushDto(
    val userId: String,
    val recipeId: String,
    val updatedAt: Long,
    val deletedAt: Long?
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
    val version: Int = 1,
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
    val serverTimestamp: Long,
    val bookmarkedRecipes: List<BookmarkAcceptedDto> = emptyList(),
    val bookmarkErrors: List<BookmarkErrorDto> = emptyList()
)

@Serializable
data class BookmarkAcceptedDto(
    val userId: String,
    val recipeId: String,
    val serverUpdatedAt: Long
)

@Serializable
data class BookmarkErrorDto(
    val recipeId: String,
    val reason: String,
    val message: String
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
data class SyncIngredientDto(
    val uuid: String,
    val displayName: String,
    val allergenId: String?,
    val sourcePrimaryId: String?,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class SyncAllergenDto(
    val uuid: String,
    val displayName: String,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class SyncSourceClassificationDto(
    val uuid: String,
    val category: String,
    val subcategory: String?,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class SyncTagDto(
    val uuid: String,
    val displayName: String,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class SyncLabelDto(
    val uuid: String,
    val displayName: String,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class SyncCreatorDto(
    val uuid: String,
    val displayName: String,
    val email: String? = null,
    val avatarUrl: String? = null,
    val updatedAt: Long,
    val deletedAt: Long?
)

@Serializable
data class SyncPullResponse(
    val recipes: List<SyncRecipeDto>,
    val serverTimestamp: Long,
    val hasMore: Boolean,
    val creators: List<SyncCreatorDto> = emptyList(),
    val allergens: List<SyncAllergenDto> = emptyList(),
    val sourceClassifications: List<SyncSourceClassificationDto> = emptyList(),
    val ingredients: List<SyncIngredientDto> = emptyList(),
    val tags: List<SyncTagDto> = emptyList(),
    val labels: List<SyncLabelDto> = emptyList(),
    val bookmarkedRecipes: List<SyncBookmarkPullDto> = emptyList()
)

@Serializable
data class SyncBookmarkPullDto(
    val userId: String,
    val recipeId: String,
    val updatedAt: Long,
    val deletedAt: Long?
)
