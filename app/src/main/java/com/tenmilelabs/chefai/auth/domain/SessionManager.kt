package com.tenmilelabs.chefai.auth.domain

import com.tenmilelabs.chefai.auth.data.local.SecurePreferencesInterface
import com.tenmilelabs.chefai.auth.data.mapper.toAuthToken
import com.tenmilelabs.chefai.auth.data.mapper.toUser
import com.tenmilelabs.chefai.auth.data.network.AuthNetworkDataSource
import com.tenmilelabs.chefai.auth.data.network.dto.LoginRequest
import com.tenmilelabs.chefai.auth.data.network.dto.RefreshTokenRequest
import com.tenmilelabs.chefai.auth.data.network.dto.RegisterRequest
import com.tenmilelabs.chefai.auth.domain.model.AuthToken
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.auth.domain.usecase.AccountUpgradeUseCase
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.UserDao
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.local.util.generateUuid7
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.core.di.ApplicationScope
import com.tenmilelabs.chefai.core.domain.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Manages user session state across the application.
 * Implements an anonymous-first model: users always have a session context
 * (either Anonymous or Authenticated). There is no "unauthenticated" state.
 */
@Singleton
class SessionManager @Inject constructor(
    private val securePreferences: SecurePreferencesInterface,
    private val authNetworkDataSource: Provider<AuthNetworkDataSource>,
    private val userDao: UserDao,
    private val accountUpgradeUseCaseProvider: Provider<AccountUpgradeUseCase>,
    private val syncSchedulerProvider: Provider<SyncScheduler>,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) : TokenProvider {

    /** Override in tests to avoid the UUIDv7 library dependency. */
    internal var uuidGenerator: () -> UUID = { generateUuid7() }

    private val _userSession = MutableStateFlow<UserSession>(UserSession.Loading)
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    init {
        // Load session on initialization
        applicationScope.launch {
            loadSession()
        }
    }

    /**
     * Loads the user session from secure storage.
     * Always resolves to either Authenticated or Anonymous — never leaves the user without a context.
     *
     * Flow:
     * 1. If valid auth tokens exist → Authenticated
     * 2. If expired tokens exist → try refresh → Authenticated or fall back to Anonymous
     * 3. If no tokens → restore or create Anonymous session
     */
    suspend fun loadSession() {
        try {
            Timber.d("Loading user session...")
            _userSession.value = UserSession.Loading

            val userUuid = securePreferences.getUserUuid().first()
            val accessToken = securePreferences.getAccessToken().first()
            val refreshToken = securePreferences.getRefreshToken().first()
            val tokenExpiry = securePreferences.getTokenExpiry().first()

            // If we have stored credentials, validate them
            if (userUuid != null && accessToken != null && refreshToken != null && tokenExpiry != null) {
                val authToken = AuthToken(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = tokenExpiry
                )

                // Restore user from storage first so refreshToken() can
                // read the current refresh token from the session state.
                val displayName = securePreferences.getDisplayName().first() ?: ""
                val email = securePreferences.getUserEmail().first() ?: ""
                val avatarUrl = securePreferences.getUserAvatarUrl().first() ?: ""
                val user = User(
                    uuid = userUuid,
                    displayName = displayName,
                    email = email,
                    avatarUrl = avatarUrl
                )

                _userSession.value = UserSession.Authenticated(
                    user = user,
                    authToken = authToken
                )

                // Check if token is expired
                val currentTime = System.currentTimeMillis()
                if (currentTime >= tokenExpiry) {
                    Timber.d("Access token expired, attempting refresh...")
                    val refreshResult = refreshToken()
                    if (refreshResult.isSuccess) {
                        Timber.d("Session refreshed successfully")
                        syncSchedulerProvider.get().schedulePeriodicSync()
                    } else {
                        Timber.w("Failed to refresh session, falling back to anonymous")
                        enterAnonymousSession()
                    }
                } else {
                    Timber.d("Session loaded successfully for user: ${user.uuid}")
                    // Resume periodic sync for restored authenticated session
                    syncSchedulerProvider.get().schedulePeriodicSync()
                }
            } else {
                // No stored auth session — enter anonymous mode
                Timber.d("No stored session found, entering anonymous mode")
                enterAnonymousSession()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load session, falling back to anonymous")
            enterAnonymousSession()
        }
    }

    /**
     * Creates or restores an anonymous session.
     * Reuses an existing localUserId if one was previously stored, otherwise generates a new UUIDv7.
     * Also ensures a corresponding UserEntity exists in Room.
     */
    private suspend fun enterAnonymousSession() {
        val existingLocalId = securePreferences.getLocalUserId().first()
        val localUserId = if (existingLocalId != null) {
            Timber.d("Restoring anonymous session with existing localUserId: $existingLocalId")
            existingLocalId
        } else {
            val newId = uuidGenerator()
            Timber.d("Creating new anonymous session with localUserId: $newId")
            securePreferences.saveLocalUserId(newId)
            newId
        }

        // Ensure the anonymous UserEntity exists in Room
        ensureAnonymousUserEntity(localUserId)

        _userSession.value = UserSession.Anonymous(localUserId)
    }

    /**
     * Creates a UserEntity in Room for the anonymous user if it doesn't already exist.
     */
    private suspend fun ensureAnonymousUserEntity(localUserId: UUID) {
        val existingUser = userDao.getUserById(localUserId)
        if (existingUser == null) {
            userDao.upsertUser(
                UserEntity(
                    uuid = localUserId,
                    displayName = "Guest",
                    email = "",
                    avatarUrl = "",
                    updatedAt = System.currentTimeMillis(),
                    deletedAt = null,
                    syncState = SyncState.PENDING
                )
            )
            Timber.d("Created anonymous UserEntity in Room: $localUserId")
        }
    }

    /**
     * Logs in a user with credentials using the backend API.
     * If the user was in an anonymous session with local recipes, those recipes are
     * reassigned to the authenticated user via [AccountUpgradeUseCase].
     */
    suspend fun login(email: String, password: String): Result<User> {
        return try {
            Timber.d("Login attempt for: $email")

            // Capture the current anonymous userId BEFORE changing session state
            val previousSession = _userSession.value
            val anonymousUserId = (previousSession as? UserSession.Anonymous)?.localUserId

            _userSession.value = UserSession.Loading

            // Make API call to backend for authentication
            val response = authNetworkDataSource.get().login(
                LoginRequest(email = email, password = password)
            )

            val authToken = response.toAuthToken()
            val user = response.toUser()

            // Run the anonymous → authenticated data upgrade if we had anonymous data
            if (anonymousUserId != null) {
                try {
                    val upgraded = accountUpgradeUseCaseProvider.get()
                        .execute(anonymousUserId, user)
                    Timber.d("Upgraded $upgraded anonymous recipes to authenticated user")
                } catch (e: Exception) {
                    Timber.e(e, "Account upgrade failed, but login succeeded. Recipes may not be transferred.")
                }
            }

            // Save to secure storage
            securePreferences.saveAuthData(
                userUuid = user.uuid,
                displayName = user.displayName,
                email = user.email,
                avatarUrl = user.avatarUrl,
                accessToken = authToken.accessToken,
                refreshToken = authToken.refreshToken,
                tokenExpiry = authToken.expiresAt
            )

            _userSession.value = UserSession.Authenticated(
                user = user,
                authToken = authToken
            )

            // Trigger sync: push any local recipes and start periodic sync
            syncSchedulerProvider.get().requestImmediateSync()
            syncSchedulerProvider.get().schedulePeriodicSync()

            Timber.d("Login successful for user: ${user.uuid}")
            Result.success(user)
        } catch (e: Exception) {
            Timber.e(e, "Login failed")
            enterAnonymousSession()
            Result.failure(e)
        }
    }

    /**
     * Registers a new user with the backend API.
     * If the user was in an anonymous session with local recipes, those recipes are
     * reassigned to the authenticated user via [AccountUpgradeUseCase].
     */
    suspend fun register(username: String, email: String, password: String): Result<User> {
        return try {
            Timber.d("Register attempt for: $email")

            // Capture the current anonymous userId BEFORE changing session state
            val previousSession = _userSession.value
            val anonymousUserId = (previousSession as? UserSession.Anonymous)?.localUserId

            _userSession.value = UserSession.Loading

            // Make API call to backend for registration
            val response = authNetworkDataSource.get().register(
                RegisterRequest(email = email, username = username, password = password)
            )

            val authToken = response.toAuthToken()
            val responseUser = response.toUser()
            // Use the username from the registration form if the backend doesn't return it
            val user = if (responseUser.displayName.isBlank()) {
                responseUser.copy(displayName = username)
            } else {
                responseUser
            }

            // Run the anonymous → authenticated data upgrade if we had anonymous data
            if (anonymousUserId != null) {
                try {
                    val upgraded = accountUpgradeUseCaseProvider.get()
                        .execute(anonymousUserId, user)
                    Timber.d("Upgraded $upgraded anonymous recipes to authenticated user")
                } catch (e: Exception) {
                    Timber.e(e, "Account upgrade failed, but registration succeeded.")
                }
            }

            // Save to secure storage
            securePreferences.saveAuthData(
                userUuid = user.uuid,
                displayName = user.displayName,
                email = user.email,
                avatarUrl = user.avatarUrl,
                accessToken = authToken.accessToken,
                refreshToken = authToken.refreshToken,
                tokenExpiry = authToken.expiresAt
            )

            _userSession.value = UserSession.Authenticated(
                user = user,
                authToken = authToken
            )

            // Trigger sync: push any local recipes and start periodic sync
            syncSchedulerProvider.get().requestImmediateSync()
            syncSchedulerProvider.get().schedulePeriodicSync()

            Timber.d("Registration successful for user: ${user.uuid}")
            Result.success(user)
        } catch (e: Exception) {
            Timber.e(e, "Registration failed")
            enterAnonymousSession()
            Result.failure(e)
        }
    }

    /**
     * Logs out the current user and returns to an anonymous session.
     * Reuses the existing anonymous localUserId from SecurePreferences so that
     * data continuity is maintained on the device (RFC-001 Section 7.3).
     */
    suspend fun logout() {
        try {
            val currentUser = getCurrentUser()
            Timber.d("Logging out user: ${currentUser?.uuid}")

            // Cancel all sync work before clearing auth data
            syncSchedulerProvider.get().cancelAllSync()

            // Clear auth data (preserves localUserId)
            securePreferences.clearAuthData()

            // Return to anonymous state, reusing existing localUserId
            enterAnonymousSession()

            Timber.d("Logout successful, now in anonymous mode")
        } catch (e: Exception) {
            Timber.e(e, "Error during logout")
            // Still try to enter anonymous mode even if clear fails
            try {
                enterAnonymousSession()
            } catch (inner: Exception) {
                Timber.e(inner, "Failed to enter anonymous session after logout error")
                // Last resort: create anonymous session without Room
                val fallbackId = uuidGenerator()
                _userSession.value = UserSession.Anonymous(fallbackId)
            }
        }
    }

    /**
     * Refreshes the access token using the refresh token.
     */
    suspend fun refreshToken(): Result<Unit> {
        return try {
            val currentSession = _userSession.value
            if (currentSession !is UserSession.Authenticated) {
                return Result.failure(IllegalStateException("No active session to refresh"))
            }

            Timber.d("Refreshing access token...")

            // Make API call to backend to refresh token
            val response = authNetworkDataSource.get().refreshToken(
                RefreshTokenRequest(refreshToken = currentSession.authToken.refreshToken)
            )

            val newAuthToken = response.toAuthToken()

            // Update secure storage with new tokens
            securePreferences.updateAccessToken(
                accessToken = newAuthToken.accessToken,
                tokenExpiry = newAuthToken.expiresAt
            )

            // Note: In token rotation, we also need to update the refresh token
            securePreferences.updateRefreshToken(
                refreshToken = newAuthToken.refreshToken
            )

            // Update session state
            _userSession.value = UserSession.Authenticated(
                user = currentSession.user,
                authToken = newAuthToken
            )

            Timber.d("Token refreshed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Token refresh failed")
            Result.failure(e)
        }
    }

    /**
     * Gets the current user ID regardless of session type.
     * Returns the localUserId for Anonymous sessions and the user UUID for Authenticated sessions.
     */
    fun getCurrentUserId(): UUID? {
        return when (val session = _userSession.value) {
            is UserSession.Anonymous -> session.localUserId
            is UserSession.Authenticated -> session.user.uuid
            is UserSession.Loading -> null
        }
    }

    /**
     * Gets the current authenticated user, or null if anonymous/loading.
     */
    fun getCurrentUser(): User? {
        return when (val session = _userSession.value) {
            is UserSession.Authenticated -> session.user
            else -> null
        }
    }

    /**
     * Gets the current access token, or null if not authenticated.
     */
    override fun getAccessToken(): String? {
        return when (val session = _userSession.value) {
            is UserSession.Authenticated -> session.authToken.accessToken
            else -> null
        }
    }

    /**
     * Checks if the current access token is expired or about to expire.
     * @param bufferMillis Buffer time in milliseconds before actual expiry (default 5 minutes)
     */
    fun isTokenExpired(bufferMillis: Long = 5 * 60 * 1000): Boolean {
        val session = _userSession.value
        if (session !is UserSession.Authenticated) return true

        val currentTime = System.currentTimeMillis()
        return currentTime >= (session.authToken.expiresAt - bufferMillis)
    }
}
