package com.tenmilelabs.chefai.auth.data.local

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Interface for secure storage of authentication data.
 * Allows for easy testing with fake implementations.
 */
interface SecurePreferencesInterface {
    suspend fun saveAuthData(
        userUuid: UUID,
        accessToken: String,
        refreshToken: String,
        tokenExpiry: Long
    )

    fun getUserUuid(): Flow<UUID?>
    fun getAccessToken(): Flow<String?>
    fun getRefreshToken(): Flow<String?>
    fun getTokenExpiry(): Flow<Long?>
    suspend fun clearAuthData()
    suspend fun updateAccessToken(accessToken: String, tokenExpiry: Long)
    suspend fun updateRefreshToken(refreshToken: String)
}
