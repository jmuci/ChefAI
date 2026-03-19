package com.tenmilelabs.chefai.home.data.mapper

import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.LabelEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.core.data.local.room.TagEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.home.data.model.SidecarCreatorDto
import com.tenmilelabs.chefai.home.data.model.SidecarIngredient
import com.tenmilelabs.chefai.home.data.model.SidecarLabelDto
import com.tenmilelabs.chefai.home.data.model.SidecarRecipeDto
import com.tenmilelabs.chefai.home.data.model.SidecarStep
import com.tenmilelabs.chefai.home.data.model.SidecarTagDto
import java.util.UUID

fun SidecarRecipeDto.toRecipeEntity(): RecipeEntity = RecipeEntity(
    uuid = UUID.fromString(uuid),
    title = title,
    description = description,
    imageUrl = imageUrl,
    imageUrlThumbnail = imageUrlThumbnail,
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    servings = servings,
    creatorId = UUID.fromString(creatorId),
    recipeExternalUrl = null,
    privacy = RecipePrivacy.valueOf(privacy.uppercase()),
    version = 1,
    updatedAt = updatedAt,
    deletedAt = null,
    syncState = SyncState.SYNCED,
)

fun SidecarTagDto.toTagEntity(): TagEntity = TagEntity(
    uuid = UUID.fromString(uuid),
    displayName = displayName,
    updatedAt = 0L,
    deletedAt = null,
    syncState = SyncState.SYNCED,
)

fun SidecarLabelDto.toLabelEntity(): LabelEntity = LabelEntity(
    uuid = UUID.fromString(uuid),
    displayName = displayName,
    updatedAt = 0L,
    deletedAt = null,
    syncState = SyncState.SYNCED,
)

fun SidecarCreatorDto.toUserEntity(): UserEntity = UserEntity(
    uuid = UUID.fromString(uuid),
    displayName = displayName,
    email = "",
    avatarUrl = avatarUrl.orEmpty(),
    updatedAt = 0L,
    deletedAt = null,
    syncState = SyncState.SYNCED,
)

fun SidecarIngredient.toIngredientEntity(now: Long): IngredientEntity = IngredientEntity(
    uuid = UUID.nameUUIDFromBytes(name.lowercase().trim().toByteArray()),
    displayName = name,
    allergenId = null,
    sourcePrimaryId = null,
    updatedAt = now,
    deletedAt = null,
    syncState = SyncState.SYNCED,
)

fun SidecarIngredient.toRecipeIngredientEntity(recipeId: UUID, now: Long): RecipeIngredientEntity {
    val ingredientId = UUID.nameUUIDFromBytes(name.lowercase().trim().toByteArray())
    return RecipeIngredientEntity(
        recipeId = recipeId,
        ingredientId = ingredientId,
        quantity = quantity,
        unit = unit,
        updatedAt = now,
        deletedAt = null,
        syncState = SyncState.SYNCED,
    )
}

fun SidecarStep.toRecipeStepEntity(recipeId: UUID, now: Long): RecipeStepEntity = RecipeStepEntity(
    uuid = UUID.nameUUIDFromBytes("$recipeId:$orderIndex".toByteArray()),
    recipeId = recipeId,
    orderIndex = orderIndex,
    instruction = instruction,
    updatedAt = now,
    deletedAt = null,
    syncState = SyncState.SYNCED,
)
