package com.tenmilelabs.chefai.auth.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Response body for successful authentication (login or register).
 */
@Serializable
data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val userId: String,
    val username: String,
    val email: String,
    val expiresIn: Long, // Expiration time in seconds
    /** False for an account created with Google only. Absent from older backends: assume true. */
    val hasPassword: Boolean = true,
    /** Linked identity providers, e.g. `["google"]`. Absent from older backends. */
    val providers: List<String> = emptyList(),
)

/**
 * Response body for token refresh.
 */
@Serializable
data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val expiresIn: Long
)

/**
 * Request body for user registration.
 */
@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String
)

/**
 * Request body for user login.
 */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

/**
 * Request body for token refresh.
 */
@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

/** Request body for `POST /auth/logout`: the refresh token of the session being ended. */
@Serializable
data class LogoutRequest(
    val refreshToken: String
)

/** Request body for `POST /auth/google` and `POST /auth/google/link`. Never log [idToken]. */
@Serializable
data class GoogleSignInRequest(
    val idToken: String
) {
    override fun toString(): String = "GoogleSignInRequest(idToken=<redacted>)"
}

/**
 * Error response from the API.
 */
@Serializable
data class ApiErrorResponse(
    val message: String? = null,
    val error: String? = null,
    /** Machine-readable reason, e.g. `GOOGLE_EMAIL_EXISTS`. */
    val code: String? = null,
)