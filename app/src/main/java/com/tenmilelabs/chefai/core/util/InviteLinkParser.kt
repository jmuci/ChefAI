package com.tenmilelabs.chefai.core.util

import java.net.URI
import java.net.URISyntaxException

private const val INVITE_HOST = "chefai.app"
private const val INVITE_PATH_PREFIX = "/invite"

/**
 * Extracts a household invite token from an App Link URL, e.g.
 * `https://chefai.app/invite?token=abc123` -> `"abc123"` — matches the backend's default
 * `household.inviteBaseUrl` (`ktor-chefai`'s `Application.kt`).
 *
 * Takes a plain `String` rather than `android.net.Uri` (unlike the `Intent` it's ultimately read
 * from — see `MainActivity.consumeInviteIntent`) so this stays unit-testable on the plain JVM
 * without Robolectric, the same reason [extractSharedRecipeUrl] parses with [java.net.URI] rather
 * than the Android type.
 *
 * Wrong scheme, wrong host, wrong path, or a missing/blank token all return `null` uniformly —
 * callers don't need to distinguish "not our link" from "malformed link," only whether opening the
 * app also means opening an invite.
 */
object InviteLinkParser {
    fun extractToken(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return null
        }
        if (uri.scheme?.lowercase() != "https") return null
        if (uri.host != INVITE_HOST) return null
        if (uri.path?.startsWith(INVITE_PATH_PREFIX) != true) return null
        return uri.tokenQueryParam()?.takeIf { it.isNotBlank() }
    }

    private fun URI.tokenQueryParam(): String? {
        val query = query ?: return null
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.getOrNull(0) == "token" }
            ?.getOrNull(1)
    }
}
