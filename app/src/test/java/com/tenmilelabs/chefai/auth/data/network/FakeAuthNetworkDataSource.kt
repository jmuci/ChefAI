package com.tenmilelabs.chefai.auth.data.network

import com.tenmilelabs.chefai.auth.data.network.dto.AuthResponse
import com.tenmilelabs.chefai.auth.data.network.dto.LoginRequest
import com.tenmilelabs.chefai.auth.data.network.dto.RefreshTokenRequest
import com.tenmilelabs.chefai.auth.data.network.dto.RegisterRequest
import com.tenmilelabs.chefai.auth.data.network.dto.TokenRefreshResponse
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Fake implementation of AuthNetworkDataSource for testing.
 * Provides mock responses for authentication operations.
 */
class FakeAuthNetworkDataSource : AuthNetworkDataSource {

    /**
     * Determines if the data source should throw exceptions.
     * Set to true to simulate API failures.
     */
    var shouldThrowError: Boolean = false

    /**
     * Custom exception to throw when shouldThrowError is true.
     * If null, throws a generic Exception.
     */
    var errorToThrow: Exception? = null

    /**
     * Simulated delay in milliseconds for network operations.
     * Set to > 0 to test loading states.
     */
    var networkDelayMs: Long = 0

    /**
     * Custom auth response to return. If null, returns a default fake response.
     */
    var authResponse: AuthResponse? = null

    /**
     * Custom token refresh response to return. If null, returns a default fake response.
     */
    var tokenRefreshResponse: TokenRefreshResponse? = null

    override suspend fun register(request: RegisterRequest): AuthResponse {
        if (networkDelayMs > 0) {
            delay(networkDelayMs)
        }

        if (shouldThrowError) {
            throw errorToThrow ?: Exception("Simulated network error during registration")
        }

        return authResponse ?: createDefaultAuthResponse(
            username = request.username,
            email = request.email
        )
    }

    override suspend fun login(request: LoginRequest): AuthResponse {
        if (networkDelayMs > 0) {
            delay(networkDelayMs)
        }

        if (shouldThrowError) {
            throw Exception("Simulated network error during login")
        }

        return authResponse ?: createDefaultAuthResponse(
            username = request.email.substringBefore("@"),
            email = request.email
        )
    }

    override suspend fun refreshToken(request: RefreshTokenRequest): TokenRefreshResponse {
        if (networkDelayMs > 0) {
            delay(networkDelayMs)
        }

        if (shouldThrowError) {
            throw Exception("Simulated network error during token refresh")
        }

        return tokenRefreshResponse ?: createDefaultTokenRefreshResponse()
    }

    /**
     * Creates a default fake auth response.
     */
    private fun createDefaultAuthResponse(username: String = "testuser", email: String = "test@example.com"): AuthResponse {
        return AuthResponse(
            token = "fake_access_token_${System.currentTimeMillis()}",
            refreshToken = "fake_refresh_token_${System.currentTimeMillis()}",
            userId = UuidV7Generator.newId().toString(),
            username = username,
            email = email,
            expiresIn = 3600 // 1 hour in seconds
        )
    }

    /**
     * Creates a default fake token refresh response.
     */
    private fun createDefaultTokenRefreshResponse(): TokenRefreshResponse {
        return TokenRefreshResponse(
            accessToken = "fake_new_access_token_${System.currentTimeMillis()}",
            refreshToken = "fake_new_refresh_token_${System.currentTimeMillis()}",
            userId = UuidV7Generator.newId().toString(),
            expiresIn = 3600 // 1 hour in seconds
        )
    }

    /**
     * Resets the fake data source to its default state.
     */
    fun reset() {
        shouldThrowError = false
        errorToThrow = null
        networkDelayMs = 0
        authResponse = null
        tokenRefreshResponse = null
    }
}