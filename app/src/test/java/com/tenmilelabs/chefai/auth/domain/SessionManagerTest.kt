package com.tenmilelabs.chefai.auth.domain

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.data.local.FakeSecurePreferences
import com.tenmilelabs.chefai.auth.data.network.FakeAuthNetworkDataSource
import com.tenmilelabs.chefai.auth.data.network.dto.AuthResponse
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.auth.domain.usecase.AccountUpgradeUseCase
import com.tenmilelabs.chefai.core.data.local.room.FakeTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeUserDao
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.FakeSyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID
import javax.inject.Provider

/**
 * Unit tests for SessionManager with anonymous-first session model.
 */
@ExperimentalCoroutinesApi
class SessionManagerTest {

    private lateinit var sessionManager: SessionManager
    private lateinit var fakeSecurePreferences: FakeSecurePreferences
    private lateinit var fakeAuthNetworkDataSource: FakeAuthNetworkDataSource
    private lateinit var fakeUserDao: FakeUserDao
    private lateinit var fakeRecipeDao: FakeRecipeDao
    private lateinit var fakeRecipeStepDao: FakeRecipeStepDao
    private lateinit var fakeRecipeIngredientDao: FakeRecipeIngredientDao
    private lateinit var fakeRecipeTagCrossRefDao: FakeRecipeTagCrossRefDao
    private lateinit var fakeRecipeLabelCrossRefDao: FakeRecipeLabelCrossRefDao
    private lateinit var accountUpgradeUseCaseProvider: Provider<AccountUpgradeUseCase>
    private lateinit var fakeSyncManager: FakeSyncManager
    private lateinit var testScope: TestScope
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        testScope = TestScope(testDispatcher)
        fakeSecurePreferences = FakeSecurePreferences()
        fakeAuthNetworkDataSource = FakeAuthNetworkDataSource()
        fakeUserDao = FakeUserDao()
        fakeRecipeDao = FakeRecipeDao()
        fakeRecipeStepDao = FakeRecipeStepDao()
        fakeRecipeIngredientDao = FakeRecipeIngredientDao()
        fakeRecipeTagCrossRefDao = FakeRecipeTagCrossRefDao()
        fakeRecipeLabelCrossRefDao = FakeRecipeLabelCrossRefDao()

        accountUpgradeUseCaseProvider = Provider {
            AccountUpgradeUseCase(
                transactionRunner = FakeTransactionRunner(),
                userDao = fakeUserDao,
                recipeDao = fakeRecipeDao,
                recipeStepDao = fakeRecipeStepDao,
                recipeIngredientDao = fakeRecipeIngredientDao,
                recipeTagCrossRefDao = fakeRecipeTagCrossRefDao,
                recipeLabelCrossRefDao = fakeRecipeLabelCrossRefDao
            )
        }

        fakeSyncManager = FakeSyncManager()

        // Create SessionManager with test scope
        sessionManager = SessionManager(
            securePreferences = fakeSecurePreferences,
            authNetworkDataSource = { fakeAuthNetworkDataSource },
            userDao = fakeUserDao,
            accountUpgradeUseCaseProvider = accountUpgradeUseCaseProvider,
            syncSchedulerProvider = { fakeSyncManager },
            applicationScope = testScope
        ).apply {
            uuidGenerator = { UUID.randomUUID() }
        }
    }

    @Test
    fun `initial state is anonymous when no stored data`() = testScope.runTest {
        // SessionManager automatically loads session in init block
        val initialState = sessionManager.userSession.value
        assertThat(initialState).isInstanceOf(UserSession.Anonymous::class.java)
    }

    @Test
    fun `load session returns anonymous when no stored data`() = testScope.runTest {
        // Given: No stored auth data
        fakeSecurePreferences.clearAuthData()

        // When: Loading session
        sessionManager.loadSession()

        // Then: Session is anonymous
        val session = sessionManager.userSession.first()
        assertThat(session).isInstanceOf(UserSession.Anonymous::class.java)
    }

    @Test
    fun `anonymous session creates UserEntity in Room`() = testScope.runTest {
        // Given: No stored data
        fakeSecurePreferences.clearAuthData()

        // When: Loading session (enters anonymous mode)
        sessionManager.loadSession()

        // Then: An anonymous UserEntity exists in Room
        val session = sessionManager.userSession.first() as UserSession.Anonymous
        val userEntity = fakeUserDao.getUserById(session.localUserId)
        assertThat(userEntity).isNotNull()
        assertThat(userEntity!!.displayName).isEqualTo("Guest")
        assertThat(userEntity.email).isEmpty()
    }

    @Test
    fun `anonymous session reuses existing localUserId`() = testScope.runTest {
        // Given: A localUserId already stored in preferences
        sessionManager.loadSession()
        val firstSession = sessionManager.userSession.first() as UserSession.Anonymous
        val firstLocalUserId = firstSession.localUserId

        // When: Loading session again (simulating app restart)
        sessionManager.loadSession()

        // Then: Same localUserId is reused
        val secondSession = sessionManager.userSession.first() as UserSession.Anonymous
        assertThat(secondSession.localUserId).isEqualTo(firstLocalUserId)
    }

    @Test
    fun `login with valid credentials returns user and saves session`() = testScope.runTest {
        // Given: Valid credentials
        val email = "test@example.com"
        val password = "password123"

        // When: Logging in
        val result = sessionManager.login(email, password)
        // Then: Login succeeds and session is authenticated
        assertThat(result.isSuccess).isTrue()
        val user = result.getOrNull()
        assertThat(user).isNotNull()
        assertThat(user?.email).isEqualTo(email)

        val session = sessionManager.userSession.first()
        assertThat(session).isInstanceOf(UserSession.Authenticated::class.java)
    }

    @Test
    fun `login with network error returns failure and falls back to anonymous`() = testScope.runTest {
        // Given: Network error is simulated
        fakeAuthNetworkDataSource.shouldThrowError = true

        // When: Logging in
        val result = sessionManager.login("test@example.com", "password123")
        // Then: Login fails and session falls back to anonymous
        assertThat(result.isFailure).isTrue()

        val session = sessionManager.userSession.first()
        assertThat(session).isInstanceOf(UserSession.Anonymous::class.java)
    }

    @Test
    fun `register with valid credentials creates user and session`() = testScope.runTest {
        // Given: Valid registration data
        val username = "testuser"
        val email = "test@example.com"
        val password = "password123"

        // When: Registering
        val result = sessionManager.register(username, email, password)
        // Then: Registration succeeds and session is authenticated
        assertThat(result.isSuccess).isTrue()
        val user = result.getOrNull()
        assertThat(user).isNotNull()
        assertThat(user?.displayName).isEqualTo(username)
        assertThat(user?.email).isEqualTo(email)

        val session = sessionManager.userSession.first()
        assertThat(session).isInstanceOf(UserSession.Authenticated::class.java)
    }

    @Test
    fun `register with network error returns failure and falls back to anonymous`() = testScope.runTest {
        // Given: Network error is simulated
        fakeAuthNetworkDataSource.shouldThrowError = true

        // When: Registering
        val result = sessionManager.register("testuser", "test@example.com", "password123")
        // Then: Registration fails and session falls back to anonymous
        assertThat(result.isFailure).isTrue()

        val session = sessionManager.userSession.first()
        assertThat(session).isInstanceOf(UserSession.Anonymous::class.java)
    }

    @Test
    fun `logout returns to anonymous session`() = testScope.runTest {
        // Given: User is logged in
        sessionManager.login("test@example.com", "password123")
        // When: User logs out
        sessionManager.logout()

        // Then: Session is anonymous and auth storage is cleared
        val session = sessionManager.userSession.first()
        assertThat(session).isInstanceOf(UserSession.Anonymous::class.java)
        assertThat(fakeSecurePreferences.getUserUuid().first()).isNull()
    }

    @Test
    fun `getCurrentUserId returns localUserId when anonymous`() = testScope.runTest {
        // Given: Anonymous session
        sessionManager.loadSession()

        // When: Getting current user ID
        val userId = sessionManager.getCurrentUserId()

        // Then: Returns the anonymous localUserId
        assertThat(userId).isNotNull()
        val session = sessionManager.userSession.first() as UserSession.Anonymous
        assertThat(userId).isEqualTo(session.localUserId)
    }

    @Test
    fun `getCurrentUserId returns user uuid when authenticated`() = testScope.runTest {
        // Given: Authenticated session
        sessionManager.login("test@example.com", "password123")

        // When: Getting current user ID
        val userId = sessionManager.getCurrentUserId()

        // Then: Returns the authenticated user's UUID
        assertThat(userId).isNotNull()
        val session = sessionManager.userSession.first() as UserSession.Authenticated
        assertThat(userId).isEqualTo(session.user.uuid)
    }

    @Test
    fun `get current user returns null when anonymous`() = testScope.runTest {
        // Given: Anonymous session
        sessionManager.loadSession()

        // When: Getting current user
        val user = sessionManager.getCurrentUser()

        // Then: Returns null (anonymous has no full User profile)
        assertThat(user).isNull()
    }

    @Test
    fun `get current user returns user when authenticated`() = testScope.runTest {
        // Given: User is logged in
        sessionManager.login("test@example.com", "password123")
        // When: Getting current user
        val user = sessionManager.getCurrentUser()

        // Then: Returns user from FakeAuthNetworkDataSource defaults
        assertThat(user).isNotNull()
        assertThat(user?.email).isEqualTo("test@example.com")
    }

    @Test
    fun `get access token returns null when anonymous`() = testScope.runTest {
        // Given: Anonymous session
        sessionManager.loadSession()

        // When: Getting access token
        val token = sessionManager.getAccessToken()

        // Then: Returns null
        assertThat(token).isNull()
    }

    @Test
    fun `get access token returns token when authenticated`() = testScope.runTest {
        // Given: User is logged in
        sessionManager.login("test@example.com", "password123")
        // When: Getting access token
        val token = sessionManager.getAccessToken()

        // Then: Returns token from FakeAuthNetworkDataSource
        assertThat(token).isNotNull()
        assertThat(token).startsWith("fake_access_token_")
    }

    @Test
    fun `is token expired returns true when anonymous`() = testScope.runTest {
        // Given: Anonymous session
        sessionManager.loadSession()

        // When: Checking if token is expired
        val isExpired = sessionManager.isTokenExpired()

        // Then: Returns true (no token)
        assertThat(isExpired).isTrue()
    }

    @Test
    fun `is token expired returns false for valid token`() = testScope.runTest {
        // Given: User is logged in with fresh token
        sessionManager.login("test@example.com", "password123")
        // When: Checking if token is expired (with buffer)
        val isExpired = sessionManager.isTokenExpired(bufferMillis = 5 * 60 * 1000)

        // Then: Returns false (fake token expires in 1 hour)
        assertThat(isExpired).isFalse()
    }

    @Test
    fun `load session restores session from storage`() = testScope.runTest {
        // Given: User is logged in and session is saved
        sessionManager.login("restored@example.com", "password123")
        val originalSession = sessionManager.userSession.value as UserSession.Authenticated

        // When: Creating new SessionManager that loads from storage
        val newSessionManager = SessionManager(
            securePreferences = fakeSecurePreferences,
            authNetworkDataSource = Provider { fakeAuthNetworkDataSource },
            userDao = fakeUserDao,
            accountUpgradeUseCaseProvider = accountUpgradeUseCaseProvider,
            syncSchedulerProvider = { FakeSyncManager() },
            applicationScope = testScope
        ).apply { uuidGenerator = { UUID.randomUUID() } }
        advanceUntilIdle()

        // Then: Session is restored from storage
        val restoredSession = newSessionManager.userSession.value
        assertThat(restoredSession).isInstanceOf(UserSession.Authenticated::class.java)
        val authenticated = restoredSession as UserSession.Authenticated
        assertThat(authenticated.user.uuid).isEqualTo(originalSession.user.uuid)
    }

    @Test
    fun `load session restores user details from storage`() = testScope.runTest {
        // Given: User registers so full profile is saved to storage
        val username = "Jane"
        val email = "jane@example.com"
        sessionManager.register(username, email, "password123")
        val originalSession = sessionManager.userSession.value as UserSession.Authenticated

        // When: A new SessionManager instance loads the persisted session
        val newSessionManager = SessionManager(
            securePreferences = fakeSecurePreferences,
            authNetworkDataSource = Provider { fakeAuthNetworkDataSource },
            userDao = fakeUserDao,
            accountUpgradeUseCaseProvider = accountUpgradeUseCaseProvider,
            syncSchedulerProvider = { FakeSyncManager() },
            applicationScope = testScope
        ).apply { uuidGenerator = { UUID.randomUUID() } }
        advanceUntilIdle()

        // Then: displayName and email are restored from storage instead of fallback placeholders
        val restoredSession = newSessionManager.userSession.value
        assertThat(restoredSession).isInstanceOf(UserSession.Authenticated::class.java)
        val authenticated = restoredSession as UserSession.Authenticated
        assertThat(authenticated.user.uuid).isEqualTo(originalSession.user.uuid)
        assertThat(authenticated.user.displayName).isEqualTo(originalSession.user.displayName)
        assertThat(authenticated.user.email).isEqualTo(originalSession.user.email)
    }

    @Test
    fun `login persists user display name and email`() = testScope.runTest {
        // Given / When: User logs in
        val email = "alice@example.com"
        sessionManager.login(email, "password123")

        // Then: displayName derived from email prefix is stored and accessible
        val session = sessionManager.userSession.value as UserSession.Authenticated
        assertThat(session.user.email).isEqualTo(email)
        // FakeAuthNetworkDataSource derives username from email prefix
        assertThat(session.user.displayName).isEqualTo(email.substringBefore("@"))

        // And: details survive a new SessionManager loading from the same storage
        val newSessionManager = SessionManager(
            securePreferences = fakeSecurePreferences,
            authNetworkDataSource = Provider { fakeAuthNetworkDataSource },
            userDao = fakeUserDao,
            accountUpgradeUseCaseProvider = accountUpgradeUseCaseProvider,
            syncSchedulerProvider = { FakeSyncManager() },
            applicationScope = testScope
        ).apply { uuidGenerator = { UUID.randomUUID() } }
        advanceUntilIdle()
        val restoredSession = newSessionManager.userSession.value as UserSession.Authenticated
        assertThat(restoredSession.user.displayName).isEqualTo(session.user.displayName)
        assertThat(restoredSession.user.email).isEqualTo(session.user.email)
    }

    @Test
    fun `logout reuses same anonymous UUID from previous session`() = testScope.runTest {
        // Given: Anonymous session
        sessionManager.loadSession()
        val firstSession = sessionManager.userSession.first() as UserSession.Anonymous
        val firstLocalUserId = firstSession.localUserId

        // When: User logs in and then logs out
        sessionManager.login("test@example.com", "password123")
        sessionManager.logout()

        // Then: Anonymous session reuses the same localUserId (RFC-001 Section 7.3)
        val newSession = sessionManager.userSession.first() as UserSession.Anonymous
        assertThat(newSession.localUserId).isEqualTo(firstLocalUserId)
    }

    @Test
    fun `anonymous session creates UserEntity with PENDING syncState`() = testScope.runTest {
        // Given: No stored data
        fakeSecurePreferences.clearAuthData()

        // When: Loading session (enters anonymous mode)
        sessionManager.loadSession()

        // Then: The anonymous UserEntity has PENDING syncState
        val session = sessionManager.userSession.first() as UserSession.Anonymous
        val userEntity = fakeUserDao.getUserById(session.localUserId)
        assertThat(userEntity).isNotNull()
        assertThat(userEntity!!.syncState).isEqualTo(SyncState.PENDING)
    }

    @Test
    fun `register uses input username when backend returns empty displayName`() = testScope.runTest {
        // Given: Backend returns an empty username in the registration response
        fakeAuthNetworkDataSource.authResponse = AuthResponse(
            token = "fake_token",
            refreshToken = "fake_refresh",
            userId = UUID.randomUUID().toString(),
            username = "",
            email = "test@example.com",
            expiresIn = 3600
        )

        // When: Registering with a username
        val result = sessionManager.register("myusername", "test@example.com", "password123")

        // Then: The session uses the input username as displayName
        assertThat(result.isSuccess).isTrue()
        val user = result.getOrNull()
        assertThat(user?.displayName).isEqualTo("myusername")

        val session = sessionManager.userSession.value as UserSession.Authenticated
        assertThat(session.user.displayName).isEqualTo("myusername")
    }

    // --- Account Upgrade Tests ---

    private fun createRecipeForUser(creatorId: UUID) = RecipeEntity(
        uuid = UUID.randomUUID(),
        title = "Test Recipe",
        description = "A test recipe",
        imageUrl = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 10,
        cookTimeMinutes = 20,
        servings = 4,
        creatorId = creatorId,
        recipeExternalUrl = null,
        privacy = RecipePrivacy.PRIVATE,
        updatedAt = 1000L,
        deletedAt = null,
        syncState = SyncState.PENDING
    )

    @Test
    fun `login transfers anonymous recipes to authenticated user`() = testScope.runTest {
        // Given: Anonymous session with a recipe
        sessionManager.loadSession()
        val anonSession = sessionManager.userSession.first() as UserSession.Anonymous
        val anonUserId = anonSession.localUserId

        val recipe = createRecipeForUser(anonUserId)
        fakeRecipeDao.seed(
            users = listOf(fakeUserDao.getUserById(anonUserId)!!),
            recipes = listOf(recipe)
        )

        // When: User logs in
        val result = sessionManager.login("test@example.com", "password123")

        // Then: Login succeeds
        assertThat(result.isSuccess).isTrue()
        val authSession = sessionManager.userSession.first() as UserSession.Authenticated

        // And: Recipe is now owned by the authenticated user
        val updatedRecipe = fakeRecipeDao.getRecipeById(recipe.uuid)
        assertThat(updatedRecipe?.creatorId).isEqualTo(authSession.user.uuid)
    }

    @Test
    fun `register transfers anonymous recipes to authenticated user`() = testScope.runTest {
        // Given: Anonymous session with a recipe
        sessionManager.loadSession()
        val anonSession = sessionManager.userSession.first() as UserSession.Anonymous
        val anonUserId = anonSession.localUserId

        val recipe = createRecipeForUser(anonUserId)
        fakeRecipeDao.seed(
            users = listOf(fakeUserDao.getUserById(anonUserId)!!),
            recipes = listOf(recipe)
        )

        // When: User registers
        val result = sessionManager.register("testuser", "test@example.com", "password123")

        // Then: Registration succeeds
        assertThat(result.isSuccess).isTrue()
        val authSession = sessionManager.userSession.first() as UserSession.Authenticated

        // And: Recipe is now owned by the authenticated user
        val updatedRecipe = fakeRecipeDao.getRecipeById(recipe.uuid)
        assertThat(updatedRecipe?.creatorId).isEqualTo(authSession.user.uuid)
    }

    @Test
    fun `login succeeds even if upgrade throws`() = testScope.runTest {
        // Given: Anonymous session
        sessionManager.loadSession()

        // Create a new SessionManager where upgrade provider throws
        val throwingSessionManager = SessionManager(
            securePreferences = fakeSecurePreferences,
            authNetworkDataSource = Provider { fakeAuthNetworkDataSource },
            userDao = fakeUserDao,
            accountUpgradeUseCaseProvider = Provider { throw RuntimeException("Upgrade failed") },
            syncSchedulerProvider = { FakeSyncManager() },
            applicationScope = testScope
        ).apply { uuidGenerator = { UUID.randomUUID() } }
        advanceUntilIdle()

        // When: User logs in (upgrade will throw)
        val result = throwingSessionManager.login("test@example.com", "password123")

        // Then: Login still succeeds despite upgrade failure
        assertThat(result.isSuccess).isTrue()
        val session = throwingSessionManager.userSession.first()
        assertThat(session).isInstanceOf(UserSession.Authenticated::class.java)
    }

    @Test
    fun `login without anonymous recipes skips upgrade gracefully`() = testScope.runTest {
        // Given: Anonymous session with no recipes
        sessionManager.loadSession()
        val anonSession = sessionManager.userSession.first() as UserSession.Anonymous
        assertThat(fakeRecipeDao.countRecipesForUser(anonSession.localUserId)).isEqualTo(0)

        // When: User logs in
        val result = sessionManager.login("test@example.com", "password123")

        // Then: Login succeeds with no errors
        assertThat(result.isSuccess).isTrue()
        val session = sessionManager.userSession.first()
        assertThat(session).isInstanceOf(UserSession.Authenticated::class.java)
    }

    // --- Sync Lifecycle Tests ---

    @Test
    fun `login triggers immediate sync and schedules periodic sync`() = testScope.runTest {
        // Given: Anonymous session
        sessionManager.loadSession()
        fakeSyncManager.reset()

        // When: User logs in
        sessionManager.login("test@example.com", "password123")

        // Then: Immediate sync was requested and periodic sync was scheduled
        assertThat(fakeSyncManager.immediateSyncCount).isEqualTo(1)
        assertThat(fakeSyncManager.periodicSyncCount).isEqualTo(1)
    }

    @Test
    fun `register triggers immediate sync and schedules periodic sync`() = testScope.runTest {
        // Given: Anonymous session
        sessionManager.loadSession()
        fakeSyncManager.reset()

        // When: User registers
        sessionManager.register("testuser", "test@example.com", "password123")

        // Then: Immediate sync was requested and periodic sync was scheduled
        assertThat(fakeSyncManager.immediateSyncCount).isEqualTo(1)
        assertThat(fakeSyncManager.periodicSyncCount).isEqualTo(1)
    }

    @Test
    fun `logout cancels all sync work`() = testScope.runTest {
        // Given: User is logged in
        sessionManager.login("test@example.com", "password123")
        fakeSyncManager.reset()

        // When: User logs out
        sessionManager.logout()

        // Then: All sync work was cancelled
        assertThat(fakeSyncManager.cancelAllCount).isEqualTo(1)
    }

    @Test
    fun `login failure does not trigger sync`() = testScope.runTest {
        // Given: Network error
        fakeAuthNetworkDataSource.shouldThrowError = true
        sessionManager.loadSession()
        fakeSyncManager.reset()

        // When: Login fails
        sessionManager.login("test@example.com", "password123")

        // Then: No sync was triggered
        assertThat(fakeSyncManager.immediateSyncCount).isEqualTo(0)
        assertThat(fakeSyncManager.periodicSyncCount).isEqualTo(0)
    }
}
