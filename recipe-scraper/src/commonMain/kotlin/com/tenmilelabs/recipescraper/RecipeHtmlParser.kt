package com.tenmilelabs.recipescraper

import com.fleeksoft.ksoup.Ksoup
import com.tenmilelabs.recipescraper.jsonld.extractJsonLdRecipe
import com.tenmilelabs.recipescraper.microdata.extractMicrodataRecipe
import com.tenmilelabs.recipescraper.model.ExtractionSource
import com.tenmilelabs.recipescraper.model.ScrapeResult
import com.tenmilelabs.recipescraper.model.ScrapedRecipe

/**
 * Extracts a structured recipe from an HTML document.
 *
 * This is the library's single entry point. It does parsing only — no network I/O — so callers
 * fetch the HTML themselves and hand it in along with the URL it came from.
 *
 * [parse] never throws: any unexpected failure inside the HTML or JSON parsers is caught and
 * reported as [ScrapeResult.ParseError] instead.
 *
 * Two extraction tiers are tried in order, JSON-LD then microdata, since JSON-LD is both the more
 * common format on recipe sites and the more reliably structured one. A tier only counts as a hit
 * when it produces a recipe with a non-blank title *and* at least one ingredient or instruction —
 * a schema.org block that carries only a name is not a usable recipe, so the next tier is tried.
 */
class RecipeHtmlParser {

    /**
     * Extracts a recipe from [html]. [sourceUrl] is recorded on the result as-is; it is never read
     * from the page itself.
     */
    fun parse(html: String, sourceUrl: String): ScrapeResult = try {
        val document = Ksoup.parse(html)

        val jsonLd = extractJsonLdRecipe(document, sourceUrl)
        if (jsonLd != null && jsonLd.isUsable()) {
            ScrapeResult.Success(jsonLd, ExtractionSource.JSON_LD)
        } else {
            val microdata = extractMicrodataRecipe(document, sourceUrl)
            if (microdata != null && microdata.isUsable()) {
                ScrapeResult.Success(microdata, ExtractionSource.MICRODATA)
            } else {
                ScrapeResult.NoRecipeFound
            }
        }
    } catch (e: Exception) {
        ScrapeResult.ParseError(e.message ?: "Unknown parse error")
    }
}

private fun ScrapedRecipe.isUsable(): Boolean =
    title.isNotBlank() && (ingredients.isNotEmpty() || instructions.isNotEmpty())
