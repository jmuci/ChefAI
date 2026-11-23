package com.tenmilelabs.chefai.auth.ui.google

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

class GoogleIdTokenEmailTest {

    private fun token(payloadJson: String): String {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.toByteArray())
        return "e30.$payload.c2ln"
    }

    @Test
    fun `given a token with an email claim, when read, then returns it`() {
        assertThat(googleIdTokenEmail(token("""{"sub":"1","email":"jose@example.com"}"""))).isEqualTo("jose@example.com")
    }

    @Test
    fun `given a token without an email claim, when read, then null`() {
        assertThat(googleIdTokenEmail(token("""{"sub":"1"}"""))).isNull()
    }

    @Test
    fun `given a blank email claim, when read, then null`() {
        assertThat(googleIdTokenEmail(token("""{"email":" "}"""))).isNull()
    }

    @Test
    fun `given garbage, when read, then null`() {
        assertThat(googleIdTokenEmail("garbage")).isNull()
        assertThat(googleIdTokenEmail("a.!!!.c")).isNull()
        assertThat(googleIdTokenEmail(token("not json"))).isNull()
        assertThat(googleIdTokenEmail("")).isNull()
    }
}
