package com.tenmilelabs.recipescraper.model

/**
 * A recipe extracted from a web page.
 *
 * Every field that a page may legitimately omit is nullable or empty rather than defaulted: this
 * library reports what the page actually published and never fabricates data. Choosing fallbacks
 * (for example, treating a missing prep time as zero) is the caller's decision.
 *
 * @property title the recipe name. Always non-blank — extraction fails rather than returning a
 *   recipe without one.
 * @property description the page's own summary, with any embedded HTML stripped.
 * @property imageUrl the first usable image URL found, which may be relative to [sourceUrl].
 * @property prepTimeMinutes preparation time in whole minutes.
 * @property cookTimeMinutes cooking time in whole minutes.
 * @property totalTimeMinutes total time in whole minutes. Frequently present when the prep/cook
 *   split is not, so callers can derive one from the other.
 * @property servings the yield as a whole number of servings.
 * @property ingredients ingredient lines in source order; may be empty.
 * @property instructions instruction steps in order, flattened across any sections, with HTML
 *   stripped and blanks dropped; may be empty.
 * @property keywords tags, cuisines and categories the page declared, deduplicated.
 * @property author the recipe author's name.
 * @property sourceUrl the URL this recipe was extracted from, as supplied by the caller.
 */
data class ScrapedRecipe(
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val prepTimeMinutes: Int?,
    val cookTimeMinutes: Int?,
    val totalTimeMinutes: Int?,
    val servings: Int?,
    val ingredients: List<ScrapedIngredient>,
    val instructions: List<String>,
    val keywords: List<String>,
    val author: String?,
    val sourceUrl: String,
)
