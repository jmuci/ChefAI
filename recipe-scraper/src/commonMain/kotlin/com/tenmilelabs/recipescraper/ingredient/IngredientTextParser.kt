package com.tenmilelabs.recipescraper.ingredient

import com.tenmilelabs.recipescraper.model.ScrapedIngredient
import com.tenmilelabs.recipescraper.util.collapseWhitespace

/**
 * Splits a free-text ingredient line into an amount, a unit and a name.
 *
 * schema.org publishes `recipeIngredient` as prose ("2 cups all-purpose flour, sifted"), so this is
 * necessarily heuristic. It is built to degrade rather than guess: anything it cannot confidently
 * split comes back with a null quantity and unit and the whole line as the name, which a UI can
 * still display correctly. It never throws.
 */
internal fun parseIngredient(raw: String): ScrapedIngredient {
    val text = raw.trim().collapseWhitespace()
    if (text.isEmpty()) return ScrapedIngredient(raw = raw, quantity = null, unit = null, name = "")

    val tokens = text.split(' ').filter { it.isNotEmpty() }
    var index = 0
    var quantity: Double? = null
    var unit: String? = null

    val leading = parseNumber(tokens[0])
    if (leading != null) {
        quantity = leading
        index = 1

        // A mixed number arrives as two tokens: "1 1/2 cups".
        if (index < tokens.size) {
            parseFractionOnly(tokens[index])?.let { fraction ->
                quantity = leading + fraction
                index++
            }
        }

        // A spelled-out range ("2 to 3 tablespoons") — keep the lower bound, drop the rest.
        if (index + 1 < tokens.size &&
            tokens[index].lowercase() in RANGE_WORDS &&
            parseNumber(tokens[index + 1]) != null
        ) {
            index += 2
        }

        // A parenthetical size qualifier often sits between amount and unit: "1 (14 oz) can".
        index = skipParenthetical(tokens, index)
    } else {
        // Metric sites — Spanish and French ones especially — routinely fuse the amount and the
        // unit into a single token: "300g Garbanzos".
        splitFusedAmountAndUnit(tokens[0])?.let { (amount, fusedUnit) ->
            quantity = amount
            unit = fusedUnit
            index = skipParenthetical(tokens, 1)
        }
    }

    // Only look for a unit behind an amount; without one, a leading word is far more likely to be
    // part of the name ("Salt to taste") than a unit. A fused token already supplied one, and
    // re-running this would swallow the first word of the name as a second unit.
    if (quantity != null && unit == null && index < tokens.size) {
        normalizeUnit(tokens[index])?.let { matched ->
            unit = matched
            index = skipParenthetical(tokens, index + 1)
        }
    }

    val name = tokens.drop(index)
        .joinToString(" ")
        .removePrefix("of ")
        .removePrefix("Of ")
        .trim()
        // A line that was nothing but an amount still needs a usable label.
        .ifEmpty { text }

    return ScrapedIngredient(raw = raw, quantity = quantity, unit = unit, name = name)
}

/**
 * Splits a token that fuses an amount and a unit — `"300g"`, `"1.5kg"`, `"½kg"`, `"300-400g"` —
 * into the two, or returns `null` when it isn't one.
 *
 * Deliberately conservative: the trailing part must normalise to a *known* unit, so pan sizes
 * (`"9x13"`), multipliers (`"1x"`) and product codes (`"1A"`) fall through untouched.
 */
private fun splitFusedAmountAndUnit(token: String): Pair<Double, String>? {
    val splitAt = token.indexOfFirst { it.isLetter() }
    if (splitAt <= 0) return null
    val amount = parseNumber(token.substring(0, splitAt)) ?: return null
    val unit = normalizeUnit(token.substring(splitAt)) ?: return null
    return amount to unit
}

/** Advances past a `( … )` group starting at [index], leaving [index] untouched if none is there. */
private fun skipParenthetical(tokens: List<String>, index: Int): Int {
    if (index >= tokens.size || !tokens[index].startsWith("(")) return index
    var cursor = index
    while (cursor < tokens.size) {
        val closes = tokens[cursor].endsWith(")")
        cursor++
        if (closes) return cursor
    }
    // Unbalanced parenthesis — leave the tokens in the name rather than swallowing the whole line.
    return index
}

/** Parses a leading amount, resolving a numeric range such as `"2-3"` to its lower bound. */
private fun parseNumber(token: String): Double? {
    for (dash in DASHES) {
        if (!token.contains(dash)) continue
        val parts = token.split(dash).filter { it.isNotBlank() }
        if (parts.size == 2) {
            val lower = parseSingleNumber(parts[0])
            // Require both sides to be numeric, so hyphenated words ("all-purpose") aren't split.
            if (lower != null && parseSingleNumber(parts[1]) != null) return lower
        }
    }
    return parseSingleNumber(token)
}

/** Parses a token only if it is purely a fraction, used to attach the tail of a mixed number. */
private fun parseFractionOnly(token: String): Double? {
    val isFraction = token.contains('/') || token.any { it in VULGAR_FRACTIONS }
    return if (isFraction) parseSingleNumber(token) else null
}

private fun parseSingleNumber(text: String): Double? {
    if (text.isEmpty()) return null

    // Vulgar fractions, alone ("½") or fused to a whole number ("1½").
    val vulgarAt = text.indexOfFirst { it in VULGAR_FRACTIONS }
    if (vulgarAt >= 0) {
        // Anything trailing the glyph means this isn't a bare amount.
        if (vulgarAt != text.length - 1) return null
        val fraction = VULGAR_FRACTIONS[text[vulgarAt]] ?: return null
        val whole = text.substring(0, vulgarAt)
        if (whole.isEmpty()) return fraction
        return whole.toDoubleOrNull()?.plus(fraction)
    }

    if (text.contains('/')) {
        val parts = text.split('/')
        if (parts.size != 2) return null
        val numerator = parts[0].toDoubleOrNull() ?: return null
        val denominator = parts[1].toDoubleOrNull() ?: return null
        return if (denominator == 0.0) null else numerator / denominator
    }

    // Accept a comma decimal separator, as used across most of Europe.
    return text.replace(',', '.').toDoubleOrNull()
}

/**
 * Maps a unit token onto a canonical short form, or `null` if it isn't a recognised unit.
 *
 * Single-letter forms are matched case-sensitively first, because `T` (tablespoon) and `t`
 * (teaspoon) are distinguished only by case in recipe shorthand.
 */
private fun normalizeUnit(token: String): String? {
    val bare = token.trim().trimEnd('.', ',')
    if (bare.isEmpty()) return null
    CASE_SENSITIVE_UNITS[bare]?.let { return it }
    return UNITS[bare.lowercase()]
}

private val DASHES = listOf('-', '–', '—')

private val RANGE_WORDS = setOf("to", "or")

private val VULGAR_FRACTIONS: Map<Char, Double> = mapOf(
    '½' to 0.5,          // ½
    '¼' to 0.25,         // ¼
    '¾' to 0.75,         // ¾
    '⅓' to 1.0 / 3.0,    // ⅓
    '⅔' to 2.0 / 3.0,    // ⅔
    '⅕' to 0.2,          // ⅕
    '⅖' to 0.4,          // ⅖
    '⅗' to 0.6,          // ⅗
    '⅘' to 0.8,          // ⅘
    '⅙' to 1.0 / 6.0,    // ⅙
    '⅚' to 5.0 / 6.0,    // ⅚
    '⅐' to 1.0 / 7.0,    // ⅐
    '⅛' to 0.125,        // ⅛
    '⅜' to 0.375,        // ⅜
    '⅝' to 0.625,        // ⅝
    '⅞' to 0.875,        // ⅞
    '⅑' to 1.0 / 9.0,    // ⅑
    '⅒' to 0.1,          // ⅒
)

private val CASE_SENSITIVE_UNITS: Map<String, String> = mapOf(
    "T" to "tbsp",
    "t" to "tsp",
)

private val UNITS: Map<String, String> = buildMap {
    fun unit(canonical: String, vararg synonyms: String) {
        put(canonical, canonical)
        synonyms.forEach { put(it, canonical) }
    }
    unit("cup", "cups", "c")
    unit("tbsp", "tbsps", "tablespoon", "tablespoons", "tbs", "tbl", "tblsp")
    unit("tsp", "tsps", "teaspoon", "teaspoons")
    unit("oz", "ounce", "ounces")
    unit("lb", "lbs", "pound", "pounds")
    unit("g", "gr", "gram", "grams", "gramme", "grammes")
    unit("kg", "kilo", "kilos", "kilogram", "kilograms")
    unit("mg", "milligram", "milligrams")
    unit("ml", "milliliter", "milliliters", "millilitre", "millilitres")
    unit("l", "liter", "liters", "litre", "litres")
    unit("pinch", "pinches")
    unit("dash", "dashes")
    unit("clove", "cloves")
    unit("slice", "slices")
    unit("can", "cans")
    unit("jar", "jars")
    unit("package", "packages", "pkg", "pkgs", "pack", "packs")
    unit("quart", "quarts", "qt", "qts")
    unit("pint", "pints", "pt", "pts")
    unit("gallon", "gallons", "gal")
    unit("stick", "sticks")
    unit("sprig", "sprigs")
    unit("bunch", "bunches")
    unit("head", "heads")
    unit("handful", "handfuls")
    unit("piece", "pieces")
    unit("sheet", "sheets")
}
