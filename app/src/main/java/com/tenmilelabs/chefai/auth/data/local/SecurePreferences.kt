package com.tenmilelabs.chefai.auth.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "chefai_secure_prefs"
)

/**
 * Manages secure storage of authentication data using Jetpack DataStore
 * with AES-256/GCM encryption backed by the Android Keystore.
 *
 * Each value is individually encrypted with a fresh IV before storage.
 * The encryption key is hardware-backed when available via Android Keystore.
 */
@Singleton
class SecurePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SecurePreferencesInterface {

    companion object {
        private const val KEY_ALIAS = "chefai_master_key"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128

        private val KEY_USER_UUID = stringPreferencesKey("user_uuid")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_EMAIL = stringPreferencesKey("email")
        private val KEY_AVATAR_URL = stringPreferencesKey("avatar_url")
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_TOKEN_EXPIRY = stringPreferencesKey("token_expiry")
        private val KEY_LOCAL_USER_ID = stringPreferencesKey("local_user_id")
        private val KEY_CURRENT_USER_ID = stringPreferencesKey("current_user_id")
    }

    private val dataStore: DataStore<Preferences> = context.dataStore

    private val secretKey: SecretKey by lazy { getOrCreateKey() }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generateKey()
            }
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    /**
     * Encrypts [plaintext] using AES-256/GCM. The 12-byte random IV is
     * prepended to the ciphertext and the whole blob is Base64-encoded.
     */
    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    /**
     * Decrypts a Base64-encoded blob that was produced by [encrypt].
     */
    private fun decrypt(encoded: String): String {
        val decoded = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = decoded.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = decoded.copyOfRange(GCM_IV_LENGTH, decoded.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /**
     * Stores user authentication data securely. Each field is individually
     * encrypted with AES-256/GCM before being written to DataStore.
     */
    override suspend fun saveAuthData(
        userUuid: UUID,
        displayName: String,
        email: String,
        avatarUrl: String,
        accessToken: String,
        refreshToken: String,
        tokenExpiry: Long
    ) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_USER_UUID] = encrypt(userUuid.toString())
                prefs[KEY_DISPLAY_NAME] = encrypt(displayName)
                prefs[KEY_EMAIL] = encrypt(email)
                prefs[KEY_AVATAR_URL] = encrypt(avatarUrl)
                prefs[KEY_ACCESS_TOKEN] = encrypt(accessToken)
                prefs[KEY_REFRESH_TOKEN] = encrypt(refreshToken)
                prefs[KEY_TOKEN_EXPIRY] = encrypt(tokenExpiry.toString())
            }
            Timber.d("Auth data saved securely (AES256-GCM encrypted) for user: $userUuid")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save auth data")
            throw e
        }
    }

    override fun getUserUuid(): Flow<UUID?> = dataStore.data.map { prefs ->
        prefs[KEY_USER_UUID]?.let { encrypted ->
            try {
                UUID.fromString(decrypt(encrypted))
            } catch (e: Exception) {
                Timber.e(e, "Failed to read user UUID from preferences")
                null
            }
        }
    }

    override fun getDisplayName(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_DISPLAY_NAME]?.let { runCatching { decrypt(it) }.getOrNull() }
    }

    override fun getUserEmail(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_EMAIL]?.let { runCatching { decrypt(it) }.getOrNull() }
    }

    override fun getUserAvatarUrl(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_AVATAR_URL]?.let { runCatching { decrypt(it) }.getOrNull() }
    }

    override fun getAccessToken(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_ACCESS_TOKEN]?.let { runCatching { decrypt(it) }.getOrNull() }
    }

    override fun getRefreshToken(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_REFRESH_TOKEN]?.let { runCatching { decrypt(it) }.getOrNull() }
    }

    override fun getTokenExpiry(): Flow<Long?> = dataStore.data.map { prefs ->
        prefs[KEY_TOKEN_EXPIRY]?.let { runCatching { decrypt(it).toLong() }.getOrNull() }
    }

    /**
     * Clears all stored authentication data while preserving the anonymous localUserId.
     * The localUserId is intentionally kept so that logout → re-anonymous reuses the same UUID
     * (RFC-001 Section 7.3: "Restore the existing localUserId from SecurePreferences").
     */
    override suspend fun clearAuthData() {
        try {
            dataStore.edit { prefs ->
                prefs.remove(KEY_USER_UUID)
                prefs.remove(KEY_DISPLAY_NAME)
                prefs.remove(KEY_EMAIL)
                prefs.remove(KEY_AVATAR_URL)
                prefs.remove(KEY_ACCESS_TOKEN)
                prefs.remove(KEY_REFRESH_TOKEN)
                prefs.remove(KEY_TOKEN_EXPIRY)
                // KEY_LOCAL_USER_ID is intentionally preserved
            }
            Timber.d("Auth data cleared successfully (localUserId preserved)")
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
            dataStore.edit { prefs ->
                prefs[KEY_ACCESS_TOKEN] = encrypt(accessToken)
                prefs[KEY_TOKEN_EXPIRY] = encrypt(tokenExpiry.toString())
            }
            Timber.d("Access token updated successfully (encrypted)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update access token")
            throw e
        }
    }

    /**
     * Updates only the refresh token (used during token rotation).
     */
    override suspend fun updateRefreshToken(refreshToken: String) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_REFRESH_TOKEN] = encrypt(refreshToken)
            }
            Timber.d("Refresh token updated successfully (encrypted)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update refresh token")
            throw e
        }
    }

    override suspend fun saveLocalUserId(uuid: UUID) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_LOCAL_USER_ID] = encrypt(uuid.toString())
            }
            Timber.d("Local user ID saved securely")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save local user ID")
            throw e
        }
    }

    override fun getLocalUserId(): Flow<UUID?> = dataStore.data.map { prefs ->
        prefs[KEY_LOCAL_USER_ID]?.let { encrypted ->
            try {
                UUID.fromString(decrypt(encrypted))
            } catch (e: Exception) {
                Timber.e(e, "Failed to read local user ID from preferences")
                null
            }
        }
    }

    override suspend fun clearLocalUserId() {
        try {
            dataStore.edit { prefs ->
                prefs.remove(KEY_LOCAL_USER_ID)
            }
            Timber.d("Local user ID cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear local user ID")
            throw e
        }
    }

    override suspend fun setCurrentUserId(userId: UUID) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_CURRENT_USER_ID] = encrypt(userId.toString())
            }
            Timber.d("Current authenticated user ID saved securely")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save current authenticated user ID")
            throw e
        }
    }

    override fun getStoredCurrentUserId(): Flow<UUID?> = dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_USER_ID]?.let { encrypted ->
            try {
                UUID.fromString(decrypt(encrypted))
            } catch (e: Exception) {
                Timber.e(e, "Failed to read current authenticated user ID from preferences")
                null
            }
        }
    }
}
