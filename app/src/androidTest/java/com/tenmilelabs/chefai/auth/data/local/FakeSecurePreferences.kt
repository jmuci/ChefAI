package com.tenmilelabs.chefai.auth.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Fake implementation of SecurePreferences for instrumented tests.
 * Stores data in memory instead of encrypted storage.
 */
class FakeSecurePreferences : SecurePreferencesInterface {

    private val storage = MutableStateFlow<Map<String, Any?>>(emptyMap())

    companion object {
        private const val KEY_USER_UUID = "user_uuid"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_LOCAL_USER_ID = "local_user_id"
        private const val KEY_CURRENT_USER_ID = "current_user_id"
        private const val KEY_LAST_LOGIN_EMAIL = "last_login_email"
    }

    override suspend fun saveAuthData(
        userUuid: UUID,
        displayName: String,
        email: String,
        avatarUrl: String,
        accessToken: String,
        refreshToken: String,
        tokenExpiry: Long
    ) {
        storage.value = storage.value.toMutableMap().apply {
            put(KEY_USER_UUID, userUuid.toString())
            put(KEY_DISPLAY_NAME, displayName)
            put(KEY_EMAIL, email)
            put(KEY_AVATAR_URL, avatarUrl)
            put(KEY_ACCESS_TOKEN, accessToken)
            put(KEY_REFRESH_TOKEN, refreshToken)
            put(KEY_TOKEN_EXPIRY, tokenExpiry)
            // KEY_LOCAL_USER_ID is preserved (not replaced)
        }
    }

    override fun getUserUuid(): Flow<UUID?> = storage.map { prefs ->
        prefs[KEY_USER_UUID]?.let { uuidString ->
            try {
                UUID.fromString(uuidString as String)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    override fun getDisplayName(): Flow<String?> = storage.map { prefs ->
        prefs[KEY_DISPLAY_NAME] as? String
    }

    override fun getUserEmail(): Flow<String?> = storage.map { prefs ->
        prefs[KEY_EMAIL] as? String
    }

    override fun getUserAvatarUrl(): Flow<String?> = storage.map { prefs ->
        prefs[KEY_AVATAR_URL] as? String
    }

    override fun getAccessToken(): Flow<String?> = storage.map { prefs ->
        prefs[KEY_ACCESS_TOKEN] as? String
    }

    override fun getRefreshToken(): Flow<String?> = storage.map { prefs ->
        prefs[KEY_REFRESH_TOKEN] as? String
    }

    override fun getTokenExpiry(): Flow<Long?> = storage.map { prefs ->
        prefs[KEY_TOKEN_EXPIRY] as? Long
    }

    override suspend fun clearAuthData() {
        storage.value = storage.value.toMutableMap().apply {
            remove(KEY_USER_UUID)
            remove(KEY_DISPLAY_NAME)
            remove(KEY_EMAIL)
            remove(KEY_AVATAR_URL)
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_TOKEN_EXPIRY)
            // KEY_LOCAL_USER_ID is intentionally preserved
        }
    }

    override suspend fun updateAccessToken(accessToken: String, tokenExpiry: Long) {
        storage.value = storage.value.toMutableMap().apply {
            put(KEY_ACCESS_TOKEN, accessToken)
            put(KEY_TOKEN_EXPIRY, tokenExpiry)
        }
    }

    override suspend fun updateRefreshToken(refreshToken: String) {
        storage.value = storage.value.toMutableMap().apply {
            put(KEY_REFRESH_TOKEN, refreshToken)
        }
    }

    override suspend fun saveLocalUserId(uuid: UUID) {
        storage.value = storage.value.toMutableMap().apply {
            put(KEY_LOCAL_USER_ID, uuid.toString())
        }
    }

    override fun getLocalUserId(): Flow<UUID?> = storage.map { prefs ->
        prefs[KEY_LOCAL_USER_ID]?.let { uuidString ->
            try {
                UUID.fromString(uuidString as String)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    override suspend fun clearLocalUserId() {
        storage.value = storage.value.toMutableMap().apply {
            remove(KEY_LOCAL_USER_ID)
        }
    }

    override suspend fun setCurrentUserId(userId: UUID) {
        storage.value = storage.value.toMutableMap().apply {
            put(KEY_CURRENT_USER_ID, userId.toString())
        }
    }

    override fun getStoredCurrentUserId(): Flow<UUID?> = storage.map { prefs ->
        prefs[KEY_CURRENT_USER_ID]?.let { uuidString ->
            try {
                UUID.fromString(uuidString as String)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    override suspend fun saveLastLoginEmail(email: String) {
        storage.value = storage.value.toMutableMap().apply {
            put(KEY_LAST_LOGIN_EMAIL, email)
        }
    }

    override fun getLastLoginEmail(): Flow<String?> = storage.map { prefs ->
        prefs[KEY_LAST_LOGIN_EMAIL] as? String
    }
}
