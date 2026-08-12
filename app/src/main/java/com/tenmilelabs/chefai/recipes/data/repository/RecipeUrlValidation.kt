package com.tenmilelabs.chefai.recipes.data.repository

import java.net.URI
import java.net.URISyntaxException

private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

/**
 * Trims and, if absent, adds an `https://` scheme, then validates the result is a plausible
 * public web URL: `http`/`https` only, a host present, and not a loopback or private-network
 * address. The URL comes from user paste, so there's no reason for the app to fetch those.
 *
 * Returns `null` when the input is not a usable URL.
 */
internal fun normalizeAndValidateUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if (SCHEME_PREFIX.containsMatchIn(trimmed)) trimmed else "https://$trimmed"

    val uri = try {
        URI(withScheme)
    } catch (e: URISyntaxException) {
        return null
    }

    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host ?: return null
    if (host.isBlockedHost()) return null

    return uri.toString()
}

private fun String.isBlockedHost(): Boolean {
    val host = lowercase()
    if (host == "localhost") return true

    val octets = host.split(".")
    if (octets.size != 4) return false
    val values = octets.map { it.toIntOrNull() ?: return false }
    val (a, b) = values[0] to values[1]
    return when {
        a == 127 -> true // 127.0.0.0/8
        a == 10 -> true // 10.0.0.0/8
        a == 192 && b == 168 -> true // 192.168.0.0/16
        a == 172 && b in 16..31 -> true // 172.16.0.0/12
        a == 169 && b == 254 -> true // 169.254.0.0/16
        else -> false
    }
}
