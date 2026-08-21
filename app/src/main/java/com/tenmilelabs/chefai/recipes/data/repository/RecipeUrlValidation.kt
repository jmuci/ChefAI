package com.tenmilelabs.chefai.recipes.data.repository

import com.tenmilelabs.chefai.recipes.domain.repository.HostResolver
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException

private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

/**
 * Trims and, if absent, adds an `https://` scheme, then validates the result is a plausible
 * public web URL: `http`/`https` only, a host present, and — once resolved — not a
 * loopback/private-network/link-local/multicast address. The URL may come from user paste or from
 * a scraped page's own markup, so there's no reason for the app to fetch an internal address on
 * its behalf.
 *
 * Validation resolves the host through [hostResolver] rather than pattern-matching the string, so
 * a hostname whose DNS record points at an internal address is caught the same way a literal IP
 * would be — including IPv6 and every alternate IPv4 encoding the platform resolver itself
 * accepts, since that resolver is also what the real connection uses.
 *
 * Returns `null` when the input is not a usable, safe URL.
 */
internal fun normalizeAndValidateUrl(input: String, hostResolver: HostResolver): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if (SCHEME_PREFIX.containsMatchIn(trimmed)) trimmed else "https://$trimmed"

    val uri = try {
        URI(withScheme)
    } catch (e: URISyntaxException) {
        return null
    }

    return uri.validated(hostResolver)
}

/**
 * A cheap, no-network pre-filter for call sites that can't run [normalizeAndValidateUrl] — namely
 * [com.tenmilelabs.chefai.core.util.extractSharedRecipeUrl], invoked synchronously from
 * `MainActivity.onCreate`/`onNewIntent` on the main thread, where a real DNS lookup risks a
 * `NetworkOnMainThreadException`.
 *
 * This recognizes only a host that is *itself* a literal loopback/private-network address or
 * `localhost` — nowhere near as strong a guard as [normalizeAndValidateUrl], since it can't catch
 * a hostname whose DNS record points somewhere unsafe. It exists purely so a share-sheet paste of
 * an obviously-internal address is rejected immediately rather than momentarily populating the UI;
 * every URL from this path still goes through the full, resolver-backed check once it reaches
 * [com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter], which is the actual
 * security boundary.
 */
internal fun normalizeUrlForDisplay(input: String): String? {
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
    if (host.isObviouslyUnsafeLiteralHost()) return null

    return uri.toString()
}

/** IPv4-octet/`localhost` string check only — see [normalizeUrlForDisplay]'s KDoc for why. */
private fun String.isObviouslyUnsafeLiteralHost(): Boolean {
    if (lowercase() == "localhost") return true
    val octets = split(".")
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

/**
 * Resolves [location] against [baseUrl] (it may be absolute or relative, as a redirect's
 * `Location` header can be either) and validates the result exactly as [normalizeAndValidateUrl]
 * would.
 *
 * A redirect is a second, independent trip through the SSRF guard: nothing about the original URL
 * passing validation says anything about where it 3xx's to next.
 */
internal fun validateRedirectTarget(baseUrl: String, location: String, hostResolver: HostResolver): String? {
    val base = try {
        URI(baseUrl)
    } catch (e: URISyntaxException) {
        return null
    }
    val resolved = try {
        base.resolve(location)
    } catch (e: IllegalArgumentException) {
        return null
    }
    return resolved.validated(hostResolver)
}

private fun URI.validated(hostResolver: HostResolver): String? {
    val scheme = scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = host ?: return null
    if (!host.isSafeHost(hostResolver)) return null
    return toString()
}

/** Also used directly by [com.tenmilelabs.chefai.recipes.data.network.ScraperWebViewClient] to vet every request a scraped page itself makes. */
internal fun String.isSafeHost(hostResolver: HostResolver): Boolean {
    if (lowercase() == "localhost") return false
    val addresses = hostResolver.resolve(this)
    if (addresses.isEmpty()) return false
    return addresses.none { it.isBlockedAddress() }
}

private fun InetAddress.isBlockedAddress(): Boolean {
    if (isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isAnyLocalAddress || isMulticastAddress) {
        return true
    }
    // IPv6 unique local addresses, fc00::/7 — isSiteLocalAddress above only recognizes the
    // deprecated IPv6 site-local range (fec0::/10), not this one.
    val bytes = address
    return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
}
