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
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
    }

    override suspend fun saveAuthData(
        userUuid: UUID,
        accessToken: String,
        refreshToken: String,
        tokenExpiry: Long
    ) {
        storage.value = mapOf(
            KEY_USER_UUID to userUuid.toString(),
            KEY_ACCESS_TOKEN to accessToken,
            KEY_REFRESH_TOKEN to refreshToken,
            KEY_TOKEN_EXPIRY to tokenExpiry
        )
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
        storage.value = emptyMap()
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
}
