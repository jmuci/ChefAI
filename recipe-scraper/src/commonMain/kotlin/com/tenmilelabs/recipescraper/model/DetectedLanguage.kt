package com.tenmilelabs.recipescraper.model

/**
 * The language a recipe page is written in, as worked out from its markup and its text.
 *
 * @property code an ISO 639-1 code, always two lowercase letters (`"en"`, `"es"`). Regional
 *   variants are collapsed — `es-MX` and `es_ES` both become `"es"`.
 * @property confidence whether a caller can use [code] without asking the user. See
 *   [LanguageConfidence].
 * @property source the signal [code] was taken from.
 */
data class DetectedLanguage(
    val code: String,
    val confidence: LanguageConfidence,
    val source: LanguageSource,
)

/** How far a [DetectedLanguage] can be trusted. */
enum class LanguageConfidence {
    /** The markup and the text agree, or one of them is unambiguous on its own. */
    HIGH,

    /**
     * A best guess. Either the markup and the text disagree, or the only signal was page-level
     * markup that sites often leave at a template default (`<html lang="en-US">` on a Spanish blog).
     * Callers should ask the user to confirm.
     */
    LOW,
}

/** Where a [DetectedLanguage] came from, most specific first. */
enum class LanguageSource {
    /** `inLanguage` on the JSON-LD `Recipe` object. */
    JSON_LD,

    /** `itemprop="inLanguage"` inside the microdata `Recipe` scope. */
    MICRODATA,

    /** The `lang` attribute on the `<html>` element. */
    HTML_LANG,

    /** An Open Graph `og:locale` meta tag. */
    OG_LOCALE,

    /** A `<meta http-equiv="content-language">` tag. */
    META_CONTENT_LANGUAGE,

    /** The HTTP `Content-Language` response header, as passed in by the caller. */
    HTTP_HEADER,

    /** The recipe's own words: its title, description, ingredients and instructions. */
    TEXT,
}
