package com.tenmilelabs.chefai.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ResetPasswordLinkParserTest {

    private val token = "A".repeat(20) + "b-_" + "9".repeat(20) // 43 chars of base64url

    private fun url(
        scheme: String = "https",
        host: String = "pocket-chef.app",
        path: String = "/reset-password",
        query: String = "token=$token",
    ) = "$scheme://$host$path" + if (query.isEmpty()) "" else "?$query"

    @Test
    fun `accepts a valid link`() {
        assertThat(token).hasLength(43)
        assertThat(ResetPasswordLinkParser.extractToken(url())).isEqualTo(token)
    }

    @Test
    fun `accepts the token among other query parameters`() {
        assertThat(ResetPasswordLinkParser.extractToken(url(query = "utm=x&token=$token"))).isEqualTo(token)
    }

    @Test
    fun `rejects everything that is not exactly the reset link`() {
        val rejected = mapOf(
            "wrong host" to url(host = "evil.example.com"),
            "subdomain" to url(host = "www.pocket-chef.app"),
            "http" to url(scheme = "http"),
            "invite path" to url(path = "/invite"),
            "extra path segment" to url(path = "/reset-password/extra"),
            "prefix-only path" to url(path = "/reset-password-now"),
            "trailing slash" to url(path = "/reset-password/"),
            "short token" to url(query = "token=${token.dropLast(1)}"),
            "long token" to url(query = "token=${token}x"),
            "invalid char" to url(query = "token=${token.dropLast(1)}!"),
            "padded token" to url(query = "token=${token.dropLast(1)}="),
            "missing token" to url(query = ""),
            "empty token" to url(query = "token="),
            "other param only" to url(query = "t=$token"),
            "not a url" to "not a url at all",
            "blank" to "  ",
        )
        for ((name, link) in rejected) {
            assertThat(ResetPasswordLinkParser.extractToken(link)).isNull()
            // Keep the failing case readable.
            assertThat(name).isNotEmpty()
        }
    }

    @Test
    fun `null input returns null`() {
        assertThat(ResetPasswordLinkParser.extractToken(null)).isNull()
    }

    @Test
    fun `the invite parser does not accept a reset link and vice versa`() {
        assertThat(InviteLinkParser.extractToken(url())).isNull()
        assertThat(ResetPasswordLinkParser.extractToken("https://pocket-chef.app/invite?token=$token")).isNull()
    }
}
