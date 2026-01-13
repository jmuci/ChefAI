package com.tenmilelabs.chefai.auth.data.mapper

import com.tenmilelabs.chefai.auth.data.network.dto.AuthResponse
import com.tenmilelabs.chefai.auth.data.network.dto.TokenRefreshResponse
import com.tenmilelabs.chefai.auth.domain.model.AuthToken
import com.tenmilelabs.chefai.core.domain.model.User
import java.util.UUID

/**
 * Converts AuthResponse to domain User and AuthToken.
 */
fun AuthResponse.toUser(): User {
    return User(
        uuid = UUID.fromString(userId),
        displayName = username,
        email = email,
        avatarUrl = ""
    )
}

/**
 * Converts AuthResponse to domain AuthToken.
 */
fun AuthResponse.toAuthToken(): AuthToken {
    val currentTime = System.currentTimeMillis()
    val expiresAt = currentTime + (expiresIn * 1000) // Convert seconds to milliseconds
    return AuthToken(
        accessToken = token,
        refreshToken = refreshToken,
        expiresAt = expiresAt
    )
}

/**
 * Converts TokenRefreshResponse to domain AuthToken.
 */
fun TokenRefreshResponse.toAuthToken(): AuthToken {
    val currentTime = System.currentTimeMillis()
    val expiresAt = currentTime + (expiresIn * 1000) // Convert seconds to milliseconds
    return AuthToken(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = expiresAt
    )
}