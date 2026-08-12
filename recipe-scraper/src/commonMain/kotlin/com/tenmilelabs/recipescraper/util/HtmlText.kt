package com.tenmilelabs.recipescraper.util

import com.fleeksoft.ksoup.Ksoup

/**
 * Reduces a string that may contain HTML to plain text: tags removed, entities decoded, whitespace
 * collapsed to single spaces. Recipe sites routinely embed `<p>`, `<a>` and `<br>` inside JSON-LD
 * string values, so this runs over every extracted text field.
 *
 * Returns `null` for input that is null, or that is blank once cleaned.
 */
internal fun cleanHtmlText(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    // Only pay for a parse when the text actually looks like markup; the common case is plain text.
    val text = if (raw.containsMarkup()) Ksoup.parse(raw).text() else raw
    return text.collapseWhitespace().takeIf { it.isNotBlank() }
}

private fun String.containsMarkup(): Boolean = contains('<') || contains('&')

/** Collapses all whitespace runs — including non-breaking spaces, which food blogs love — to one space. */
internal fun String.collapseWhitespace(): String =
    replace(' ', ' ').split(WHITESPACE_RUN).filter { it.isNotEmpty() }.joinToString(" ")

private val WHITESPACE_RUN = Regex("\\s+")

/**
 * Extracts the first whole number in a string, used for yields such as `"4 servings"`, `"Serves 4"`
 * or `"4-6"` (which takes the lower bound). Returns `null` when there is no number, or when the
 * number is not a plausible serving count.
 */
internal fun firstIntOrNull(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    val digits = FIRST_INT.find(raw)?.value ?: return null
    return digits.toIntOrNull()?.takeIf { it in 1..MAX_PLAUSIBLE_SERVINGS }
}

private val FIRST_INT = Regex("\\d+")

// Guards against picking up a stray number from prose ("Makes 24 cookies" is fine; a 4-digit
// value is far more likely to be a year or an ID than a serving count).
private const val MAX_PLAUSIBLE_SERVINGS = 999
