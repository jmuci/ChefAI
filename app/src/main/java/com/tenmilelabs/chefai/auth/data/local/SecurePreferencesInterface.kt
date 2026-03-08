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
        displayName: String,
        email: String,
        avatarUrl: String,
        accessToken: String,
        refreshToken: String,
        tokenExpiry: Long
    )

    fun getUserUuid(): Flow<UUID?>
    fun getDisplayName(): Flow<String?>
    fun getUserEmail(): Flow<String?>
    fun getUserAvatarUrl(): Flow<String?>
    fun getAccessToken(): Flow<String?>
    fun getRefreshToken(): Flow<String?>
    fun getTokenExpiry(): Flow<Long?>
    suspend fun clearAuthData()
    suspend fun updateAccessToken(accessToken: String, tokenExpiry: Long)
    suspend fun updateRefreshToken(refreshToken: String)

    /** Stores the anonymous local user ID (persists across app restarts). */
    suspend fun saveLocalUserId(uuid: UUID)

    /** Reads the anonymous local user ID, or null if none has been stored. */
    fun getLocalUserId(): Flow<UUID?>

    /** Clears the anonymous local user ID (e.g., after account upgrade). */
    suspend fun clearLocalUserId()

    /** Persists the last authenticated user ID across logouts and app restarts. */
    suspend fun setCurrentUserId(userId: UUID)

    /** Reads the last authenticated user ID, or null if no user has logged in yet. */
    fun getStoredCurrentUserId(): Flow<UUID?>

    /** Persists the email used during a successful login so it can be prefilled next time. */
    suspend fun saveLastLoginEmail(email: String)

    /** Reads the last successfully used login email, or null if none has been stored. */
    fun getLastLoginEmail(): Flow<String?>
}
