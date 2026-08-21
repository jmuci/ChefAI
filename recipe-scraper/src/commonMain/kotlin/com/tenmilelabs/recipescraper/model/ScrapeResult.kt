package com.tenmilelabs.recipescraper.model

/**
 * The outcome of attempting to extract a recipe from an HTML document.
 *
 * Extraction failure is an expected result, not an exception: plenty of URLs are simply not recipe
 * pages.
 */
sealed interface ScrapeResult {

    /** A recipe was found. [source] records which extraction strategy produced it. */
    data class Success(val recipe: ScrapedRecipe, val source: ExtractionSource) : ScrapeResult

    /**
     * The document parsed cleanly but carried no usable recipe — either no recipe markup at all,
     * or markup too incomplete to use (no title, or no ingredients).
     */
    data object NoRecipeFound : ScrapeResult

    /** The document could not be parsed. [message] describes the failure for logging. */
    data class ParseError(val message: String) : ScrapeResult
}

/**
 * Which markup format a recipe was extracted from. Both encode schema.org's `Recipe` type; JSON-LD
 * is tried first because it is by far the more common and the more reliably structured.
 */
enum class ExtractionSource {
    /** A `<script type="application/ld+json">` block. */
    JSON_LD,

    /** Inline `itemscope`/`itemprop` microdata attributes. */
    MICRODATA,
}
