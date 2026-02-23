package com.tenmilelabs.chefai.auth.domain

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.data.local.FakeSecurePreferences
import com.tenmilelabs.chefai.auth.data.network.FakeAuthNetworkDataSource
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

/**
 * Unit tests for SessionManager.
 */
@ExperimentalCoroutinesApi
class SessionManagerTest {

    private lateinit var sessionManager: SessionManager
    private lateinit var fakeSecurePreferences: FakeSecurePreferences
    private lateinit var fakeAuthNetworkDataSource: FakeAuthNetworkDataSource
    private lateinit var testScope: TestScope
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        testScope = TestScope(testDispatcher)
        fakeSecurePreferences = FakeSecurePreferences()
        fakeAuthNetworkDataSource = FakeAuthNetworkDataSource()

        // Create SessionManager with test scope
        sessionManager = SessionManager(
            securePreferences = fakeSecurePreferences,
            authNetworkDataSource = { fakeAuthNetworkDataSource },
            applicationScope = testScope
        )
    }

    @Test
    fun `initial state is unauthenticated when no stored data`() = testScope.runTest {
        // SessionManager automatically loads session in init block
        // Advance time to let the init coroutine complete
        val initialState = sessionManager.userSession.value
        assertThat(initialState).isEqualTo(UserSession.Unauthenticated)
    }

    @Test
    fun `load session returns unauthenticated when no stored data`() = testScope.runTest {
        // Given: No stored auth data
        fakeSecurePreferences.clearAuthData()

        // When: Loading session
        sessionManager.loadSession()
        // Then: Session is unauthenticated
        val session = sessionManager.userSession.first()
        assertThat(session).isEqualTo(UserSession.Unauthenticated)
    }

    @Test
    fun `login with valid credentials returns user and saves session`() = testScope.runTest {
        // Given: Valid credentials
        val email = "test@example.com"
        val password = "password123"

        // When: Logging in
        val result = sessionManager.login(email, password)
        // Then: Login succeeds and session is authenticated
        assertThat(result.isSuccess).isTrue()
        val user = result.getOrNull()
        assertThat(user).isNotNull()
        assertThat(user?.email).isEqualTo(email)

        val session = sessionManager.userSession.first()
        assertThat(session).isInstanceOf(UserSession.Authenticated::class.java)
    }

    @Test
    fun `login with network error returns failure`() = testScope.runTest {
        // Given: Network error is simulated
        fakeAuthNetworkDataSource.shouldThrowError = true

        // When: Logging in
        val result = sessionManager.login("test@example.com", "password123")
        // Then: Login fails and session is unauthenticated
        assertThat(result.isFailure).isTrue()

        val session = sessionManager.userSession.first()
        assertThat(session).isEqualTo(UserSession.Unauthenticated)
    }

    @Test
    fun `register with valid credentials creates user and session`() = testScope.runTest {
        // Given: Valid registration data
        val username = "testuser"
        val email = "test@example.com"
        val password = "password123"

        // When: Registering
        val result = sessionManager.register(username, email, password)
        // Then: Registration succeeds and session is authenticated
        assertThat(result.isSuccess).isTrue()
        val user = result.getOrNull()
        assertThat(user).isNotNull()
        assertThat(user?.displayName).isEqualTo(username)
        assertThat(user?.email).isEqualTo(email)

        val session = sessionManager.userSession.first()
        assertThat(session).isInstanceOf(UserSession.Authenticated::class.java)
    }

    @Test
    fun `register with network error returns failure`() = testScope.runTest {
        // Given: Network error is simulated
        fakeAuthNetworkDataSource.shouldThrowError = true

        // When: Registering
        val result = sessionManager.register("testuser", "test@example.com", "password123")
        // Then: Registration fails and session is unauthenticated
        assertThat(result.isFailure).isTrue()

        val session = sessionManager.userSession.first()
        assertThat(session).isEqualTo(UserSession.Unauthenticated)
    }

    @Test
    fun `logout clears session and secure storage`() = testScope.runTest {
        // Given: User is logged in
        sessionManager.login("test@example.com", "password123")
        // When: User logs out
        sessionManager.logout()

        // Then: Session is unauthenticated and storage is cleared
        val session = sessionManager.userSession.first()
        assertThat(session).isEqualTo(UserSession.Unauthenticated)
        assertThat(fakeSecurePreferences.getUserUuid().first()).isNull()
    }

    @Test
    fun `get current user returns null when unauthenticated`() = testScope.runTest {
        // Given: User is not logged in (already unauthenticated from setup)

        // When: Getting current user
        val user = sessionManager.getCurrentUser()

        // Then: Returns null
        assertThat(user).isNull()
    }

    @Test
    fun `get current user returns user when authenticated`() = testScope.runTest {
        // Given: User is logged in
        sessionManager.login("test@example.com", "password123")
        // When: Getting current user
        val user = sessionManager.getCurrentUser()

        // Then: Returns user from FakeAuthNetworkDataSource defaults
        assertThat(user).isNotNull()
        assertThat(user?.email).isEqualTo("test@example.com")
    }

    @Test
    fun `get access token returns null when unauthenticated`() = testScope.runTest {
        // Given: User is not logged in

        // When: Getting access token
        val token = sessionManager.getAccessToken()

        // Then: Returns null
        assertThat(token).isNull()
    }

    @Test
    fun `get access token returns token when authenticated`() = testScope.runTest {
        // Given: User is logged in
        sessionManager.login("test@example.com", "password123")
        // When: Getting access token
        val token = sessionManager.getAccessToken()

        // Then: Returns token from FakeAuthNetworkDataSource
        assertThat(token).isNotNull()
        assertThat(token).startsWith("fake_access_token_")
    }

    @Test
    fun `is token expired returns true when unauthenticated`() = testScope.runTest {
        // Given: User is not logged in

        // When: Checking if token is expired
        val isExpired = sessionManager.isTokenExpired()

        // Then: Returns true
        assertThat(isExpired).isTrue()
    }

    @Test
    fun `is token expired returns false for valid token`() = testScope.runTest {
        // Given: User is logged in with fresh token
        sessionManager.login("test@example.com", "password123")
        // When: Checking if token is expired (with buffer)
        val isExpired = sessionManager.isTokenExpired(bufferMillis = 5 * 60 * 1000)

        // Then: Returns false (fake token expires in 1 hour)
        assertThat(isExpired).isFalse()
    }

    @Test
    fun `load session restores session from storage`() = testScope.runTest {
        // Given: User is logged in and session is saved
        sessionManager.login("restored@example.com", "password123")
        val originalSession = sessionManager.userSession.value as UserSession.Authenticated

        // When: Creating new SessionManager that loads from storage
        val newSessionManager = SessionManager(
            securePreferences = fakeSecurePreferences,
            authNetworkDataSource = Provider { fakeAuthNetworkDataSource },
            applicationScope = testScope
        )
        advanceUntilIdle()

        // Then: Session is restored from storage
        val restoredSession = newSessionManager.userSession.value
        assertThat(restoredSession).isInstanceOf(UserSession.Authenticated::class.java)
        val authenticated = restoredSession as UserSession.Authenticated
        assertThat(authenticated.user.uuid).isEqualTo(originalSession.user.uuid)
    }

    @Test
    fun `load session restores user details from storage`() = testScope.runTest {
        // Given: User registers so full profile is saved to storage
        val username = "Jane"
        val email = "jane@example.com"
        sessionManager.register(username, email, "password123")
        val originalSession = sessionManager.userSession.value as UserSession.Authenticated

        // When: A new SessionManager instance loads the persisted session
        val newSessionManager = SessionManager(
            securePreferences = fakeSecurePreferences,
            authNetworkDataSource = Provider { fakeAuthNetworkDataSource },
            applicationScope = testScope
        )
        advanceUntilIdle()

        // Then: displayName and email are restored from storage instead of fallback placeholders
        val restoredSession = newSessionManager.userSession.value
        assertThat(restoredSession).isInstanceOf(UserSession.Authenticated::class.java)
        val authenticated = restoredSession as UserSession.Authenticated
        assertThat(authenticated.user.uuid).isEqualTo(originalSession.user.uuid)
        assertThat(authenticated.user.displayName).isEqualTo(originalSession.user.displayName)
        assertThat(authenticated.user.email).isEqualTo(originalSession.user.email)
    }

    @Test
    fun `login persists user display name and email`() = testScope.runTest {
        // Given / When: User logs in
        val email = "alice@example.com"
        sessionManager.login(email, "password123")

        // Then: displayName derived from email prefix is stored and accessible
        val session = sessionManager.userSession.value as UserSession.Authenticated
        assertThat(session.user.email).isEqualTo(email)
        // FakeAuthNetworkDataSource derives username from email prefix
        assertThat(session.user.displayName).isEqualTo(email.substringBefore("@"))

        // And: details survive a new SessionManager loading from the same storage
        val newSessionManager = SessionManager(
            securePreferences = fakeSecurePreferences,
            authNetworkDataSource = Provider { fakeAuthNetworkDataSource },
            applicationScope = testScope
        )
        advanceUntilIdle()
        val restoredSession = newSessionManager.userSession.value as UserSession.Authenticated
        assertThat(restoredSession.user.displayName).isEqualTo(session.user.displayName)
        assertThat(restoredSession.user.email).isEqualTo(session.user.email)
    }
}