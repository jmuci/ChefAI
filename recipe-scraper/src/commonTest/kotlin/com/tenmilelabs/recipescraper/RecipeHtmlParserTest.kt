package com.tenmilelabs.recipescraper

import com.tenmilelabs.recipescraper.jsonld.JsonLdFixtures
import com.tenmilelabs.recipescraper.microdata.MicrodataFixtures
import com.tenmilelabs.recipescraper.model.DetectedLanguage
import com.tenmilelabs.recipescraper.model.ExtractionSource
import com.tenmilelabs.recipescraper.model.LanguageConfidence
import com.tenmilelabs.recipescraper.model.LanguageSource
import com.tenmilelabs.recipescraper.model.ScrapeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

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

    // --- Language ---

    private fun parseRecipe(html: String, contentLanguage: String? = null) =
        assertIs<ScrapeResult.Success>(parser.parse(html, "https://example.com/r", contentLanguage)).recipe

    @Test
    fun `detects Spanish at HIGH confidence when markup and text agree`() {
        val recipe = parseRecipe(JsonLdFixtures.SPANISH_RECIPE)

        assertEquals(DetectedLanguage("es", LanguageConfidence.HIGH, LanguageSource.JSON_LD), recipe.language)
    }

    @Test
    fun `re-parses ingredients with the detected language`() {
        val ingredients = parseRecipe(JsonLdFixtures.SPANISH_RECIPE).ingredients.associateBy { it.raw }

        val potatoes = ingredients.getValue("500 g de patatas")
        assertEquals(500.0, potatoes.quantity)
        assertEquals("g", potatoes.unit)
        assertEquals("patatas", potatoes.name)

        val milk = ingredients.getValue("2 a 3 cucharadas de leche")
        assertEquals(2.0, milk.quantity)
        assertEquals("tbsp", milk.unit)
        assertEquals("leche", milk.name)
    }

    @Test
    fun `asks for confirmation when the page's lang attribute contradicts its text`() {
        val recipe = parseRecipe(JsonLdFixtures.MISMATCHED_LANG)

        assertEquals(DetectedLanguage("es", LanguageConfidence.LOW, LanguageSource.TEXT), recipe.language)
    }

    @Test
    fun `detects the language from the text alone when there is no markup`() {
        assertEquals(
            DetectedLanguage("es", LanguageConfidence.HIGH, LanguageSource.TEXT),
            parseRecipe(JsonLdFixtures.NO_LANG_METADATA).language,
        )
        assertEquals(
            DetectedLanguage("en", LanguageConfidence.HIGH, LanguageSource.TEXT),
            parseRecipe(JsonLdFixtures.PLAIN_OBJECT).language,
        )
    }

    @Test
    fun `detects the language of a microdata recipe`() {
        assertEquals(
            DetectedLanguage("es", LanguageConfidence.HIGH, LanguageSource.MICRODATA),
            parseRecipe(MicrodataFixtures.SPANISH_RECIPE).language,
        )
    }

    @Test
    fun `trusts the page's lang attribute when a short recipe's words lean the same way`() {
        // Too few words to be confident on their own, but they corroborate <html lang>: no prompt.
        val html = """
            <html lang="en"><head><script type="application/ld+json">
            {"@type": "Recipe", "name": "Buttered Toast",
             "recipeIngredient": ["2 slices bread", "1 tbsp butter"],
             "recipeInstructions": ["Toast the bread and spread the butter."]}
            </script></head><body></body></html>
        """.trimIndent()

        assertEquals(
            DetectedLanguage("en", LanguageConfidence.HIGH, LanguageSource.HTML_LANG),
            parseRecipe(html).language,
        )
    }

    @Test
    fun `falls back to the Content-Language header at LOW confidence`() {
        val html = """
            <html><head><script type="application/ld+json">
            {"@type": "Recipe", "name": "Gazpacho", "recipeIngredient": ["1 kg tomates"]}
            </script></head><body></body></html>
        """.trimIndent()

        assertEquals(
            DetectedLanguage("es", LanguageConfidence.LOW, LanguageSource.HTTP_HEADER),
            parseRecipe(html, contentLanguage = "es-ES").language,
        )
        assertNull(parseRecipe(html).language)
    }

    @Test
    fun `reads og-locale and the content-language meta tag`() {
        val body = """
            <script type="application/ld+json">
            {"@type": "Recipe", "name": "Gazpacho", "recipeIngredient": ["1 kg tomates"]}
            </script></head><body></body></html>
        """.trimIndent()

        assertEquals(
            DetectedLanguage("es", LanguageConfidence.LOW, LanguageSource.OG_LOCALE),
            parseRecipe("""<html><head><meta property="og:locale" content="es_ES" />""" + body).language,
        )
        assertEquals(
            DetectedLanguage("es", LanguageConfidence.LOW, LanguageSource.META_CONTENT_LANGUAGE),
            parseRecipe("""<html><head><meta http-equiv="Content-Language" content="es" />""" + body).language,
        )
    }
}
