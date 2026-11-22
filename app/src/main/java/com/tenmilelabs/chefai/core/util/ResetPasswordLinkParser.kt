package com.tenmilelabs.chefai.core.util

import java.net.URI
import java.net.URISyntaxException

private const val RESET_HOST = "pocket-chef.app"
private const val RESET_PATH = "/reset-password"

/** 32 random bytes, base64url without padding — what the backend mints. */
private val RESET_TOKEN_REGEX = Regex("^[A-Za-z0-9_-]{43}$")

/**
 * Extracts a password-reset token from `https://pocket-chef.app/reset-password?token=<43 chars>`.
 *
 * Stricter than [InviteLinkParser] on purpose: https only, the exact host, the exact path (no
 * prefix match, no extra segments) and a token of exactly the shape the backend issues. Anything
 * else returns `null` uniformly, so a mangled link can't open the reset screen at all. Plain
 * `String` in, for the same JVM-testability reason as [InviteLinkParser].
 *
 * The token is a credential: callers keep it in navigation state only and never log it.
 */
object ResetPasswordLinkParser {
    fun extractToken(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return null
        }
        if (uri.scheme?.lowercase() != "https") return null
        if (uri.host != RESET_HOST) return null
        if (uri.path != RESET_PATH) return null
        val token = uri.rawQuery
            ?.split("&")
            ?.map { it.split("=", limit = 2) }
            ?.firstOrNull { it.getOrNull(0) == "token" }
            ?.getOrNull(1)
        return token?.takeIf { RESET_TOKEN_REGEX.matches(it) }
    }
}
