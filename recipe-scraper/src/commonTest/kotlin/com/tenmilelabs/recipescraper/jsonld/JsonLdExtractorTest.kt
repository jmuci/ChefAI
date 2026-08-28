package com.tenmilelabs.recipescraper.jsonld

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonLdExtractorTest {

    private fun extract(html: String) = extractJsonLdRecipe(Ksoup.parse(html), sourceUrl = "https://example.com/r")

    @Test
    fun `maps a plain Recipe object`() {
        val recipe = extract(JsonLdFixtures.PLAIN_OBJECT)

        assertEquals("Simple Pancakes", recipe?.title)
        assertEquals("Fluffy pancakes for breakfast.", recipe?.description)
        assertEquals("https://example.com/pancakes.jpg", recipe?.imageUrl)
        assertEquals(10, recipe?.prepTimeMinutes)
        assertEquals(15, recipe?.cookTimeMinutes)
        assertEquals(4, recipe?.servings)
        assertEquals(listOf("2 cups all-purpose flour", "1 cup milk", "2 eggs"), recipe?.ingredients?.map { it.raw })
        assertEquals(
            listOf("Mix dry ingredients.", "Whisk in milk and eggs.", "Cook on a griddle."),
            recipe?.instructions,
        )
        assertEquals(listOf("breakfast", "easy"), recipe?.keywords)
        assertEquals("Jane Doe", recipe?.author)
        assertEquals("https://example.com/r", recipe?.sourceUrl)
        // No nutrition block on this fixture — unknown, not zero.
        assertNull(recipe?.caloriesPerServing)
        assertNull(recipe?.proteinGramsPerServing)
    }

    @Test
    fun `extracts calories and protein from a nutrition block`() {
        val recipe = extract(JsonLdFixtures.WITH_NUTRITION)

        assertEquals(270, recipe?.caloriesPerServing)
        assertEquals(12, recipe?.proteinGramsPerServing)
    }

    @Test
    fun `rejects an implausibly large nutrition value rather than trusting it`() {
        val recipe = extract(JsonLdFixtures.IMPLAUSIBLE_NUTRITION)

        assertNull(recipe?.caloriesPerServing)
        assertNull(recipe?.proteinGramsPerServing)
    }

    @Test
    fun `zero calories is a real value, not treated as missing, and decimal protein is truncated`() {
        val recipe = extract(JsonLdFixtures.ZERO_AND_DECIMAL_NUTRITION)

        assertEquals(0, recipe?.caloriesPerServing)
        assertEquals(12, recipe?.proteinGramsPerServing)
    }

    @Test
    fun `finds a Recipe nested inside an @graph wrapper, skipping non-Recipe siblings`() {
        val recipe = extract(JsonLdFixtures.GRAPH_WRAPPER)

        assertEquals("Graph Recipe", recipe?.title)
        assertEquals(listOf("Cream the sugar.", "Bake it."), recipe?.instructions)
    }

    @Test
    fun `matches a Recipe when @type is an array`() {
        val recipe = extract(JsonLdFixtures.TYPE_ARRAY)

        assertEquals("Multi-Typed Recipe", recipe?.title)
    }

    @Test
    fun `flattens HowToSection instructions in order`() {
        val recipe = extract(JsonLdFixtures.HOW_TO_SECTIONS)

        assertEquals(
            listOf("Rinse the rice.", "Soak for 10 minutes.", "Boil until tender."),
            recipe?.instructions,
        )
    }

    @Test
    fun `splits a single instructions string on newlines`() {
        val recipe = extract(JsonLdFixtures.STRING_INSTRUCTIONS)

        assertEquals(listOf("Peel the potato.", "Boil it.", "Mash it."), recipe?.instructions)
    }

    @Test
    fun `resolves an ImageObject to its url`() {
        val recipe = extract(JsonLdFixtures.IMAGE_OBJECT)

        assertEquals("https://example.com/dish.jpg", recipe?.imageUrl)
    }

    @Test
    fun `skips a malformed script block and recovers the valid one`() {
        val recipe = extract(JsonLdFixtures.MULTIPLE_BLOCKS_ONE_MALFORMED)

        assertEquals("Recovered Recipe", recipe?.title)
    }

    @Test
    fun `treats a nameless Recipe candidate as a miss`() {
        assertNull(extract("""<script type="application/ld+json">{"@type":"Recipe"}</script>"""))
    }

    @Test
    fun `returns null when no Recipe markup is present`() {
        assertNull(extract("<html><body>hello</body></html>"))
    }

    @Test
    fun `a name-only Recipe still maps — ingredient-instruction sufficiency is the caller's job`() {
        val recipe = extract(JsonLdFixtures.NAME_ONLY)

        assertEquals("Just A Name", recipe?.title)
        assertEquals(emptyList(), recipe?.ingredients)
        assertEquals(emptyList(), recipe?.instructions)
    }

    @Test
    fun `maps HowToSupply-style ingredient objects by their name or text field`() {
        val recipe = extract(JsonLdFixtures.OBJECT_INGREDIENTS)

        assertEquals(listOf("2 cups flour", "1 cup milk"), recipe?.ingredients?.map { it.raw })
    }

    @Test
    fun `maps a single ingredient string instead of an array`() {
        val recipe = extract(JsonLdFixtures.SINGLE_STRING_INGREDIENT)

        assertEquals(listOf("1 cup rice"), recipe?.ingredients?.map { it.raw })
    }

    @Test
    fun `flattens ingredients grouped into nested arrays`() {
        val recipe = extract(JsonLdFixtures.GROUPED_INGREDIENTS)

        assertEquals(
            listOf("1 cup sugar", "2 eggs", "1 tsp vanilla"),
            recipe?.ingredients?.map { it.raw },
        )
    }
}
