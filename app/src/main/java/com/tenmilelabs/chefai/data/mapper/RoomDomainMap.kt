package com.tenmilelabs.chefai.data.mapper

import com.tenmilelabs.chefai.data.source.local.room.RecipeEntity
import com.tenmilelabs.chefai.domain.model.Recipe

/**
 * Data model mapping extension functions. There are three model types:
 *
 * - Recipe: Domain Entity model exposed to other layers in the architecture.
 * Obtained using `toDomain`. *
 * - RecipeEntity: Room Data Entity used to represent a recipe stored locally in a database. Obtained
 * using `toRoomEntity`.
 *
 */
fun Recipe.toRecipeEntity(): RecipeEntity = RecipeEntity(
    title = title,
    label = label,
    description = description,
    prepTime = prepTime,
    recipeExternalUrl = recipeUrl,
    imageUrl = imageUrl,
    imageUrlThumbnail = thumbnailUrl,
    uuid = uuid,
)

fun List<Recipe>.toRoomEntity() = map(Recipe::toRecipeEntity)

fun RecipeEntity.toDomain() = Recipe(
    title = title,
    label = label,
    description = description,
    prepTime = prepTime,
    recipeUrl = recipeExternalUrl,
    imageUrl = imageUrl,
    thumbnailUrl = imageUrlThumbnail,
    uuid = uuid,
)

fun List<RecipeEntity>.toDomain() = map(RecipeEntity::toDomain)
