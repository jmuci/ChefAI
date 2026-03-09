package com.tenmilelabs.chefai.home.data.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test

class ComponentModelSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // region SectionHeader

    @Test
    fun `deserializes SectionHeader correctly`() {
        val raw = """
            {
                "type": "section_header",
                "id": "sh-1",
                "title": "Featured Recipes",
                "subtitle": "Curated for you",
                "actionText": "See all",
                "actionUrl": "https://example.com/recipes"
            }
        """.trimIndent()

        val result = json.decodeFromString<ComponentModel>(raw)

        assertThat(result).isInstanceOf(ComponentModel.SectionHeader::class.java)
        val header = result as ComponentModel.SectionHeader
        assertThat(header.id).isEqualTo("sh-1")
        assertThat(header.title).isEqualTo("Featured Recipes")
        assertThat(header.subtitle).isEqualTo("Curated for you")
        assertThat(header.actionText).isEqualTo("See all")
        assertThat(header.actionUrl).isEqualTo("https://example.com/recipes")
    }

    // endregion

    // region Carousel

    @Test
    fun `deserializes Carousel correctly`() {
        val raw = """
            {
                "type": "carousel",
                "id": "carousel-1",
                "items": [
                    {
                        "type": "large_card",
                        "id": "lc-1",
                        "recipeId": "recipe-abc"
                    }
                ]
            }
        """.trimIndent()

        val result = json.decodeFromString<ComponentModel>(raw)

        assertThat(result).isInstanceOf(ComponentModel.Carousel::class.java)
        val carousel = result as ComponentModel.Carousel
        assertThat(carousel.id).isEqualTo("carousel-1")
        assertThat(carousel.items).hasSize(1)
        assertThat(carousel.items.first()).isInstanceOf(ComponentModel.LargeCard::class.java)
    }

    // endregion

    // region LargeCard

    @Test
    fun `deserializes LargeCard with id and recipeId`() {
        val raw = """
            {
                "type": "large_card",
                "id": "lc-2",
                "recipeId": "recipe-abc"
            }
        """.trimIndent()

        val result = json.decodeFromString<ComponentModel>(raw)

        assertThat(result).isInstanceOf(ComponentModel.LargeCard::class.java)
        val card = result as ComponentModel.LargeCard
        assertThat(card.id).isEqualTo("lc-2")
        assertThat(card.recipeId).isEqualTo("recipe-abc")
    }

    @Test
    fun `LargeCard ignores unknown content fields from old server format`() {
        // Ensure backward compat: old payload with title/description etc. doesn't crash
        val raw = """
            {
                "type": "large_card",
                "id": "lc-legacy",
                "recipeId": "recipe-legacy",
                "title": "Old Title",
                "description": "Old description",
                "imageUrl": "https://example.com/img.jpg",
                "prepTimeMinutes": 20,
                "cookTimeMinutes": 40,
                "servings": 4,
                "labels": ["High Protein"],
                "tags": ["Indian"]
            }
        """.trimIndent()

        val result = json.decodeFromString<ComponentModel>(raw)

        assertThat(result).isInstanceOf(ComponentModel.LargeCard::class.java)
        val card = result as ComponentModel.LargeCard
        assertThat(card.id).isEqualTo("lc-legacy")
        assertThat(card.recipeId).isEqualTo("recipe-legacy")
    }

    // endregion

    // region SquaredCard

    @Test
    fun `deserializes SquaredCard with id and recipeId`() {
        val raw = """
            {
                "type": "squared_card",
                "id": "sc-1",
                "recipeId": "recipe-xyz"
            }
        """.trimIndent()

        val result = json.decodeFromString<ComponentModel>(raw)

        assertThat(result).isInstanceOf(ComponentModel.SquaredCard::class.java)
        val card = result as ComponentModel.SquaredCard
        assertThat(card.id).isEqualTo("sc-1")
        assertThat(card.recipeId).isEqualTo("recipe-xyz")
    }

    // endregion

    // region ListCard

    @Test
    fun `deserializes ListCard with id and recipeId`() {
        val raw = """
            {
                "type": "list_card",
                "id": "list-1",
                "recipeId": "recipe-def"
            }
        """.trimIndent()

        val result = json.decodeFromString<ComponentModel>(raw)

        assertThat(result).isInstanceOf(ComponentModel.ListCard::class.java)
        val card = result as ComponentModel.ListCard
        assertThat(card.id).isEqualTo("list-1")
        assertThat(card.recipeId).isEqualTo("recipe-def")
    }

    // endregion

    // region Unknown type fallback

    @Test
    fun `unknown component type deserializes to Unknown without crashing`() {
        val raw = """
            {
                "type": "future_widget",
                "id": "fw-1",
                "someNewField": "some value"
            }
        """.trimIndent()

        val result = json.decodeFromString<ComponentModel>(raw)

        assertThat(result).isInstanceOf(ComponentModel.Unknown::class.java)
    }

    @Test
    fun `unknown component type preserves id field`() {
        val raw = """
            {
                "type": "banner_v2",
                "id": "banner-99",
                "text": "Hello!"
            }
        """.trimIndent()

        val result = json.decodeFromString<ComponentModel>(raw)

        assertThat(result).isInstanceOf(ComponentModel.Unknown::class.java)
        val unknown = result as ComponentModel.Unknown
        assertThat(unknown.id).isEqualTo("banner-99")
        assertThat(unknown.originalType).isEqualTo("banner_v2")
    }

    // endregion

    // region Optional field defaults

    @Test
    fun `optional fields default to null when absent`() {
        val rawHeader = """
            {
                "type": "section_header",
                "id": "sh-min",
                "title": "Minimal Header"
            }
        """.trimIndent()

        val header = json.decodeFromString<ComponentModel>(rawHeader) as ComponentModel.SectionHeader
        assertThat(header.subtitle).isNull()
        assertThat(header.actionText).isNull()
        assertThat(header.actionUrl).isNull()

        val rawLargeCard = """
            {
                "type": "large_card",
                "id": "lc-min"
            }
        """.trimIndent()

        val largeCard = json.decodeFromString<ComponentModel>(rawLargeCard) as ComponentModel.LargeCard
        assertThat(largeCard.recipeId).isNull()

        val rawSquaredCard = """
            {
                "type": "squared_card",
                "id": "sc-min"
            }
        """.trimIndent()

        val squaredCard = json.decodeFromString<ComponentModel>(rawSquaredCard) as ComponentModel.SquaredCard
        assertThat(squaredCard.recipeId).isNull()

        val rawListCard = """
            {
                "type": "list_card",
                "id": "list-min"
            }
        """.trimIndent()

        val listCard = json.decodeFromString<ComponentModel>(rawListCard) as ComponentModel.ListCard
        assertThat(listCard.recipeId).isNull()
    }

    // endregion

    // region Carousel polymorphic items

    @Test
    fun `carousel items support polymorphic mixed card types`() {
        val raw = """
            {
                "type": "carousel",
                "id": "carousel-mixed",
                "items": [
                    {
                        "type": "large_card",
                        "id": "lc-mix-1",
                        "recipeId": "r1"
                    },
                    {
                        "type": "squared_card",
                        "id": "sc-mix-1",
                        "recipeId": "r2"
                    },
                    {
                        "type": "list_card",
                        "id": "list-mix-1",
                        "recipeId": "r3"
                    }
                ]
            }
        """.trimIndent()

        val result = json.decodeFromString<ComponentModel>(raw) as ComponentModel.Carousel

        assertThat(result.id).isEqualTo("carousel-mixed")
        assertThat(result.items).hasSize(3)
        assertThat(result.items[0]).isInstanceOf(ComponentModel.LargeCard::class.java)
        assertThat(result.items[1]).isInstanceOf(ComponentModel.SquaredCard::class.java)
        assertThat(result.items[2]).isInstanceOf(ComponentModel.ListCard::class.java)
    }

    @Test
    fun `carousel containing unknown component type does not crash`() {
        val raw = """
            {
                "type": "carousel",
                "id": "carousel-with-unknown",
                "items": [
                    {
                        "type": "large_card",
                        "id": "lc-known",
                        "recipeId": "r1"
                    },
                    {
                        "type": "mystery_component",
                        "id": "mystery-1",
                        "payload": "some data"
                    }
                ]
            }
        """.trimIndent()

        val result = json.decodeFromString<ComponentModel>(raw) as ComponentModel.Carousel

        assertThat(result.items).hasSize(2)
        assertThat(result.items[0]).isInstanceOf(ComponentModel.LargeCard::class.java)
        assertThat(result.items[1]).isInstanceOf(ComponentModel.Unknown::class.java)
        val unknown = result.items[1] as ComponentModel.Unknown
        assertThat(unknown.id).isEqualTo("mystery-1")
        assertThat(unknown.originalType).isEqualTo("mystery_component")
    }

    // endregion

    // region HomeLayoutModel

    @Test
    fun `HomeLayoutModel parses schemaVersion and components`() {
        val raw = """
            {
                "schemaVersion": "1.0.0",
                "layoutChecksum": "abc123",
                "components": [
                    {
                        "type": "section_header",
                        "id": "sh-top",
                        "title": "Welcome back"
                    },
                    {
                        "type": "carousel",
                        "id": "carousel-top",
                        "items": []
                    }
                ]
            }
        """.trimIndent()

        val result = json.decodeFromString<HomeLayoutModel>(raw)

        assertThat(result.schemaVersion).isEqualTo("1.0.0")
        assertThat(result.layoutChecksum).isEqualTo("abc123")
        assertThat(result.components).hasSize(2)
        assertThat(result.components[0]).isInstanceOf(ComponentModel.SectionHeader::class.java)
        assertThat(result.components[1]).isInstanceOf(ComponentModel.Carousel::class.java)
    }

    @Test
    fun `HomeLayoutModel with empty components list parses successfully`() {
        val raw = """
            {
                "schemaVersion": "1.0.0",
                "components": []
            }
        """.trimIndent()

        val result = json.decodeFromString<HomeLayoutModel>(raw)

        assertThat(result.schemaVersion).isEqualTo("1.0.0")
        assertThat(result.layoutChecksum).isNull()
        assertThat(result.components).isEmpty()
    }

    // endregion
}
