package com.tenmilelabs.chefai.auth.domain

import com.tenmilelabs.chefai.auth.data.local.SecurePreferencesInterface
import com.tenmilelabs.chefai.auth.data.mapper.toAuthToken
import com.tenmilelabs.chefai.auth.data.mapper.toUser
import com.tenmilelabs.chefai.auth.data.network.AuthNetworkDataSource
import com.tenmilelabs.chefai.auth.data.network.AuthHttpException
import com.tenmilelabs.chefai.auth.data.network.dto.LoginRequest
import com.tenmilelabs.chefai.auth.data.network.dto.RegisterRequest
import com.tenmilelabs.chefai.auth.data.network.dto.RefreshTokenRequest
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
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Manages user authentication session state across the application.
 * This is a singleton that maintains the current user's authentication status.
 */
@Singleton
class SessionManager @Inject constructor(
    private val securePreferences: SecurePreferencesInterface,
    private val authNetworkDataSource: Provider<AuthNetworkDataSource>,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) : TokenProvider {

    private val _userSession = MutableStateFlow<UserSession>(UserSession.Loading)
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    init {
        // Load session on initialization
        applicationScope.launch {
            loadSession()
        }
    }

    /**
     * Loads the user session from secure storage.
     */
    suspend fun loadSession() {
        try {
            Timber.d("Loading user session...")
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
                    Timber.d("Access token expired, attempting refresh...")
                    val refreshResult = refreshToken()
                    if (refreshResult.isSuccess) {
                        Timber.d("Session refreshed successfully")
                    } else {
                        Timber.w("Failed to refresh session, user needs to login again")
                        _userSession.value = UserSession.Unauthenticated
                    }
                } else {
                    // Token is still valid, create user session
                    val user = User(
                        uuid = userUuid,
                        displayName = "User", // TODO: Fetch user details from backend
                        email = "",
                        avatarUrl = ""
                    )

                    _userSession.value = UserSession.Authenticated(
                        user = user,
                        authToken = authToken
                    )
                    Timber.d("Session loaded successfully for user: ${user.uuid}")
                }
            } else {
                // No stored session
                Timber.d("No stored session found")
                _userSession.value = UserSession.Unauthenticated
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load session")
            _userSession.value = UserSession.Unauthenticated
        }
    }

    /**
     * Logs in a user with credentials using the backend API.
     */
    suspend fun login(email: String, password: String): Result<User> {
        return try {
            Timber.d("Login attempt for: $email")
            _userSession.value = UserSession.Loading

            // Make API call to backend for authentication
            val response = authNetworkDataSource.get().login(
                LoginRequest(email = email, password = password)
            )
            
            val authToken = response.toAuthToken()
            val user = response.toUser()

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

            Timber.d("Login successful for user: ${user.uuid}")
            Result.success(user)
        } catch (e: Exception) {
            Timber.e(e, "Login failed")
            _userSession.value = UserSession.Unauthenticated
            Result.failure(e)
        }
    }

    /**
     * Registers a new user with the backend API.
     */
    suspend fun register(username: String, email: String, password: String): Result<User> {
        return try {
            Timber.d("Register attempt for: $email")
            _userSession.value = UserSession.Loading

            // Make API call to backend for registration
            val response = authNetworkDataSource.get().register(
                RegisterRequest(email = email, username = username, password = password)
            )
            
            val authToken = response.toAuthToken()
            val user = response.toUser()

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

            Timber.d("Registration successful for user: ${user.uuid}")
            Result.success(user)
        } catch (e: Exception) {
            Timber.e(e, "Registration failed")
            _userSession.value = UserSession.Unauthenticated
            Result.failure(e)
        }
    }

    /**
     * Logs out the current user and clears session data.
     */
    suspend fun logout() {
        try {
            val currentUser = getCurrentUser()
            Timber.d("Logging out user: ${currentUser?.uuid}")

            // Clear secure storage
            securePreferences.clearAuthData()

            // Clear session state
            _userSession.value = UserSession.Unauthenticated

            Timber.d("Logout successful")
        } catch (e: Exception) {
            Timber.e(e, "Error during logout")
            // Still mark as unauthenticated even if clear fails
            _userSession.value = UserSession.Unauthenticated
        }
    }

    /**
     * Refreshes the access token using the refresh token.
     */
    suspend fun refreshToken(): Result<Unit> {
        return try {
            val currentSession = _userSession.value
            if (currentSession !is UserSession.Authenticated) {
                return Result.failure(IllegalStateException("No active session to refresh"))
            }

            Timber.d("Refreshing access token...")

            // Make API call to backend to refresh token
            val response = authNetworkDataSource.get().refreshToken(
                RefreshTokenRequest(refreshToken = currentSession.authToken.refreshToken)
            )
            
            val newAuthToken = response.toAuthToken()

            // Update secure storage with new tokens
            securePreferences.updateAccessToken(
                accessToken = newAuthToken.accessToken,
                tokenExpiry = newAuthToken.expiresAt
            )

            // Note: In token rotation, we also need to update the refresh token
            securePreferences.updateRefreshToken(
                refreshToken = newAuthToken.refreshToken
            )

            // Update session state
            _userSession.value = UserSession.Authenticated(
                user = currentSession.user,
                authToken = newAuthToken
            )

            Timber.d("Token refreshed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Token refresh failed")
            // If refresh fails, we should consider logging out the user
            // But for now, just return the error
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
    override fun getAccessToken(): String? {
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
}