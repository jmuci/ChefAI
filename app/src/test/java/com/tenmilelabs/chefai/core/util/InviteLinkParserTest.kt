package com.tenmilelabs.chefai.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InviteLinkParserTest {

    @Test
    fun `extracts the token from a valid invite link`() {
        assertThat(InviteLinkParser.extractToken("https://chefai.app/invite?token=abc123"))
            .isEqualTo("abc123")
    }

    @Test
    fun `extracts the token when other query parameters are present`() {
        assertThat(InviteLinkParser.extractToken("https://chefai.app/invite?ref=email&token=abc123"))
            .isEqualTo("abc123")
    }

    @Test
    fun `returns null for the wrong host`() {
        assertThat(InviteLinkParser.extractToken("https://evil.example.com/invite?token=abc123")).isNull()
    }

    @Test
    fun `returns null for the wrong path`() {
        assertThat(InviteLinkParser.extractToken("https://chefai.app/recipes?token=abc123")).isNull()
    }

    @Test
    fun `returns null for a non-https scheme`() {
        assertThat(InviteLinkParser.extractToken("http://chefai.app/invite?token=abc123")).isNull()
    }

    @Test
    fun `returns null when the token query parameter is missing`() {
        assertThat(InviteLinkParser.extractToken("https://chefai.app/invite")).isNull()
    }

    @Test
    fun `returns null when the token query parameter is blank`() {
        assertThat(InviteLinkParser.extractToken("https://chefai.app/invite?token=")).isNull()
    }

    @Test
    fun `returns null for null or blank input`() {
        assertThat(InviteLinkParser.extractToken(null)).isNull()
        assertThat(InviteLinkParser.extractToken("")).isNull()
    }

    @Test
    fun `does not throw on malformed input`() {
        assertThat(InviteLinkParser.extractToken("not a url at all")).isNull()
        assertThat(InviteLinkParser.extractToken("https://chefai.app/invite?token=%")).isNull()
    }
}
