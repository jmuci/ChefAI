package com.tenmilelabs.chefai.core.data.sync.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SyncPushRequest(
    val recipes: List<SyncRecipeDto>,
    val bookmarkedRecipes: List<SyncBookmarkPushDto> = emptyList(),
    val mealPlans: List<SyncMealPlanDto> = emptyList()
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
    /**
     * Content hash of the recipe's image on the backend, or `null` if none was ever uploaded.
     *
     * Server-owned. The server ignores whatever a push carries here and echoes its own value, so a
     * client cannot point a recipe at a blob it did not upload, and the field takes no part in
     * last-writer-wins. Defaulted so a server that predates image upload still deserialises.
     */
    val imageBlobId: String? = null,
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
    val bookmarkErrors: List<BookmarkErrorDto> = emptyList(),
    val mealPlans: MealPlanPushResults = MealPlanPushResults()
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

/**
 * Response body for `GET /api/v1/recipes/{recipeId}` — fetches one recipe aggregate the
 * device hasn't synced yet (ChefAI#186), e.g. a search result tapped before any pull has
 * delivered it. Shares [SyncRecipeDto] and the same reference-data/creator shapes
 * [SyncPullResponse] already carries, so the exact upsert ordering [SyncOrchestrator] uses
 * for a pull page applies here unchanged — see [SyncOrchestrator.fetchAndPersistRecipe].
 */
@Serializable
data class RecipeDetailResponseDto(
    val recipe: SyncRecipeDto,
    val referenceData: SyncReferenceDataDto,
    val creators: List<SyncCreatorDto>
)

@Serializable
data class SyncReferenceDataDto(
    val ingredients: List<SyncIngredientDto> = emptyList(),
    val allergens: List<SyncAllergenDto> = emptyList(),
    val sourceClassifications: List<SyncSourceClassificationDto> = emptyList(),
    val tags: List<SyncTagDto> = emptyList(),
    val labels: List<SyncLabelDto> = emptyList(),
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
    val bookmarkedRecipes: List<SyncBookmarkPullDto> = emptyList(),
    val mealPlans: List<SyncMealPlanDto> = emptyList()
)

@Serializable
data class SyncBookmarkPullDto(
    val userId: String,
    val recipeId: String,
    val updatedAt: Long,
    val deletedAt: Long?
)

// --- Meal Plan Sync DTOs ---

@Serializable
data class SyncMealPlanDto(
    val uuid: String,
    val name: String,
    val status: String,
    val preferencesJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val days: List<SyncMealPlanDayDto>
)

@Serializable
data class SyncMealPlanDayDto(
    val uuid: String,
    val dayIndex: Int,
    val dinnerRecipeId: String?,
    val lunchRecipeId: String?
)

@Serializable
data class MealPlanPushResults(
    val accepted: List<AcceptedEntityDto> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val errors: List<SyncErrorDto> = emptyList()
)

@Serializable
data class GenerateMealPlanResponse(
    val uuid: String,
    val status: String,
    val updatedAt: Long
)

@Serializable
data class GenerateMealPlanStatelessRequest(val preferencesJson: String)

/**
 * Response body for `POST /api/v1/meal-plans/generate` — the anonymous-capable, stateless
 * counterpart to `POST /meal-plans/{id}/generate`. Shaped like [RecipeDetailResponseDto] plus a
 * day list rather than a bare day list alone: an anonymous device has typically never received the
 * assigned recipes, so the server bundles every recipe aggregate the days reference in the same
 * round trip. [creators] sits outside [referenceData] for the same FK-safety reason as
 * [RecipeDetailResponseDto.creators].
 */
@Serializable
data class GenerateMealPlanStatelessResponseDto(
    val days: List<SyncMealPlanDayDto>,
    val recipes: List<SyncRecipeDto>,
    val referenceData: SyncReferenceDataDto,
    val creators: List<SyncCreatorDto>
)
