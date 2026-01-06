package com.tenmilelabs.chefai.auth.data.network

import com.tenmilelabs.chefai.auth.data.network.dto.*

/**
 * Network data source for authentication operations.
 */
interface AuthNetworkDataSource {
    /**
     * Registers a new user with the backend.
     */
    suspend fun register(request: RegisterRequest): AuthResponse

    /**
     * Logs in an existing user.
     */
    suspend fun login(request: LoginRequest): AuthResponse

    /**
     * Refreshes the access token using a refresh token.
     */
    suspend fun refreshToken(request: RefreshTokenRequest): TokenRefreshResponse
}