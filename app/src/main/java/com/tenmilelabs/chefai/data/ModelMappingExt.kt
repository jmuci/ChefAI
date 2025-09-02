package com.tenmilelabs.chefai.data

import com.tenmilelabs.chefai.data.source.local.RecipeEntity
import com.tenmilelabs.chefai.data.source.network.NetworkRecipe
import com.tenmilelabs.chefai.data.source.network.NetworkRecipeList


/**
 * Data model mapping extension functions. There are three model types:
 *
 * - Recipe: External model exposed to other layers in the architecture.
 * Obtained using `toExternal`.
 *
 * - NetworkRecipe: Internal model used to represent a recipe from the network. Obtained using
 * `toNetwork`.
 *
 * - Recipe: Internal model used to represent a recipe stored locally in a database. Obtained
 * using `toLocal`.
 *
 */

// Recipe to RecipeEntity mapper
fun Recipe.toRecipeEntity(): RecipeEntity = RecipeEntity(
    title = title,
    label = label,
    description = description,
    prepTime = prepTime,
    recipeUrl = recipeUrl,
    imageUrl = imageUrl,
    imageUrlThumbnail = thumbnailUrl,
    uuid = uuid,
)

fun List<Recipe>.toLocal() = map(Recipe::toRecipeEntity)

fun Recipe.toNetworkRecipe(): NetworkRecipe = NetworkRecipe(
    uuid = uuid,
    title = title,
    label = label,
    description = description,
    preparationTimeMinutes = prepTime,
    recipeUrl = recipeUrl,
    imageUrl = imageUrl,
    imageUrlThumbnail = thumbnailUrl,
)

fun List<Recipe>.toNetwork() = map(Recipe::toNetworkRecipe)

fun RecipeEntity.toExternal() = Recipe(
    title = title,
    label = label,
    description = description,
    prepTime = prepTime,
    recipeUrl = recipeUrl,
    imageUrl = imageUrl,
    thumbnailUrl = imageUrlThumbnail,
    uuid = uuid,
)

fun List<RecipeEntity>.toExternal() = map(RecipeEntity::toExternal)

fun NetworkRecipe.toRecipe() = Recipe(
    uuid = uuid,
    title = title,
    label = label,
    description = description,
    prepTime = preparationTimeMinutes,
    recipeUrl = recipeUrl,
    imageUrl = imageUrl,
    thumbnailUrl = imageUrlThumbnail,
)

fun List<NetworkRecipe>.toRecipe() = map(NetworkRecipe::toRecipe)
fun NetworkRecipeList.toRecipe() = this.recipes.map(NetworkRecipe::toRecipe)