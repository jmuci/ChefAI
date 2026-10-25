package com.tenmilelabs.recipescraper.language

import com.fleeksoft.ksoup.nodes.Document
import com.tenmilelabs.recipescraper.model.DetectedLanguage
import com.tenmilelabs.recipescraper.model.LanguageConfidence
import com.tenmilelabs.recipescraper.model.LanguageSource
import com.tenmilelabs.recipescraper.model.ScrapedRecipe

/** A language code a page's markup declared, already normalised, and where it was declared. */
internal data class LanguageSignal(val code: String, val source: LanguageSource)

/** The outcome of [scoreText]: the best-scoring language and whether it won decisively. */
internal data class TextVerdict(val code: String, val confident: Boolean)

// A text verdict is confident only with enough evidence and a clear margin over the runner-up.
// Recipe-length text clears both easily; a three-ingredient stub may not, which is the point.
private const val MIN_CONFIDENT_HITS = 8
private const val MIN_WINNING_RATIO = 2.0

private val EXPLICIT_SOURCES = setOf(LanguageSource.JSON_LD, LanguageSource.MICRODATA)

private val NON_LETTERS = Regex("[^\\p{L}]+")

/**
 * Works out [recipe]'s language from every signal available, most specific first:
 *
 * 1. the recipe's own `inLanguage` (JSON-LD or microdata), which the extractors leave on
 *    [ScrapedRecipe.language] as a provisional value;
 * 2. `<html lang>`, `og:locale` and `<meta http-equiv="content-language">` from [document];
 * 3. [contentLanguageHeader], the HTTP `Content-Language` header if the caller had one;
 * 4. the recipe's text, scored against [STOP_WORD_PROFILES].
 *
 * See [decideLanguage] for how markup and text are weighed against each other.
 */
internal fun detectLanguage(
    recipe: ScrapedRecipe,
    document: Document,
    contentLanguageHeader: String?,
): DetectedLanguage? {
    val signals = buildList {
        recipe.language?.let { add(LanguageSignal(it.code, it.source)) }
        document.selectFirst("html")?.let { html ->
            addSignal(html.attr("lang").ifBlank { html.attr("xml:lang") }, LanguageSource.HTML_LANG)
        }
        addSignal(document.metaContent("meta[property=og:locale]"), LanguageSource.OG_LOCALE)
        addSignal(
            document.metaContent("meta[http-equiv~=(?i)^content-language$]"),
            LanguageSource.META_CONTENT_LANGUAGE,
        )
        addSignal(contentLanguageHeader, LanguageSource.HTTP_HEADER)
    }
    return decideLanguage(signals, scoreText(recipe.searchableText()))
}

/**
 * Weighs the highest-priority markup signal against the text verdict:
 *
 * | Markup                | Text                          | Result       |
 * |-----------------------|-------------------------------|--------------|
 * | agrees                | confident                     | markup, HIGH |
 * | absent                | confident                     | text, HIGH   |
 * | disagrees             | confident                     | text, LOW    |
 * | explicit `inLanguage` | not confident                 | markup, HIGH |
 * | page-level only       | not confident, leans same way | markup, HIGH |
 * | page-level only       | leans elsewhere, or no lean   | markup, LOW  |
 * | absent                | not confident                 | `null`       |
 *
 * Text beats markup on disagreement because page-level markup is often a theme's template default
 * that nobody changed — but only at LOW confidence, so the user still gets the final say. A short
 * recipe whose few words lean the same way as its page-level markup is corroboration enough on its
 * own; asking about every three-line English recipe on an English site would make the prompt noise.
 * Markup naming a language with no text profile can't be contradicted or corroborated (the text
 * scorer can't recognise it), so it is treated like the "leans elsewhere" row.
 */
internal fun decideLanguage(signals: List<LanguageSignal>, text: TextVerdict?): DetectedLanguage? {
    val markup = signals.firstOrNull()
    val confidentText = text?.takeIf { it.confident }

    return when {
        markup == null ->
            confidentText?.let { DetectedLanguage(it.code, LanguageConfidence.HIGH, LanguageSource.TEXT) }

        confidentText == null || markup.code !in STOP_WORD_PROFILES -> {
            val corroborated = markup.source in EXPLICIT_SOURCES || text?.code == markup.code
            DetectedLanguage(
                code = markup.code,
                confidence = if (corroborated) LanguageConfidence.HIGH else LanguageConfidence.LOW,
                source = markup.source,
            )
        }

        confidentText.code == markup.code ->
            DetectedLanguage(markup.code, LanguageConfidence.HIGH, markup.source)

        else -> DetectedLanguage(confidentText.code, LanguageConfidence.LOW, LanguageSource.TEXT)
    }
}

/**
 * Scores [text] against each language in [STOP_WORD_PROFILES] by counting the words it shares with
 * that profile. Returns `null` when there is no lean at all: nothing matched, or two languages tie
 * for the lead (which map order would otherwise break in English's favour).
 */
internal fun scoreText(text: String): TextVerdict? {
    val words = text.lowercase().split(NON_LETTERS).filter { it.isNotEmpty() }
    val ranked = STOP_WORD_PROFILES
        .map { (code, profile) -> code to words.count { it in profile } }
        .sortedByDescending { it.second }

    val (winner, hits) = ranked.first()
    val runnerUp = ranked.getOrNull(1)?.second ?: 0
    if (hits == 0 || hits == runnerUp) return null
    val confident = hits >= MIN_CONFIDENT_HITS && hits >= runnerUp * MIN_WINNING_RATIO
    return TextVerdict(winner, confident)
}

private fun MutableList<LanguageSignal>.addSignal(tag: String?, source: LanguageSource) {
    normalizeLanguageTag(tag)?.let { add(LanguageSignal(it, source)) }
}

private fun Document.metaContent(selector: String): String? =
    selectFirst(selector)?.attr("content")?.takeIf { it.isNotBlank() }

private fun ScrapedRecipe.searchableText(): String = buildString {
    appendLine(title)
    description?.let(::appendLine)
    ingredients.forEach { appendLine(it.raw) }
    instructions.forEach(::appendLine)
}
