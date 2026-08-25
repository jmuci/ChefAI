package com.tenmilelabs.chefai.recipes.data.mapper

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.User
import com.tenmilelabs.chefai.recipes.data.network.model.NetworkRecipe
import com.tenmilelabs.chefai.recipes.data.network.model.NetworkRecipeList
import org.junit.Test
import java.util.UUID

class NetworkDomainMapTest {

    private fun networkRecipe(
        uuid: String = UUID.randomUUID().toString(),
        recipeUrl: String = "https://example.com/recipe",
    ) = NetworkRecipe(
        uuid = uuid,
        title = "Chili",
        label = "spicy",
        description = "Warms you up",
        preparationTimeMinutes = 25,
        recipeUrl = recipeUrl,
        imageUrl = "https://example.com/full.jpg",
        imageUrlThumbnail = "https://example.com/thumb.jpg",
    )

    @Test
    fun `maps the fields the network DTO actually carries`() {
        val source = networkRecipe()

        val recipe = source.toDomain()

        assertThat(recipe.uuid).isEqualTo(UUID.fromString(source.uuid))
        assertThat(recipe.title).isEqualTo(source.title)
        assertThat(recipe.description).isEqualTo(source.description)
        assertThat(recipe.imageUrl).isEqualTo(source.imageUrl)
        assertThat(recipe.imageUrlThumbnail).isEqualTo(source.imageUrlThumbnail)
        assertThat(recipe.prepTimeMinutes).isEqualTo(source.preparationTimeMinutes)
        assertThat(recipe.recipeExternalUrl).isEqualTo(source.recipeUrl)
    }

    @Test
    fun `fields absent from the network DTO fall back to safe defaults`() {
        val recipe = networkRecipe().toDomain()

        assertThat(recipe.cookTimeMinutes).isEqualTo(0)
        assertThat(recipe.servings).isEqualTo(0)
        assertThat(recipe.ingredients).isEmpty()
        assertThat(recipe.steps).isEmpty()
        assertThat(recipe.tags).isEmpty()
        assertThat(recipe.labels).isEmpty()
    }

    @Test
    fun `list mapping preserves order and delegates to the single-item mapper`() {
        val sources = listOf(networkRecipe(), networkRecipe())

        val recipes = sources.toDomain()

        assertThat(recipes.map { it.uuid }).containsExactlyElementsIn(
            sources.map { UUID.fromString(it.uuid) }
        ).inOrder()
    }

    @Test
    fun `NetworkRecipeList toDomain unwraps its recipes field`() {
        val sources = listOf(networkRecipe(), networkRecipe())

        val recipes = NetworkRecipeList(recipes = sources).toDomain()

        assertThat(recipes).hasSize(2)
    }

    @Test
    fun `toNetwork carries the recipe id, title and image fields back`() {
        val recipe = Recipe(
            uuid = UuidV7Generator.newId(),
            title = "Chili",
            description = "Warms you up",
            imageUrl = "https://example.com/full.jpg",
            imageUrlThumbnail = "https://example.com/thumb.jpg",
            prepTimeMinutes = 25,
            cookTimeMinutes = 40,
            servings = 4,
            creator = User(UuidV7Generator.newId(), "Chef", "chef@example.com", ""),
            recipeExternalUrl = "https://example.com/recipe",
            ingredients = emptyList(),
            steps = emptyList(),
            tags = emptyList(),
            labels = emptyList(),
            updatedAt = 0L,
        )

        val network = recipe.toNetwork()

        assertThat(network.uuid).isEqualTo(recipe.uuid.toString())
        assertThat(network.title).isEqualTo(recipe.title)
        assertThat(network.description).isEqualTo(recipe.description)
        assertThat(network.preparationTimeMinutes).isEqualTo(recipe.prepTimeMinutes)
        assertThat(network.recipeUrl).isEqualTo(recipe.recipeExternalUrl)
        assertThat(network.imageUrl).isEqualTo(recipe.imageUrl)
        assertThat(network.imageUrlThumbnail).isEqualTo(recipe.imageUrlThumbnail)
    }

    @Test
    fun `toNetwork falls back to an empty string when there is no external url`() {
        val recipe = Recipe(
            uuid = UuidV7Generator.newId(),
            title = "Chili",
            description = "",
            imageUrl = "",
            imageUrlThumbnail = "",
            prepTimeMinutes = 0,
            cookTimeMinutes = 0,
            servings = 0,
            creator = User(UuidV7Generator.newId(), "Chef", "chef@example.com", ""),
            recipeExternalUrl = null,
            ingredients = emptyList(),
            steps = emptyList(),
            tags = emptyList(),
            labels = emptyList(),
            updatedAt = 0L,
        )

        assertThat(recipe.toNetwork().recipeUrl).isEmpty()
    }
}
