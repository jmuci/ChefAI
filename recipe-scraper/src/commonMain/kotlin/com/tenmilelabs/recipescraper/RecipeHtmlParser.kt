package com.tenmilelabs.recipescraper

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.tenmilelabs.recipescraper.ingredient.parseIngredient
import com.tenmilelabs.recipescraper.jsonld.extractJsonLdRecipe
import com.tenmilelabs.recipescraper.language.detectLanguage
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
 * when it produces a recipe with a non-blank title *and* at least one ingredient — a block with
 * instructions but no ingredients has nothing to show on the ingredients tab, so the next tier is
 * tried instead of silently succeeding with an empty list.
 *
 * Once a tier hits, the recipe's language is detected (see [ScrapedRecipe.language]) and its
 * ingredient lines are re-parsed with that language, so connector words like Spanish `de` are
 * stripped from the names.
 */
class RecipeHtmlParser {

    /**
     * Extracts a recipe from [html]. [sourceUrl] is recorded on the result as-is; it is never read
     * from the page itself.
     *
     * [contentLanguage] is the HTTP `Content-Language` response header, if the caller fetched the
     * page itself and has one. It is the weakest language signal and is safe to omit.
     */
    fun parse(html: String, sourceUrl: String, contentLanguage: String? = null): ScrapeResult = try {
        val document = Ksoup.parse(html)

        val jsonLd = extractJsonLdRecipe(document, sourceUrl)?.bounded()
        if (jsonLd != null && jsonLd.isUsable()) {
            ScrapeResult.Success(jsonLd.withLanguage(document, contentLanguage), ExtractionSource.JSON_LD)
        } else {
            val microdata = extractMicrodataRecipe(document, sourceUrl)?.bounded()
            if (microdata != null && microdata.isUsable()) {
                ScrapeResult.Success(microdata.withLanguage(document, contentLanguage), ExtractionSource.MICRODATA)
            } else {
                ScrapeResult.NoRecipeFound
            }
        }
    } catch (e: Exception) {
        ScrapeResult.ParseError(e.message ?: "Unknown parse error")
    }
}

private fun ScrapedRecipe.isUsable(): Boolean =
    title.isNotBlank() && ingredients.isNotEmpty()

// Generous enough to admit any real recipe; the point is that a hostile page cannot hand the caller
// hundreds of thousands of ingredients or a megabyte-long title to persist and sync.
private const val MAX_ITEMS = 300
private const val MAX_LINE_CHARS = 500
private const val MAX_TEXT_CHARS = 4_000
private const val MAX_URL_CHARS = 2_048

/**
 * Caps how much of a page's recipe is kept. Runs before [isUsable], so a recipe whose ingredient
 * lines were all dropped as over-long falls through to the next tier rather than succeeding empty.
 *
 * An over-long ingredient line is dropped, not truncated: half a line would parse into a wrong
 * quantity or name, where a missing line is at least visibly missing.
 */
private fun ScrapedRecipe.bounded(): ScrapedRecipe = copy(
    title = title.take(MAX_LINE_CHARS),
    description = description?.take(MAX_TEXT_CHARS),
    imageUrl = imageUrl?.takeIf { it.length <= MAX_URL_CHARS },
    ingredients = ingredients.asSequence().filter { it.raw.length <= MAX_LINE_CHARS }.take(MAX_ITEMS).toList(),
    instructions = instructions.asSequence().map { it.take(MAX_TEXT_CHARS) }.take(MAX_ITEMS).toList(),
    keywords = keywords.take(MAX_ITEMS),
    author = author?.take(MAX_LINE_CHARS),
)

/**
 * Replaces the extractor's provisional [ScrapedRecipe.language] with the final verdict, then
 * re-parses each ingredient from its untouched [com.tenmilelabs.recipescraper.model.ScrapedIngredient.raw]
 * line with that language.
 */
private fun ScrapedRecipe.withLanguage(document: Document, contentLanguage: String?): ScrapedRecipe {
    val detected = detectLanguage(this, document, contentLanguage)
    return copy(
        language = detected,
        ingredients = ingredients.map { parseIngredient(it.raw, detected?.code) },
    )
}
