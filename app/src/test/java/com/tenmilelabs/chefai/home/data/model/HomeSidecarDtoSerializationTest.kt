package com.tenmilelabs.chefai.home.data.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class HomeSidecarDtoSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `SidecarRecipeDto with ingredients and steps deserializes correctly`() {
        val raw = """
            {
                "uuid": "dead0000-cafe-4000-8000-000000000101",
                "title": "Herb-Crusted Chicken",
                "description": "Juicy roasted chicken.",
                "imageUrl": "https://example.com/chicken.jpg",
                "imageUrlThumbnail": "https://example.com/chicken-thumb.jpg",
                "prepTimeMinutes": 15,
                "cookTimeMinutes": 40,
                "servings": 4,
                "creatorId": "dead0000-cafe-4000-8000-000000000001",
                "privacy": "PUBLIC",
                "updatedAt": 1773792000000,
                "tagIds": [],
                "labelIds": [],
                "ingredients": [
                    { "name": "Chicken breast", "quantity": 500.0, "unit": "g" },
                    { "name": "Olive oil", "quantity": 2.0, "unit": "tbsp" }
                ],
                "steps": [
                    { "orderIndex": 0, "instruction": "Preheat oven to 200°C." },
                    { "orderIndex": 1, "instruction": "Coat chicken with oil and herbs." },
                    { "orderIndex": 2, "instruction": "Bake for 40 minutes." }
                ]
            }
        """.trimIndent()

        val result = json.decodeFromString<SidecarRecipeDto>(raw)

        assertThat(result.uuid).isEqualTo("dead0000-cafe-4000-8000-000000000101")
        assertThat(result.title).isEqualTo("Herb-Crusted Chicken")

        assertThat(result.ingredients).hasSize(2)
        val firstIngredient = result.ingredients[0]
        assertThat(firstIngredient.name).isEqualTo("Chicken breast")
        assertThat(firstIngredient.quantity).isEqualTo(500.0)
        assertThat(firstIngredient.unit).isEqualTo("g")

        assertThat(result.steps).hasSize(3)
        val firstStep = result.steps[0]
        assertThat(firstStep.orderIndex).isEqualTo(0)
        assertThat(firstStep.instruction).isEqualTo("Preheat oven to 200°C.")
    }

    @Test
    fun `SidecarRecipeDto without ingredients and steps defaults to emptyList`() {
        val raw = """
            {
                "uuid": "dead0000-cafe-4000-8000-000000000101",
                "title": "Simple Recipe",
                "description": "No frills.",
                "imageUrl": "https://example.com/img.jpg",
                "imageUrlThumbnail": "https://example.com/img-thumb.jpg",
                "prepTimeMinutes": 5,
                "cookTimeMinutes": 10,
                "servings": 1,
                "creatorId": "dead0000-cafe-4000-8000-000000000001",
                "updatedAt": 1773792000000
            }
        """.trimIndent()

        val result = json.decodeFromString<SidecarRecipeDto>(raw)

        assertThat(result.ingredients).isEmpty()
        assertThat(result.steps).isEmpty()
    }

    @Test
    fun `HomeSidecarDto with recipes containing ingredients and steps deserializes correctly`() {
        val raw = """
            {
                "recipes": [
                    {
                        "uuid": "dead0000-cafe-4000-8000-000000000101",
                        "title": "Test Recipe",
                        "description": "A test.",
                        "imageUrl": "https://example.com/img.jpg",
                        "imageUrlThumbnail": "https://example.com/img-thumb.jpg",
                        "prepTimeMinutes": 10,
                        "cookTimeMinutes": 20,
                        "servings": 2,
                        "creatorId": "dead0000-cafe-4000-8000-000000000001",
                        "updatedAt": 1773792000000,
                        "ingredients": [
                            { "name": "Salt", "quantity": 1.0, "unit": "tsp" }
                        ],
                        "steps": [
                            { "orderIndex": 0, "instruction": "Season with salt." }
                        ]
                    }
                ],
                "creators": [],
                "tags": [],
                "labels": []
            }
        """.trimIndent()

        val result = json.decodeFromString<HomeSidecarDto>(raw)

        assertThat(result.recipes).hasSize(1)
        val recipe = result.recipes[0]
        assertThat(recipe.ingredients).hasSize(1)
        assertThat(recipe.ingredients[0].name).isEqualTo("Salt")
        assertThat(recipe.steps).hasSize(1)
        assertThat(recipe.steps[0].instruction).isEqualTo("Season with salt.")
    }
}
