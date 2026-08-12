package com.tenmilelabs.recipescraper.microdata

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MicrodataExtractorTest {

    private fun extract(html: String) = extractMicrodataRecipe(Ksoup.parse(html), sourceUrl = "https://example.com/r")

    @Test
    fun `maps a full microdata recipe`() {
        val recipe = extract(MicrodataFixtures.FULL_RECIPE)

        assertEquals("Chocolate Chip Cookies", recipe?.title)
        assertEquals("Classic chewy cookies.", recipe?.description)
        assertEquals("https://example.com/cookies.jpg", recipe?.imageUrl)
        assertEquals(20, recipe?.prepTimeMinutes)
        assertEquals(10, recipe?.cookTimeMinutes)
        assertEquals(24, recipe?.servings)
        assertEquals(listOf("2 cups flour", "1 cup sugar"), recipe?.ingredients?.map { it.raw })
        assertEquals(
            listOf("Preheat oven to 350F.", "Mix ingredients.", "Bake for 10 minutes."),
            recipe?.instructions,
        )
        assertEquals(listOf("cookies", "dessert"), recipe?.keywords)
        assertEquals("Baker Bob", recipe?.author)
        assertEquals("https://example.com/r", recipe?.sourceUrl)
    }

    @Test
    fun `reads meta content and link href values`() {
        val recipe = extract(MicrodataFixtures.META_AND_LINK_VALUES)

        assertEquals("Meta-Driven Recipe", recipe?.title)
        assertEquals(6, recipe?.servings)
        assertEquals("https://example.com/from-link.jpg", recipe?.imageUrl)
    }

    @Test
    fun `treats a nameless scope as a miss`() {
        assertNull(extract("""<div itemscope itemtype="https://schema.org/Recipe"></div>"""))
    }

    @Test
    fun `returns null when there is no Recipe-typed scope`() {
        assertNull(extract(MicrodataFixtures.NO_RECIPE_SCOPE))
        assertNull(extract("<html><body>hello</body></html>"))
    }

    @Test
    fun `a name-only scope still maps — ingredient-instruction sufficiency is the caller's job`() {
        val recipe = extract(MicrodataFixtures.NAME_ONLY)

        assertEquals("Just A Name", recipe?.title)
        assertEquals(emptyList(), recipe?.ingredients)
        assertEquals(emptyList(), recipe?.instructions)
    }
}
