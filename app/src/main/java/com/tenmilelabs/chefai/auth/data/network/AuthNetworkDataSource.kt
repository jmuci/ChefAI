package com.tenmilelabs.chefai.auth.data.network

import com.tenmilelabs.chefai.auth.data.network.dto.AuthResponse
import com.tenmilelabs.chefai.auth.data.network.dto.LoginRequest
import com.tenmilelabs.chefai.auth.data.network.dto.RefreshTokenRequest
import com.tenmilelabs.chefai.auth.data.network.dto.RegisterRequest
import com.tenmilelabs.chefai.auth.data.network.dto.TokenRefreshResponse

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