package com.tenmilelabs.chefai.auth.util

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.AuthToken
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.domain.model.User
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

class SessionUtilsTest {

    private val user = User(
        uuid = UUID.randomUUID(),
        displayName = "Chef",
        email = "chef@example.com",
        avatarUrl = "",
    )

    private fun sessionManager(session: UserSession): SessionManager {
        val flow = MutableStateFlow(session)
        return mockk { every { userSession } returns flow }
    }

    @Test
    fun `getCurrentUserOrNull returns the user when authenticated`() {
        val authenticated = UserSession.Authenticated(
            user = user,
            authToken = AuthToken("access", "refresh", 0L),
        )

        assertThat(sessionManager(authenticated).getCurrentUserOrNull()).isEqualTo(user)
    }

    @Test
    fun `getCurrentUserOrNull returns null when anonymous`() {
        val anonymous = UserSession.Anonymous(UUID.randomUUID())

        assertThat(sessionManager(anonymous).getCurrentUserOrNull()).isNull()
    }

    @Test
    fun `getCurrentUserOrNull returns null while loading`() {
        assertThat(sessionManager(UserSession.Loading).getCurrentUserOrNull()).isNull()
    }

    @Test
    fun `isAuthenticated is true only for the Authenticated session`() {
        val authenticated = UserSession.Authenticated(user, AuthToken("a", "r", 0L))
        val anonymous = UserSession.Anonymous(UUID.randomUUID())

        assertThat(sessionManager(authenticated).isAuthenticated()).isTrue()
        assertThat(sessionManager(anonymous).isAuthenticated()).isFalse()
        assertThat(sessionManager(UserSession.Loading).isAuthenticated()).isFalse()
    }

    @Test
    fun `observeCurrentUser emits the user, then null after logout`() = runTest {
        val flow = MutableStateFlow<UserSession>(
            UserSession.Authenticated(user, AuthToken("a", "r", 0L))
        )
        val manager: SessionManager = mockk { every { userSession } returns flow }

        manager.observeCurrentUser().test {
            assertThat(awaitItem()).isEqualTo(user)

            flow.value = UserSession.Anonymous(UUID.randomUUID())

            assertThat(awaitItem()).isNull()
        }
    }
}
