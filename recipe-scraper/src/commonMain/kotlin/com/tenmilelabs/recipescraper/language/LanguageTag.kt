package com.tenmilelabs.recipescraper.language

/**
 * Reduces a language tag as pages publish it — `"es-MX"`, `"es_ES"`, `"ES"`, `"en-US, en"` — to its
 * ISO 639-1 primary subtag (`"es"`), or `null` when it isn't one.
 *
 * Only the first entry of a comma-separated list is read, matching how `Content-Language` lists
 * the primary language first. Anything that isn't exactly two ASCII letters after splitting off
 * the region — three-letter codes, `"x-default"`, an English word like `"Spanish"` — is rejected
 * rather than guessed at.
 */
internal fun normalizeLanguageTag(tag: String?): String? {
    val first = tag?.substringBefore(',')?.trim().orEmpty()
    val primary = first.split('-', '_').first().lowercase()
    return primary.takeIf { it.length == 2 && it.all { c -> c in 'a'..'z' } }
}
