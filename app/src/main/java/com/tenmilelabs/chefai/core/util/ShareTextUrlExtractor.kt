package com.tenmilelabs.chefai.core.util

import com.tenmilelabs.chefai.recipes.data.repository.normalizeUrlForDisplay

private val URL_REGEX = Regex("https?://\\S+")

/** Trailing punctuation a sentence wraps a shared link in, e.g. "...check it out: https://x.com/r." */
private val TRAILING_PUNCTUATION = Regex("[.,;:!?)\\]\"']+$")

/**
 * Extracts a usable recipe URL from arbitrary shared text — typically Android's share-sheet
 * `EXTRA_TEXT`, which often reads like "Check out this recipe! https://example.com/r Enjoy!"
 * rather than a bare URL.
 *
 * The text comes from another app and is exactly as untrusted as a pasted URL, so the candidate is
 * run through [normalizeUrlForDisplay] — an invalid URL, or one whose host is *itself* an obvious
 * loopback/private-network literal, yields `null` here too, rather than being handed to the
 * importer. This runs on the main thread (see `MainActivity.consumeShareIntent`), so it can only
 * do a no-network check — see [normalizeUrlForDisplay]'s KDoc. The importer re-validates the URL
 * with the full, DNS-resolving guard before ever fetching it.
 */
fun extractSharedRecipeUrl(sharedText: String?): String? {
    val match = sharedText?.let { URL_REGEX.find(it)?.value } ?: return null
    val trimmed = match.replace(TRAILING_PUNCTUATION, "")
    if (trimmed.isBlank()) return null
    return normalizeUrlForDisplay(trimmed)
}
