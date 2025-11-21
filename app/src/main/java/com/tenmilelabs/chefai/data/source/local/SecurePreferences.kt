package com.tenmilelabs.chefai.data.source.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages secure storage of authentication data using EncryptedFile for DataStore.
 * Uses Android's Security Crypto library to encrypt sensitive data at rest.
 */
@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) : SecurePreferencesInterface {
    companion object {
        private const val SECURE_PREFS_FILE_NAME = "secure_prefs.preferences_pb"

        // Preference keys
        private val KEY_USER_UUID = stringPreferencesKey("user_uuid")
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_TOKEN_EXPIRY = longPreferencesKey("token_expiry")
    }

    // Create MasterKey for encryption
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // Create encrypted file for DataStore
    private val encryptedFile = EncryptedFile.Builder(
        context,
        File(context.filesDir, SECURE_PREFS_FILE_NAME),
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    // DataStore instance using encrypted file
    private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(
        name = SECURE_PREFS_FILE_NAME
    )

    private val dataStore = context.secureDataStore

    /**
     * Stores user authentication data securely.
     */
    override suspend fun saveAuthData(
        userUuid: UUID,
        accessToken: String,
        refreshToken: String,
        tokenExpiry: Long
    ) {
        try {
            dataStore.edit { preferences ->
                preferences[KEY_USER_UUID] = userUuid.toString()
                preferences[KEY_ACCESS_TOKEN] = accessToken
                preferences[KEY_REFRESH_TOKEN] = refreshToken
                preferences[KEY_TOKEN_EXPIRY] = tokenExpiry
            }
            Timber.d("Auth data saved successfully for user: $userUuid")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save auth data")
            throw e
        }
    }

    /**
     * Retrieves the stored user UUID.
     */
    override fun getUserUuid(): Flow<UUID?> = dataStore.data.map { preferences ->
        preferences[KEY_USER_UUID]?.let { uuidString ->
            try {
                UUID.fromString(uuidString)
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Invalid UUID format in preferences")
                null
            }
        }
    }

    /**
     * Retrieves the stored access token.
     */
    override fun getAccessToken(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_ACCESS_TOKEN]
    }

    /**
     * Retrieves the stored refresh token.
     */
    override fun getRefreshToken(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_REFRESH_TOKEN]
    }

    /**
     * Retrieves the token expiry timestamp.
     */
    override fun getTokenExpiry(): Flow<Long?> = dataStore.data.map { preferences ->
        preferences[KEY_TOKEN_EXPIRY]
    }

    /**
     * Clears all stored authentication data.
     */
    override suspend fun clearAuthData() {
        try {
            dataStore.edit { preferences ->
                preferences.clear()
            }
            Timber.d("Auth data cleared successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear auth data")
            throw e
        }
    }

    /**
     * Updates only the access token and expiry (used during token refresh).
     */
    override suspend fun updateAccessToken(accessToken: String, tokenExpiry: Long) {
        try {
            dataStore.edit { preferences ->
                preferences[KEY_ACCESS_TOKEN] = accessToken
                preferences[KEY_TOKEN_EXPIRY] = tokenExpiry
            }
            Timber.d("Access token updated successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update access token")
            throw e
        }
    }
}
