package com.tenmilelabs.chefai.data.mapper

import com.tenmilelabs.chefai.data.source.network.NetworkRecipe
import com.tenmilelabs.chefai.data.source.network.NetworkRecipeList
import com.tenmilelabs.chefai.domain.model.Recipe
import com.tenmilelabs.chefai.domain.model.User
import java.util.UUID

/**
 * Maps network DTOs to domain models.
 */

fun NetworkRecipe.toDomain(): Recipe {
    return Recipe(
        uuid = UUID.fromString(uuid),
        title = title,
        description = description,
        imageUrl = imageUrl,
        imageUrlThumbnail = imageUrlThumbnail,
        prepTimeMinutes = preparationTimeMinutes,
        cookTimeMinutes = 0, // Not available in network DTO
        servings = 0, // Not available in network DTO
        creator = User(UUID.randomUUID(), "", null, null), // Not available in network DTO
        recipeExternalUrl = recipeUrl,
        ingredients = emptyList(),
        steps = emptyList(),
        tags = emptyList(),
        labels = emptyList(),
        updatedAt = System.currentTimeMillis() // Or a default value
    )
}

fun List<NetworkRecipe>.toDomain(): List<Recipe> = map (NetworkRecipe::toDomain)
fun NetworkRecipeList.toDomain() = this.recipes.toDomain()
fun Recipe.toNetwork(): NetworkRecipe {
    return NetworkRecipe(
        uuid = uuid.toString(),
        title = title,
        label = "", // You might want to map this from recipe.labels
        description = description,
        preparationTimeMinutes = prepTimeMinutes,
        recipeUrl = recipeExternalUrl ?: "",
        imageUrl = imageUrl,
        imageUrlThumbnail = imageUrlThumbnail
    )
}

fun List<Recipe>.toNetwork(): List<NetworkRecipe> = map { it.toNetwork() }