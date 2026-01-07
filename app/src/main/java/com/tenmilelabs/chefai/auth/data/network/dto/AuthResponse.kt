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
    val expiresIn: Long // Expiration time in seconds
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

/**
 * Error response from the API.
 */
@Serializable
data class ApiErrorResponse(
    val message: String? = null,
    val error: String? = null
)