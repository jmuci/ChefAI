package com.tenmilelabs.chefai.core.data.sync.mapper

import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeIngredientDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeStepDto
import java.util.UUID

// --- Push direction: Room entities → DTO ---

fun RecipeEntity.toSyncDto(
    steps: List<RecipeStepEntity>,
    ingredients: List<RecipeIngredientEntity>,
    tagCrossRefs: List<RecipeTagCrossRef>,
    labelCrossRefs: List<RecipeLabelCrossRef>
): SyncRecipeDto = SyncRecipeDto(
    uuid = uuid.toString(),
    title = title,
    description = description,
    imageUrl = imageUrl,
    imageUrlThumbnail = imageUrlThumbnail,
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    servings = servings,
    creatorId = creatorId.toString(),
    recipeExternalUrl = recipeExternalUrl,
    privacy = privacy.name,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    steps = steps.map { it.toSyncDto() },
    ingredients = ingredients.map { it.toSyncDto() },
    tagIds = tagCrossRefs.map { it.tagId.toString() },
    labelIds = labelCrossRefs.map { it.labelId.toString() }
)

fun RecipeStepEntity.toSyncDto(): SyncRecipeStepDto = SyncRecipeStepDto(
    uuid = uuid.toString(),
    orderIndex = orderIndex,
    instruction = instruction
)

fun RecipeIngredientEntity.toSyncDto(): SyncRecipeIngredientDto = SyncRecipeIngredientDto(
    ingredientId = ingredientId.toString(),
    quantity = quantity,
    unit = unit
)

// --- Pull direction: DTO → Room entities ---

fun SyncRecipeDto.toRecipeEntity(): RecipeEntity = RecipeEntity(
    uuid = UUID.fromString(uuid),
    title = title,
    description = description,
    imageUrl = imageUrl,
    imageUrlThumbnail = imageUrlThumbnail,
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    servings = servings,
    creatorId = UUID.fromString(creatorId),
    recipeExternalUrl = recipeExternalUrl,
    privacy = RecipePrivacy.valueOf(privacy.uppercase()),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = SyncState.SYNCED
)

fun SyncRecipeDto.toStepEntities(): List<RecipeStepEntity> =
    steps.map { step ->
        RecipeStepEntity(
            uuid = UUID.fromString(step.uuid),
            recipeId = UUID.fromString(uuid),
            orderIndex = step.orderIndex,
            instruction = step.instruction,
            updatedAt = updatedAt,
            deletedAt = null,
            syncState = SyncState.SYNCED
        )
    }

fun SyncRecipeDto.toIngredientEntities(): List<RecipeIngredientEntity> =
    ingredients.map { ing ->
        RecipeIngredientEntity(
            recipeId = UUID.fromString(uuid),
            ingredientId = UUID.fromString(ing.ingredientId),
            quantity = ing.quantity,
            unit = ing.unit,
            updatedAt = updatedAt,
            deletedAt = null,
            syncState = SyncState.SYNCED
        )
    }

fun SyncRecipeDto.toTagCrossRefs(): List<RecipeTagCrossRef> =
    tagIds.map { tagId ->
        RecipeTagCrossRef(
            recipeId = UUID.fromString(uuid),
            tagId = UUID.fromString(tagId),
            updatedAt = updatedAt,
            deletedAt = null,
            syncState = SyncState.SYNCED
        )
    }

fun SyncRecipeDto.toLabelCrossRefs(): List<RecipeLabelCrossRef> =
    labelIds.map { labelId ->
        RecipeLabelCrossRef(
            recipeId = UUID.fromString(uuid),
            labelId = UUID.fromString(labelId),
            updatedAt = updatedAt,
            deletedAt = null,
            syncState = SyncState.SYNCED
        )
    }
