package com.tenmilelabs.chefai.auth.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages secure storage of authentication data using EncryptedSharedPreferences.
 * Uses Android's Security Crypto library to encrypt sensitive data at rest with AES256-GCM.
 *
 * This implementation ensures that all authentication tokens and user IDs are actually
 * encrypted on disk, unlike the DataStore approach which stores data in plain text.
 */
@Singleton
class SecurePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SecurePreferencesInterface {
    companion object {
        private const val SECURE_PREFS_FILE_NAME = "chefai_secure_prefs"

        // Preference keys
        private const val KEY_USER_UUID = "user_uuid"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
    }

    // Create MasterKey for encryption (hardware-backed when available)
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // Create EncryptedSharedPreferences - this actually encrypts data at rest
    // Keys are encrypted with AES256-SIV, values with AES256-GCM
    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        SECURE_PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Flow-based state for reactive observation
    private val prefsFlow = MutableStateFlow(getCurrentPrefsMap())

    /**
     * Gets current preferences as a map for flow emission.
     */
    private fun getCurrentPrefsMap(): Map<String, Any?> {
        return mapOf(
            KEY_USER_UUID to encryptedPrefs.getString(KEY_USER_UUID, null),
            KEY_ACCESS_TOKEN to encryptedPrefs.getString(KEY_ACCESS_TOKEN, null),
            KEY_REFRESH_TOKEN to encryptedPrefs.getString(KEY_REFRESH_TOKEN, null),
            KEY_TOKEN_EXPIRY to encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, -1L).takeIf { it != -1L }
        )
    }

    /**
     * Updates the flow with current preferences.
     */
    private fun notifyChange() {
        prefsFlow.value = getCurrentPrefsMap()
    }

    /**
     * Stores user authentication data securely (encrypted at rest).
     * Data is encrypted using AES256-GCM before being written to disk.
     */
    override suspend fun saveAuthData(
        userUuid: UUID,
        accessToken: String,
        refreshToken: String,
        tokenExpiry: Long
    ) {
        try {
            encryptedPrefs.edit()
                .putString(KEY_USER_UUID, userUuid.toString())
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putLong(KEY_TOKEN_EXPIRY, tokenExpiry)
                .apply()

            notifyChange()
            Timber.d("Auth data saved securely (AES256-GCM encrypted) for user: $userUuid")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save auth data")
            throw e
        }
    }

    /**
     * Retrieves the stored user UUID as a Flow.
     * Data is decrypted on read.
     */
    override fun getUserUuid(): Flow<UUID?> = prefsFlow.map { prefs ->
        (prefs[KEY_USER_UUID] as? String)?.let { uuidString ->
            try {
                UUID.fromString(uuidString)
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Invalid UUID format in preferences")
                null
            }
        }
    }

    /**
     * Retrieves the stored access token as a Flow.
     * Data is decrypted on read.
     */
    override fun getAccessToken(): Flow<String?> = prefsFlow.map { prefs ->
        prefs[KEY_ACCESS_TOKEN] as? String
    }

    /**
     * Retrieves the stored refresh token as a Flow.
     * Data is decrypted on read.
     */
    override fun getRefreshToken(): Flow<String?> = prefsFlow.map { prefs ->
        prefs[KEY_REFRESH_TOKEN] as? String
    }

    /**
     * Retrieves the token expiry timestamp as a Flow.
     * Data is decrypted on read.
     */
    override fun getTokenExpiry(): Flow<Long?> = prefsFlow.map { prefs ->
        prefs[KEY_TOKEN_EXPIRY] as? Long
    }

    /**
     * Clears all stored authentication data (secure erasure).
     * The encrypted file is cleared, ensuring no sensitive data remains.
     */
    override suspend fun clearAuthData() {
        try {
            encryptedPrefs.edit()
                .clear()
                .apply()

            notifyChange()
            Timber.d("Auth data cleared successfully (securely erased)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear auth data")
            throw e
        }
    }

    /**
     * Updates only the access token and expiry (used during token refresh).
     * Data is encrypted before being written to disk.
     */
    override suspend fun updateAccessToken(accessToken: String, tokenExpiry: Long) {
        try {
            encryptedPrefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putLong(KEY_TOKEN_EXPIRY, tokenExpiry)
                .apply()

            notifyChange()
            Timber.d("Access token updated successfully (encrypted)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update access token")
            throw e
        }
    }

    /**
     * Updates only the refresh token (used during token rotation).
     * Data is encrypted before being written to disk.
     */
    override suspend fun updateRefreshToken(refreshToken: String) {
        try {
            encryptedPrefs.edit()
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply()

            notifyChange()
            Timber.d("Refresh token updated successfully (encrypted)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update refresh token")
            throw e
        }
    }
}
