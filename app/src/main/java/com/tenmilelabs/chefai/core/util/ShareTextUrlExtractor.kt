package com.tenmilelabs.chefai.core.util

import com.tenmilelabs.chefai.recipes.data.repository.normalizeAndValidateUrl

private val URL_REGEX = Regex("https?://\\S+")

/** Trailing punctuation a sentence wraps a shared link in, e.g. "...check it out: https://x.com/r." */
private val TRAILING_PUNCTUATION = Regex("[.,;:!?)\\]\"']+$")

/**
 * Extracts a usable recipe URL from arbitrary shared text — typically Android's share-sheet
 * `EXTRA_TEXT`, which often reads like "Check out this recipe! https://example.com/r Enjoy!"
 * rather than a bare URL.
 *
 * The text comes from another app and is exactly as untrusted as a pasted URL, so the candidate is
 * run through the same [normalizeAndValidateUrl] check used for manual paste — an invalid or
 * private-network URL yields `null` here too, rather than being handed to the importer.
 */
fun extractSharedRecipeUrl(sharedText: String?): String? {
    val match = sharedText?.let { URL_REGEX.find(it)?.value } ?: return null
    val trimmed = match.replace(TRAILING_PUNCTUATION, "")
    if (trimmed.isBlank()) return null
    return normalizeAndValidateUrl(trimmed)
}
