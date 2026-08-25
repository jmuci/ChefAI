package com.tenmilelabs.chefai.auth.data.mapper

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.data.network.dto.AuthResponse
import com.tenmilelabs.chefai.auth.data.network.dto.TokenRefreshResponse
import org.junit.Test
import java.util.UUID

class AuthMapperTest {

    private fun authResponse(
        userId: String = UUID.randomUUID().toString(),
        expiresIn: Long = 3_600L,
    ) = AuthResponse(
        token = "access-token",
        refreshToken = "refresh-token",
        userId = userId,
        username = "chef",
        email = "chef@example.com",
        expiresIn = expiresIn,
    )

    @Test
    fun `toUser maps id, username and email, leaving avatar blank`() {
        val response = authResponse()

        val user = response.toUser()

        assertThat(user.uuid).isEqualTo(UUID.fromString(response.userId))
        assertThat(user.displayName).isEqualTo(response.username)
        assertThat(user.email).isEqualTo(response.email)
        assertThat(user.avatarUrl).isEmpty()
    }

    @Test
    fun `AuthResponse toAuthToken converts expiresIn from seconds to an absolute millis deadline`() {
        val before = System.currentTimeMillis()

        val token = authResponse(expiresIn = 60L).toAuthToken()

        val after = System.currentTimeMillis()
        assertThat(token.accessToken).isEqualTo("access-token")
        assertThat(token.refreshToken).isEqualTo("refresh-token")
        // expiresAt = now + 60_000ms, bounded by the two timestamps taken around the call.
        assertThat(token.expiresAt).isAtLeast(before + 60_000L)
        assertThat(token.expiresAt).isAtMost(after + 60_000L)
    }

    @Test
    fun `TokenRefreshResponse toAuthToken converts expiresIn the same way`() {
        val before = System.currentTimeMillis()
        val response = TokenRefreshResponse(
            accessToken = "new-access",
            refreshToken = "new-refresh",
            userId = UUID.randomUUID().toString(),
            expiresIn = 120L,
        )

        val token = response.toAuthToken()

        val after = System.currentTimeMillis()
        assertThat(token.accessToken).isEqualTo("new-access")
        assertThat(token.refreshToken).isEqualTo("new-refresh")
        assertThat(token.expiresAt).isAtLeast(before + 120_000L)
        assertThat(token.expiresAt).isAtMost(after + 120_000L)
    }
}
