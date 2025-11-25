package com.tenmilelabs.chefai.auth.domain

import com.tenmilelabs.chefai.auth.data.local.SecurePreferencesInterface
import com.tenmilelabs.chefai.core.data.local.util.decodeHex
import com.tenmilelabs.chefai.core.data.local.util.toUuid
import com.tenmilelabs.chefai.core.di.ApplicationScope
import com.tenmilelabs.chefai.auth.domain.model.AuthToken
import com.tenmilelabs.chefai.core.domain.model.User
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages user authentication session state across the application.
 * This is a singleton that maintains the current user's authentication status.
 */
@Singleton
class SessionManager @Inject constructor(
    private val securePreferences: SecurePreferencesInterface,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) {
    private val _userSession = MutableStateFlow<UserSession>(UserSession.Loading)
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    // TODO replace with backend API call
    // Mock user for development (until backend login is ready)
    private val mockUser = User(
        uuid = "F47AC10B58CC4372A5670E02B2C3D479".decodeHex().toUuid(),
        displayName = "Test User",
        email = "test@chefai.com",
        avatarUrl = "https://lh3.googleusercontent.com/a/ACg8ocJkiHEp4Du1l-Y4-raRQ6opmwP2Dihq9JIi47wJUn1Aki3d_8Z6=s288-c-no"
    )

    init {
        // Load session on initialization
        applicationScope.launch {
            loadSession()
        }
    }

    /**
     * Loads the user session from secure storage.
     * For now, returns a mock user until backend authentication is ready.
     */
    suspend fun loadSession() {
        try {
            Timber.Forest.d("Loading user session...")
            _userSession.value = UserSession.Loading

            val userUuid = securePreferences.getUserUuid().first()
            val accessToken = securePreferences.getAccessToken().first()
            val refreshToken = securePreferences.getRefreshToken().first()
            val tokenExpiry = securePreferences.getTokenExpiry().first()

            // If we have stored credentials, validate them
            if (userUuid != null && accessToken != null && refreshToken != null && tokenExpiry != null) {
                val authToken = AuthToken(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = tokenExpiry
                )

                // Check if token is expired
                val currentTime = System.currentTimeMillis()
                if (currentTime >= tokenExpiry) {
                    Timber.Forest.d("Access token expired, attempting refresh...")
                    // TODO: Implement token refresh when backend is ready
                    // For now, use mock user
                    setMockSession()
                } else {
                    // TODO: Fetch user details from backend when ready
                    // For now, use mock user with stored tokens
                    val user = if (userUuid == mockUser.uuid) {
                        mockUser
                    } else {
                        // Create a basic user object
                        User(
                            uuid = userUuid,
                            displayName = "User",
                            email = "",
                            avatarUrl = ""
                        )
                    }

                    _userSession.value = UserSession.Authenticated(
                        user = user,
                        authToken = authToken
                    )
                    Timber.Forest.d("Session loaded successfully for user: ${user.uuid}")
                }
            } else {
                // No stored session, use mock user for development
                Timber.Forest.d("No stored session found, using mock user")
                setMockSession()
            }
        } catch (e: Exception) {
            Timber.Forest.e(e, "Failed to load session")
            _userSession.value = UserSession.Unauthenticated
        }
    }

    /**
     * Logs in a user with credentials.
     * TODO: Implement actual API call when backend is ready.
     */
    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            Timber.Forest.d("Login attempt for: $email")
            _userSession.value = UserSession.Loading

            // TODO: Make API call to backend for authentication
            // val response = authApi.login(email, password)
            // val authToken = response.toAuthToken()
            // val user = response.toUser()

            // For now, use mock data
            val authToken = createMockAuthToken()
            val user = mockUser.copy(email = email)

            // Save to secure storage
            securePreferences.saveAuthData(
                userUuid = user.uuid,
                accessToken = authToken.accessToken,
                refreshToken = authToken.refreshToken,
                tokenExpiry = authToken.expiresAt
            )

            _userSession.value = UserSession.Authenticated(
                user = user,
                authToken = authToken
            )

            Timber.Forest.d("Login successful for user: ${user.uuid}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.Forest.e(e, "Login failed")
            _userSession.value = UserSession.Unauthenticated
            Result.failure(e)
        }
    }

    /**
     * Logs out the current user and clears session data.
     */
    suspend fun logout() {
        try {
            Timber.Forest.d("Logging out user...")

            // Clear secure storage
            securePreferences.clearAuthData()

            // Clear session state
            _userSession.value = UserSession.Unauthenticated

            Timber.Forest.d("Logout successful")
        } catch (e: Exception) {
            Timber.Forest.e(e, "Error during logout")
            // Still mark as unauthenticated even if clear fails
            _userSession.value = UserSession.Unauthenticated
        }
    }

    /**
     * Refreshes the access token using the refresh token.
     * TODO: Implement actual API call when backend is ready.
     */
    suspend fun refreshToken(): Result<Unit> {
        return try {
            val currentSession = _userSession.value
            if (currentSession !is UserSession.Authenticated) {
                return Result.failure(IllegalStateException("No active session to refresh"))
            }

            Timber.Forest.d("Refreshing access token...")

            // TODO: Make API call to backend to refresh token
            // val response = authApi.refreshToken(currentSession.authToken.refreshToken)
            // val newAuthToken = response.toAuthToken()

            // For now, generate mock token
            val newAuthToken = createMockAuthToken()

            // Update secure storage with new tokens
            securePreferences.updateAccessToken(
                accessToken = newAuthToken.accessToken,
                tokenExpiry = newAuthToken.expiresAt
            )

            // Update session state
            _userSession.value = UserSession.Authenticated(
                user = currentSession.user,
                authToken = newAuthToken
            )

            Timber.Forest.d("Token refreshed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.Forest.e(e, "Token refresh failed")
            Result.failure(e)
        }
    }

    /**
     * Gets the current authenticated user, or null if not authenticated.
     */
    fun getCurrentUser(): User? {
        return when (val session = _userSession.value) {
            is UserSession.Authenticated -> session.user
            else -> null
        }
    }

    /**
     * Gets the current access token, or null if not authenticated.
     */
    fun getAccessToken(): String? {
        return when (val session = _userSession.value) {
            is UserSession.Authenticated -> session.authToken.accessToken
            else -> null
        }
    }

    /**
     * Checks if the current access token is expired or about to expire.
     * @param bufferMillis Buffer time in milliseconds before actual expiry (default 5 minutes)
     */
    fun isTokenExpired(bufferMillis: Long = 5 * 60 * 1000): Boolean {
        val session = _userSession.value
        if (session !is UserSession.Authenticated) return true

        val currentTime = System.currentTimeMillis()
        return currentTime >= (session.authToken.expiresAt - bufferMillis)
    }

    /**
     * Sets up a mock session for development purposes.
     */
    private suspend fun setMockSession() {
        val mockAuthToken = createMockAuthToken()

        // Save mock session
        securePreferences.saveAuthData(
            userUuid = mockUser.uuid,
            accessToken = mockAuthToken.accessToken,
            refreshToken = mockAuthToken.refreshToken,
            tokenExpiry = mockAuthToken.expiresAt
        )

        _userSession.value = UserSession.Authenticated(
            user = mockUser,
            authToken = mockAuthToken
        )

        Timber.Forest.d("Mock session initialized for user: ${mockUser.uuid}")
    }

    /**
     * Creates a mock auth token for development.
     */
    private fun createMockAuthToken(): AuthToken {
        val currentTime = System.currentTimeMillis()
        return AuthToken(
            accessToken = "mock_access_token_${UUID.randomUUID()}",
            refreshToken = "mock_refresh_token_${UUID.randomUUID()}",
            expiresAt = currentTime + (24 * 60 * 60 * 1000) // 24 hours from now
        )
    }
}