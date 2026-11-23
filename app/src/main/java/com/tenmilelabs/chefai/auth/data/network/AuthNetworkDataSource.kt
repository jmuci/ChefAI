package com.tenmilelabs.chefai.auth.data.network

import com.tenmilelabs.chefai.auth.data.network.dto.AuthResponse
import com.tenmilelabs.chefai.auth.data.network.dto.GoogleSignInRequest
import com.tenmilelabs.chefai.auth.data.network.dto.LoginRequest
import com.tenmilelabs.chefai.auth.data.network.dto.LogoutRequest
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

    /**
     * Revokes one session's refresh token server-side (`204` whether or not it existed). Throws
     * [AuthHttpException] on any other status.
     */
    suspend fun logout(request: LogoutRequest)

    /**
     * Exchanges a Google ID token for our own tokens (`POST /auth/google`). Failures carry the
     * backend's error code in [AuthHttpException.code] (`INVALID_GOOGLE_TOKEN`,
     * `GOOGLE_EMAIL_EXISTS`).
     */
    suspend fun googleSignIn(request: GoogleSignInRequest): AuthResponse

    /**
     * Links the signed-in account to a Google identity (`POST /auth/google/link`, authenticated;
     * `204` on success). Failures carry `INVALID_GOOGLE_TOKEN`, `GOOGLE_EMAIL_MISMATCH` or
     * `GOOGLE_IDENTITY_TAKEN` in [AuthHttpException.code].
     */
    suspend fun linkGoogle(request: GoogleSignInRequest)
}