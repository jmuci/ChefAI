package com.tenmilelabs.chefai.domain.model

/**
 * Represents authentication tokens for API access.
 */
data class AuthToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long // Timestamp in milliseconds when access token expires
)
