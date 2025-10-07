package com.tenmilelabs.chefai.data.mapper

import com.tenmilelabs.chefai.data.source.network.NetworkRecipe
import com.tenmilelabs.chefai.data.source.network.NetworkRecipeList
import com.tenmilelabs.chefai.domain.model.Recipe
/**
 * Data model mapping extension functions. There are three model types:
 *
 * - Recipe: Domain Entity model exposed to other layers in the architecture.
 * Obtained using `toDomain`.
 *
 * - NetworkRecipe: Internal model used to represent a recipe from the network. Obtained using
 * `toNetwork`.
 *
 */
fun Recipe.toNetworkRecipe() : NetworkRecipe = NetworkRecipe(
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

fun NetworkRecipe.toDomain() = Recipe(
    uuid = uuid,
    title = title,
    label = label,
    description = description,
    prepTime = preparationTimeMinutes,
    recipeUrl = recipeUrl,
    imageUrl = imageUrl,
    thumbnailUrl = imageUrlThumbnail,
)

fun List<NetworkRecipe>.toDomain() = map(NetworkRecipe::toDomain)
fun NetworkRecipeList.toDomain() = this.recipes.map(NetworkRecipe::toDomain)