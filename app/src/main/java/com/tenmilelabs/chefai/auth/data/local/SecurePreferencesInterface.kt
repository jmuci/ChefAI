package com.tenmilelabs.chefai.auth.data.local

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Interface for secure storage of authentication data.
 * Allows for easy testing with fake implementations.
 */
interface SecurePreferencesInterface {
    /**
     * Stores the signed-in account in one write. [pendingUpgradeFrom] is the anonymous local user
     * id whose rows this login still has to re-parent (null when there are none); it is written in
     * the same edit as the tokens, so no process death can leave tokens without it.
     */
    suspend fun saveAuthData(
        userUuid: UUID,
        displayName: String,
        email: String,
        avatarUrl: String,
        accessToken: String,
        refreshToken: String,
        tokenExpiry: Long,
        pendingUpgradeFrom: UUID? = null,
    )

    /** The anonymous id an interrupted login still has to upgrade from, or null. See [saveAuthData]. */
    fun getPendingUpgradeFrom(): Flow<UUID?>

    /** Clears [getPendingUpgradeFrom] once the anonymous → account upgrade has committed. */
    suspend fun clearPendingUpgradeFrom()

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

    /**
     * Persists a household invite token an anonymous session previewed but can't act on, so the
     * sign-up round trip (which may kill the process) can resume the join afterward (ADR-014 §7).
     */
    suspend fun savePendingInviteToken(token: String)

    /** Reads the pending invite token, or null if none has been stored. */
    fun getPendingInviteToken(): Flow<String?>

    /** Clears the pending invite token once its join succeeds or is explicitly declined. */
    suspend fun clearPendingInviteToken()
}
