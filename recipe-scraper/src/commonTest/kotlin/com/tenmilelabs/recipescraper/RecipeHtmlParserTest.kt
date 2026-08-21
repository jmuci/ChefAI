package com.tenmilelabs.recipescraper

import com.tenmilelabs.recipescraper.jsonld.JsonLdFixtures
import com.tenmilelabs.recipescraper.microdata.MicrodataFixtures
import com.tenmilelabs.recipescraper.model.ExtractionSource
import com.tenmilelabs.recipescraper.model.ScrapeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RecipeHtmlParserTest {

    private val parser = RecipeHtmlParser()

    @Test
    fun `extracts from JSON-LD when present`() {
        val result = parser.parse(JsonLdFixtures.PLAIN_OBJECT, "https://example.com/r")

        val success = assertIs<ScrapeResult.Success>(result)
        assertEquals(ExtractionSource.JSON_LD, success.source)
        assertEquals("Simple Pancakes", success.recipe.title)
    }

    @Test
    fun `falls back to microdata when there is no JSON-LD`() {
        val result = parser.parse(MicrodataFixtures.FULL_RECIPE, "https://example.com/r")

        val success = assertIs<ScrapeResult.Success>(result)
        assertEquals(ExtractionSource.MICRODATA, success.source)
        assertEquals("Chocolate Chip Cookies", success.recipe.title)
    }

    @Test
    fun `prefers JSON-LD over microdata when both are present`() {
        val html = JsonLdFixtures.PLAIN_OBJECT.replace("</body></html>", "") +
            MicrodataFixtures.FULL_RECIPE.substringAfter("<body>")

        val result = parser.parse(html, "https://example.com/r")

        val success = assertIs<ScrapeResult.Success>(result)
        assertEquals(ExtractionSource.JSON_LD, success.source)
        assertEquals("Simple Pancakes", success.recipe.title)
    }

    @Test
    fun `falls through to NoRecipeFound when the only JSON-LD candidate has no ingredients or instructions`() {
        assertEquals(ScrapeResult.NoRecipeFound, parser.parse(JsonLdFixtures.NAME_ONLY, "https://example.com/r"))
    }

    @Test
    fun `falls through to NoRecipeFound when the only JSON-LD candidate has instructions but no ingredients`() {
        assertEquals(
            ScrapeResult.NoRecipeFound,
            parser.parse(JsonLdFixtures.INSTRUCTIONS_ONLY, "https://example.com/r"),
        )
    }

    @Test
    fun `falls back to microdata when JSON-LD has instructions but no ingredients and microdata does`() {
        val html = JsonLdFixtures.INSTRUCTIONS_ONLY.replace("</body></html>", "") +
            MicrodataFixtures.FULL_RECIPE.substringAfter("<body>")

        val result = parser.parse(html, "https://example.com/r")

        val success = assertIs<ScrapeResult.Success>(result)
        assertEquals(ExtractionSource.MICRODATA, success.source)
        assertEquals("Chocolate Chip Cookies", success.recipe.title)
    }

    @Test
    fun `falls through to NoRecipeFound when the only microdata scope has no ingredients or instructions`() {
        assertEquals(ScrapeResult.NoRecipeFound, parser.parse(MicrodataFixtures.NAME_ONLY, "https://example.com/r"))
    }

    @Test
    fun `returns NoRecipeFound for a page with no recipe markup`() {
        assertEquals(
            ScrapeResult.NoRecipeFound,
            parser.parse("<html><body>hello</body></html>", "https://example.com/r"),
        )
    }

    @Test
    fun `returns NoRecipeFound for an empty document`() {
        assertEquals(ScrapeResult.NoRecipeFound, parser.parse("", "https://example.com/r"))
    }

    @Test
    fun `records the caller-supplied sourceUrl, not anything read from the page`() {
        val result = parser.parse(JsonLdFixtures.PLAIN_OBJECT, "https://mysite.example/recipe/42")

        val success = assertIs<ScrapeResult.Success>(result)
        assertEquals("https://mysite.example/recipe/42", success.recipe.sourceUrl)
    }
}
