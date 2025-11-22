package com.tenmilelabs.chefai.auth.domain

import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.data.local.FakeSecurePreferences
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.domain.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SessionManager.
 */
@ExperimentalCoroutinesApi
class SessionManagerTest {

    private lateinit var sessionManager: SessionManager
    private lateinit var fakeSecurePreferences: FakeSecurePreferences
    private lateinit var testScope: TestScope
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        testScope = TestScope(testDispatcher)
        fakeSecurePreferences = FakeSecurePreferences()

        // Create SessionManager with test scope
        sessionManager = SessionManager(
            securePreferences = fakeSecurePreferences,
            applicationScope = testScope
        )
    }

    @Test
    fun `initial state loads mock session automatically`() = testScope.runTest {
        // SessionManager automatically loads session in init block
        // Advance time to let the init coroutine complete
        advanceUntilIdle()

        val initialState = sessionManager.userSession.value
        Truth.assertThat(initialState).isInstanceOf(UserSession.Authenticated::class.java)
    }

    @Test
    fun `loadSession creates mock session when no stored data`() = testScope.runTest {
        // Given: No stored auth data
        fakeSecurePreferences.clearAuthData()

        // When: Loading session
        sessionManager.loadSession()
        advanceUntilIdle()

        // Then: Mock user session is created
        val session = sessionManager.userSession.first()
        Truth.assertThat(session).isInstanceOf(UserSession.Authenticated::class.java)

        val authenticated = session as UserSession.Authenticated
        assertThat(authenticated.user.displayName).isEqualTo("Test User")
    }

    @Test
    fun `logout clears session and secure storage`() = testScope.runTest {
        // Given: User is logged in
        sessionManager.loadSession()
        advanceUntilIdle()

        // When: User logs out
        sessionManager.logout()

        // Then: Session is unauthenticated and storage is cleared
        val session = sessionManager.userSession.first()
        Truth.assertThat(session).isEqualTo(UserSession.Unauthenticated)
        Truth.assertThat(fakeSecurePreferences.getUserUuid().first()).isNull()
    }

    @Test
    fun `getCurrentUser returns null when unauthenticated`() = testScope.runTest {
        // Given: User is not logged in
        sessionManager.logout()

        // When: Getting current user
        val user = sessionManager.getCurrentUser()

        // Then: Returns null
        Truth.assertThat(user).isNull()
    }

    @Test
    fun `getCurrentUser returns user when authenticated`() = testScope.runTest {
        // Given: User is logged in
        sessionManager.loadSession()
        advanceUntilIdle()

        // When: Getting current user
        val user = sessionManager.getCurrentUser()

        // Then: Returns user
        Truth.assertThat(user).isNotNull()
        Truth.assertThat(user?.displayName).isEqualTo("Test User")
    }

    @Test
    fun `getAccessToken returns null when unauthenticated`() = testScope.runTest {
        // Given: User is not logged in
        sessionManager.logout()

        // When: Getting access token
        val token = sessionManager.getAccessToken()

        // Then: Returns null
        Truth.assertThat(token).isNull()
    }

    @Test
    fun `getAccessToken returns token when authenticated`() = testScope.runTest {
        // Given: User is logged in
        sessionManager.loadSession()
        advanceUntilIdle()

        // When: Getting access token
        val token = sessionManager.getAccessToken()

        // Then: Returns token
        Truth.assertThat(token).isNotNull()
        Truth.assertThat(token).startsWith("mock_access_token_")
    }

    @Test
    fun `isTokenExpired returns true when unauthenticated`() = testScope.runTest {
        // Given: User is not logged in
        sessionManager.logout()

        // When: Checking if token is expired
        val isExpired = sessionManager.isTokenExpired()

        // Then: Returns true
        Truth.assertThat(isExpired).isTrue()
    }

    @Test
    fun `isTokenExpired returns false for valid token`() = testScope.runTest {
        // Given: User is logged in with fresh token
        sessionManager.loadSession()
        advanceUntilIdle()

        // When: Checking if token is expired (with buffer)
        val isExpired = sessionManager.isTokenExpired(bufferMillis = 5 * 60 * 1000)

        // Then: Returns false (mock token expires in 24 hours)
        Truth.assertThat(isExpired).isFalse()
    }
}