package com.tenmilelabs.chefai.recipes.data.mapper

import com.tenmilelabs.chefai.core.data.local.room.RecipeDraftEntity
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.core.domain.model.User
import com.tenmilelabs.chefai.recipes.domain.model.RecipeDraft
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// --- Serializable DTOs for JSON storage in RecipeDraftEntity ---

@Serializable
internal data class DraftIngredientDto(
    val ingredientId: String,
    val ingredientDisplayName: String,
    val quantity: Double,
    val unit: String,
    val allergenName: String? = null,
    val srcCategory: String? = null,
    val srcSubcategory: String? = null,
)

@Serializable
internal data class DraftStepDto(
    val uuid: String,
    val orderIndex: Int,
    val instruction: String,
)

@Serializable
internal data class DraftTagDto(
    val uuid: String,
    val displayName: String,
)

@Serializable
internal data class DraftLabelDto(
    val uuid: String,
    val displayName: String,
)

// Lenient JSON instance for draft serialization — ignores unknown keys for forward compatibility
private val draftJson = Json { ignoreUnknownKeys = true }

// --- Domain ↔ DTO mappers ---

private fun RecipeIngredient.toDto(): DraftIngredientDto = DraftIngredientDto(
    ingredientId = ingredientId.toString(),
    ingredientDisplayName = ingredientDisplayName,
    quantity = quantity,
    unit = unit,
    allergenName = allergenName,
    srcCategory = srcCategory,
    srcSubcategory = srcSubcategory,
)

private fun DraftIngredientDto.toDomain(): RecipeIngredient = RecipeIngredient(
    ingredientId = java.util.UUID.fromString(ingredientId),
    ingredientDisplayName = ingredientDisplayName,
    quantity = quantity,
    unit = unit,
    allergenName = allergenName,
    srcCategory = srcCategory,
    srcSubcategory = srcSubcategory,
)

private fun RecipeStep.toDto(): DraftStepDto = DraftStepDto(
    uuid = uuid.toString(),
    orderIndex = orderIndex,
    instruction = instruction,
)

private fun DraftStepDto.toDomain(): RecipeStep = RecipeStep(
    uuid = java.util.UUID.fromString(uuid),
    orderIndex = orderIndex,
    instruction = instruction,
)

private fun Tag.toDto(): DraftTagDto = DraftTagDto(
    uuid = uuid.toString(),
    displayName = displayName,
)

private fun DraftTagDto.toDomain(): Tag = Tag(
    uuid = java.util.UUID.fromString(uuid),
    displayName = displayName,
)

private fun Label.toDto(): DraftLabelDto = DraftLabelDto(
    uuid = uuid.toString(),
    displayName = displayName,
)

private fun DraftLabelDto.toDomain(): Label = Label(
    uuid = java.util.UUID.fromString(uuid),
    displayName = displayName,
)

// --- Public mapper functions ---

/**
 * Converts a [Recipe] to a [RecipeDraft] for editing.
 * Numeric fields are converted to strings for form input.
 */
fun Recipe.toRecipeDraft(): RecipeDraft = RecipeDraft(
    recipeId = uuid,
    isNewRecipe = false,
    title = title,
    description = description,
    imageUrl = imageUrl,
    selectedImageUri = null,
    prepTimeMinutes = prepTimeMinutes.toString(),
    cookTimeMinutes = cookTimeMinutes.toString(),
    servings = servings.toString(),
    externalUrl = recipeExternalUrl.orEmpty(),
    ingredients = ingredients,
    steps = steps,
    tags = tags,
    labels = labels,
    version = version,
    updatedAt = updatedAt,
)

/**
 * Converts a [RecipeDraft] to a persistable [RecipeDraftEntity].
 * Lists are serialized to JSON strings.
 */
fun RecipeDraft.toRecipeDraftEntity(): RecipeDraftEntity = RecipeDraftEntity(
    recipeId = recipeId,
    isNewRecipe = isNewRecipe,
    title = title,
    description = description,
    imageUrl = imageUrl,
    selectedImageUri = selectedImageUri,
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    servings = servings,
    externalUrl = externalUrl,
    ingredientsJson = draftJson.encodeToString(ingredients.map { it.toDto() }),
    stepsJson = draftJson.encodeToString(steps.map { it.toDto() }),
    tagsJson = draftJson.encodeToString(tags.map { it.toDto() }),
    labelsJson = draftJson.encodeToString(labels.map { it.toDto() }),
    version = version,
    updatedAt = updatedAt,
)

/**
 * Converts a [RecipeDraftEntity] back to a [RecipeDraft].
 * JSON strings are deserialized to domain lists.
 */
fun RecipeDraftEntity.toRecipeDraft(): RecipeDraft = RecipeDraft(
    recipeId = recipeId,
    isNewRecipe = isNewRecipe,
    title = title,
    description = description,
    imageUrl = imageUrl,
    selectedImageUri = selectedImageUri,
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    servings = servings,
    externalUrl = externalUrl,
    ingredients = draftJson.decodeFromString<List<DraftIngredientDto>>(ingredientsJson).map { it.toDomain() },
    steps = draftJson.decodeFromString<List<DraftStepDto>>(stepsJson).map { it.toDomain() },
    tags = draftJson.decodeFromString<List<DraftTagDto>>(tagsJson).map { it.toDomain() },
    labels = draftJson.decodeFromString<List<DraftLabelDto>>(labelsJson).map { it.toDomain() },
    version = version,
    updatedAt = updatedAt,
)

/**
 * Converts a [RecipeDraft] back to a [Recipe] for saving to the repository.
 * Requires the [creator] since drafts don't store full user objects.
 *
 * @throws NumberFormatException if numeric string fields contain invalid values.
 */
fun RecipeDraft.toRecipe(creator: User): Recipe = Recipe(
    uuid = recipeId,
    title = title,
    description = description,
    imageUrl = imageUrl,
    imageUrlThumbnail = imageUrl,
    prepTimeMinutes = prepTimeMinutes.toInt(),
    cookTimeMinutes = cookTimeMinutes.toInt(),
    servings = servings.toInt(),
    creator = creator,
    recipeExternalUrl = externalUrl.ifBlank { null },
    privacy = RecipePrivacy.PUBLIC,
    version = version,
    ingredients = ingredients,
    steps = steps,
    tags = tags,
    labels = labels,
    updatedAt = updatedAt,
)
