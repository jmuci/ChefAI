package com.tenmilelabs.chefai.auth.data.network

import com.tenmilelabs.chefai.auth.data.network.dto.AuthResponse
import com.tenmilelabs.chefai.auth.data.network.dto.LoginRequest
import com.tenmilelabs.chefai.auth.data.network.dto.RefreshTokenRequest
import com.tenmilelabs.chefai.auth.data.network.dto.RegisterRequest
import com.tenmilelabs.chefai.auth.data.network.dto.TokenRefreshResponse
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator

/**
 * Fake implementation of AuthNetworkDataSource for instrumented tests.
 * Provides mock responses for authentication operations.
 * 
 * This version allows pre-seeding users for testing existing user flows.
 * TODO This fakes duplicate the ones in the unit tests package.
 * Put all fakes in a test utilities module in the future to remove redundancy.
 */
class FakeAuthNetworkDataSource : AuthNetworkDataSource {

    /**
     * Map of email to AuthResponse for pre-existing users.
     * Use this to seed test users before running tests.
     */
    val existingUsers = mutableMapOf<String, AuthResponse>()

    /**
     * Determines if the data source should throw exceptions.
     * Set to true to simulate API failures.
     */
    var shouldThrowError: Boolean = false

    /**
     * Simulated delay in milliseconds for network operations.
     * Set to > 0 to test loading states.
     */
    var networkDelayMs: Long = 0

    override suspend fun register(request: RegisterRequest): AuthResponse {
        if (networkDelayMs > 0) {
            kotlinx.coroutines.delay(networkDelayMs)
        }

        if (shouldThrowError) {
            throw Exception("Simulated network error during registration")
        }

        // Check if user already exists
        if (existingUsers.containsKey(request.email)) {
            throw Exception("User already exists")
        }

        // Create new user
        val response = createAuthResponse(
            username = request.username,
            email = request.email
        )
        existingUsers[request.email] = response
        return response
    }

    override suspend fun login(request: LoginRequest): AuthResponse {
        if (networkDelayMs > 0) {
            kotlinx.coroutines.delay(networkDelayMs)
        }

        if (shouldThrowError) {
            throw Exception("Simulated network error during login")
        }

        // Check if user exists in existingUsers map
        val existingUser = existingUsers[request.email]
        if (existingUser != null) {
            return existingUser
        }

        // For backward compatibility, create a default response
        return createAuthResponse(
            username = request.email.substringBefore("@"),
            email = request.email
        )
    }

    override suspend fun refreshToken(request: RefreshTokenRequest): TokenRefreshResponse {
        if (networkDelayMs > 0) {
            kotlinx.coroutines.delay(networkDelayMs)
        }

        if (shouldThrowError) {
            throw Exception("Simulated network error during token refresh")
        }

        return createTokenRefreshResponse()
    }

    /**
     * Creates a fake auth response for testing.
     */
    private fun createAuthResponse(username: String, email: String): AuthResponse {
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
    private fun createTokenRefreshResponse(): TokenRefreshResponse {
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
        networkDelayMs = 0
        existingUsers.clear()
    }
}
