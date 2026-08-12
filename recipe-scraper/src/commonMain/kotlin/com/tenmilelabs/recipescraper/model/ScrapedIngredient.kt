package com.tenmilelabs.recipescraper.model

/**
 * A single ingredient line, as found on a recipe page and split into its parts.
 *
 * Recipe sites publish ingredients as free text ("2 cups all-purpose flour, sifted"), so
 * [quantity] and [unit] are best-effort: both are `null` when the line carries no recognisable
 * amount ("Salt to taste"). Callers decide what to substitute — this library does not invent values.
 *
 * @property raw the untouched source line, always populated, so callers can display or re-parse it.
 * @property quantity the parsed amount, or `null` if the line had none.
 * @property unit the unit normalised to a canonical short form (e.g. `"cup"`, `"tbsp"`), or `null`
 *   if the line had no recognisable unit.
 * @property name the ingredient text with the quantity and unit removed. Never blank — falls back
 *   to the whole of [raw] when the line could not be split.
 */
data class ScrapedIngredient(
    val raw: String,
    val quantity: Double?,
    val unit: String?,
    val name: String,
)
