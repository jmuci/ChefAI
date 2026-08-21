package com.tenmilelabs.chefai.recipes.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecipeUrlValidationTest {

    private fun resolver(vararg entries: Pair<String, List<java.net.InetAddress>>) =
        FakeHostResolver(entries.toMap())

    // --- Basic shape ---

    @Test
    fun `adds an https scheme when none is given`() {
        val result = normalizeAndValidateUrl("example.com/recipe", resolver())

        assertThat(result).isEqualTo("https://example.com/recipe")
    }

    @Test
    fun `keeps an explicit http scheme`() {
        val result = normalizeAndValidateUrl("http://example.com/recipe", resolver())

        assertThat(result).isEqualTo("http://example.com/recipe")
    }

    @Test
    fun `rejects a non-http scheme`() {
        assertThat(normalizeAndValidateUrl("ftp://example.com/recipe", resolver())).isNull()
    }

    @Test
    fun `rejects blank input`() {
        assertThat(normalizeAndValidateUrl("   ", resolver())).isNull()
    }

    @Test
    fun `rejects text that isn't a url`() {
        assertThat(normalizeAndValidateUrl("not a url \n with control chars", resolver())).isNull()
    }

    // --- localhost / loopback ---

    @Test
    fun `rejects localhost regardless of case`() {
        assertThat(normalizeAndValidateUrl("http://LOCALHOST:8080/x", resolver())).isNull()
    }

    @Test
    fun `rejects a literal IPv4 loopback address`() {
        val host = "127.0.0.1"
        assertThat(normalizeAndValidateUrl("http://$host/x", resolver(host to FakeHostResolver.literal(host)))).isNull()
    }

    @Test
    fun `rejects an IPv6 loopback address`() {
        // java.net.URI#getHost keeps the brackets on an IPv6 literal (e.g. "[::1]"), unlike the
        // IPv4 case above — that bracketed string is exactly what the resolver is keyed on here.
        val host = "::1"
        assertThat(normalizeAndValidateUrl("http://[$host]/x", resolver("[$host]" to FakeHostResolver.literal(host)))).isNull()
    }

    // --- Private / link-local ranges ---

    @Test
    fun `rejects an RFC1918 10-slash-8 address`() {
        val host = "10.1.2.3"
        assertThat(normalizeAndValidateUrl("http://$host/x", resolver(host to FakeHostResolver.literal(host)))).isNull()
    }

    @Test
    fun `rejects an RFC1918 192-168 address`() {
        val host = "192.168.1.5"
        assertThat(normalizeAndValidateUrl("http://$host/x", resolver(host to FakeHostResolver.literal(host)))).isNull()
    }

    @Test
    fun `rejects the cloud metadata link-local address`() {
        val host = "169.254.169.254"
        assertThat(normalizeAndValidateUrl("http://$host/x", resolver(host to FakeHostResolver.literal(host)))).isNull()
    }

    @Test
    fun `rejects an IPv6 link-local address`() {
        val host = "fe80::1"
        assertThat(normalizeAndValidateUrl("http://[$host]/x", resolver("[$host]" to FakeHostResolver.literal(host)))).isNull()
    }

    @Test
    fun `rejects an IPv6 unique-local address not covered by isSiteLocalAddress`() {
        // fc00::/7 — java.net.InetAddress#isSiteLocalAddress only recognizes the deprecated
        // fec0::/10 range, so this needs its own check (see isBlockedAddress).
        val host = "fd12:3456:789a::1"
        assertThat(normalizeAndValidateUrl("http://[$host]/x", resolver("[$host]" to FakeHostResolver.literal(host)))).isNull()
    }

    // --- Alternate encodings the underlying resolver itself normalizes ---

    @Test
    fun `rejects the decimal-integer encoding of a loopback address`() {
        // "2130706433" is 127.0.0.1 — java.net.InetAddress parses this form as a literal, so
        // resolving before checking closes this off automatically.
        val host = "2130706433"
        assertThat(normalizeAndValidateUrl("http://$host/x", resolver(host to FakeHostResolver.literal(host)))).isNull()
    }

    @Test
    fun `rejects an IPv4-mapped IPv6 loopback address`() {
        val host = "::ffff:127.0.0.1"
        assertThat(normalizeAndValidateUrl("http://[$host]/x", resolver("[$host]" to FakeHostResolver.literal(host)))).isNull()
    }

    // --- DNS rebinding: the actual gap this issue is about ---

    @Test
    fun `rejects a hostname whose DNS record points at a blocked address`() {
        val result = normalizeAndValidateUrl(
            "https://evil.example.com/recipe",
            resolver("evil.example.com" to FakeHostResolver.literal("169.254.169.254")),
        )

        assertThat(result).isNull()
    }

    @Test
    fun `rejects a hostname when any of its resolved addresses is blocked`() {
        val result = normalizeAndValidateUrl(
            "https://multi.example.com/recipe",
            resolver("multi.example.com" to FakeHostResolver.literal("93.184.216.34", "10.0.0.1")),
        )

        assertThat(result).isNull()
    }

    @Test
    fun `rejects a hostname that fails to resolve`() {
        val result = normalizeAndValidateUrl("https://nowhere.invalid/recipe", resolver("nowhere.invalid" to emptyList()))

        assertThat(result).isNull()
    }

    @Test
    fun `accepts a hostname that resolves only to public addresses`() {
        val result = normalizeAndValidateUrl(
            "https://good.example.com/recipe",
            resolver("good.example.com" to FakeHostResolver.literal("93.184.216.34")),
        )

        assertThat(result).isEqualTo("https://good.example.com/recipe")
    }

    // --- Redirect re-validation ---

    @Test
    fun `follows a relative redirect and validates the resolved target`() {
        val result = validateRedirectTarget(
            "https://good.example.com/recipe",
            "/moved",
            resolver("good.example.com" to FakeHostResolver.literal("93.184.216.34")),
        )

        assertThat(result).isEqualTo("https://good.example.com/moved")
    }

    @Test
    fun `rejects a redirect to a different host that resolves to a blocked address`() {
        val result = validateRedirectTarget(
            "https://good.example.com/recipe",
            "https://internal.attacker.example/steal",
            resolver(
                "good.example.com" to FakeHostResolver.literal("93.184.216.34"),
                "internal.attacker.example" to FakeHostResolver.literal("169.254.169.254"),
            ),
        )

        assertThat(result).isNull()
    }

    @Test
    fun `rejects a redirect whose Location is unparseable`() {
        val result = validateRedirectTarget(
            "https://good.example.com/recipe",
            "http://[not a valid host",
            resolver("good.example.com" to FakeHostResolver.literal("93.184.216.34")),
        )

        assertThat(result).isNull()
    }
}
