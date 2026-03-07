package com.tenmilelabs.chefai.recipes.data.mapper

import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.User
import com.tenmilelabs.chefai.recipes.data.network.model.NetworkRecipe
import com.tenmilelabs.chefai.recipes.data.network.model.NetworkRecipeList
import timber.log.Timber
import java.util.UUID

/**
 * Maps network DTOs to domain models.
 */

fun NetworkRecipe.toDomain(): Recipe {
    Timber.tag("NetworkDomainMap").d(
        "📡 Mapping NetworkRecipe to Recipe domain model:\n" +
        "  Title: $title\n" +
        "  Image URL (from network): $imageUrl\n" +
        "  Image Thumbnail (from network): $imageUrlThumbnail\n" +
        "  Image URL is null/empty: ${imageUrl.isEmpty()}\n" +
        "  Thumbnail is null/empty: ${imageUrlThumbnail.isEmpty()}"
    )

    return Recipe(
        uuid = UUID.fromString(uuid),
        title = title,
        description = description,
        imageUrl = imageUrl,
        imageUrlThumbnail = imageUrlThumbnail,
        prepTimeMinutes = preparationTimeMinutes,
        cookTimeMinutes = 0, // Not available in network DTO
        servings = 0, // Not available in network DTO
        creator = User(UUID.randomUUID(), "", "", ""), // Not available in network DTO
        recipeExternalUrl = recipeUrl,
        ingredients = emptyList(),
        steps = emptyList(),
        tags = emptyList(),
        labels = emptyList(),
        updatedAt = System.currentTimeMillis() // Or a default value
    ).also {
        Timber.tag("NetworkDomainMap").d(
            "✅ Recipe domain model created:\n" +
            "  Title: ${it.title}\n" +
            "  Image URL (in domain): ${it.imageUrl}\n" +
            "  Image Thumbnail (in domain): ${it.imageUrlThumbnail}"
        )
    }
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