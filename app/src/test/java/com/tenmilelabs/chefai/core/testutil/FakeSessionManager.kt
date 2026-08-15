package com.tenmilelabs.chefai.core.testutil

import com.tenmilelabs.chefai.auth.data.local.FakeSecurePreferences
import com.tenmilelabs.chefai.auth.data.network.FakeAuthNetworkDataSource
import com.tenmilelabs.chefai.auth.domain.AccountSwitchHandler
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.usecase.AccountUpgradeUseCase
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.FakeTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeUserDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeBookmarkedRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeMealPlanDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.sync.FakeSyncManager
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * No-op implementation of [SyncScheduler] for testing.
 */
class FakeSyncScheduler : SyncScheduler {
    override fun requestImmediateSync() {}
    override fun requestMutationSync() {}
    override fun requestBookmarkSync() {}
    override fun requestManualSync() {}
    override fun schedulePeriodicSync() {}
    override fun scheduleImageBackfill() {}
    override fun scheduleImageUpload() {}
    override fun cancelAllSync() {}
}

/**
 * Builds a real [SessionManager] wired with in-memory fakes, ready for use in unit tests.
 *
 * Synchronously calls [SessionManager.loadSession] to ensure the anonymous session is fully
 * loaded before returning, so [SessionManager.getCurrentUserId] returns a non-null UUID
 * immediately without race conditions or uncaught async exceptions.
 *
 * Pass a custom [testScope] when the test already owns a [CoroutineScope] with a different
 * dispatcher and needs the session coroutines to participate in the same domain.
 */
fun createTestSessionManager(
    testScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
): SessionManager {
    val fakeUserDao = FakeUserDao()
    val fakeSecurePreferences = FakeSecurePreferences()
    val sessionManager = SessionManager(
        securePreferences = fakeSecurePreferences,
        authNetworkDataSource = { FakeAuthNetworkDataSource() },
        userDao = fakeUserDao,
        accountSwitchHandler = AccountSwitchHandler(
            securePreferences = fakeSecurePreferences,
            database = mockk<ChefAIDataBase>(relaxed = true),
            recipeDao = FakeRecipeDao(),
            userDao = fakeUserDao,
            recipeImageStore = mockk<RecipeImageStore>(relaxed = true),
        ),
        accountUpgradeUseCaseProvider = {
            AccountUpgradeUseCase(
                FakeTransactionRunner(),
                fakeUserDao,
                FakeRecipeDao(),
                FakeRecipeStepDao(),
                FakeRecipeIngredientDao(),
                FakeRecipeTagCrossRefDao(),
                FakeRecipeLabelCrossRefDao(),
                FakeBookmarkedRecipeDao(),
                FakeMealPlanDao(),
            )
        },
        syncSchedulerProvider = { FakeSyncScheduler() },
        applicationScope = testScope
    ).apply {
        uuidGenerator = { UuidV7Generator.newId() }
    }

    // Synchronously load the session to ensure it's fully initialized before the test runs.
    // This prevents race conditions and uncaught exceptions in async coroutines.
    runBlocking {
        sessionManager.loadSession()
    }

    return sessionManager
}

/**
 * Like [createTestSessionManager], but also returns the [FakeAuthNetworkDataSource] backing it —
 * for tests that need to drive a *real* [SessionManager.refreshToken] failure (e.g. by setting
 * [FakeAuthNetworkDataSource.shouldThrowError]) rather than mocking the `Result`-returning suspend
 * function directly. MockK's suspend-function stubbing does not reliably preserve `Result.failure`
 * across its proxy for a function whose return type is itself `kotlin.Result` (a known MockK/Kotlin
 * inline-class interaction limitation, not specific to this codebase) — going through the real
 * object sidesteps it entirely.
 */
fun createTestSessionManagerWithAuthSource(
    testScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
): Pair<SessionManager, FakeAuthNetworkDataSource> {
    val fakeAuthNetworkDataSource = FakeAuthNetworkDataSource()
    val fakeUserDao = FakeUserDao()
    val fakeSecurePreferences = FakeSecurePreferences()
    val sessionManager = SessionManager(
        securePreferences = fakeSecurePreferences,
        authNetworkDataSource = { fakeAuthNetworkDataSource },
        userDao = fakeUserDao,
        accountSwitchHandler = AccountSwitchHandler(
            securePreferences = fakeSecurePreferences,
            database = mockk<ChefAIDataBase>(relaxed = true),
            recipeDao = FakeRecipeDao(),
            userDao = fakeUserDao,
            recipeImageStore = mockk<RecipeImageStore>(relaxed = true),
        ),
        accountUpgradeUseCaseProvider = {
            AccountUpgradeUseCase(
                FakeTransactionRunner(),
                fakeUserDao,
                FakeRecipeDao(),
                FakeRecipeStepDao(),
                FakeRecipeIngredientDao(),
                FakeRecipeTagCrossRefDao(),
                FakeRecipeLabelCrossRefDao(),
                FakeBookmarkedRecipeDao(),
                FakeMealPlanDao(),
            )
        },
        syncSchedulerProvider = { FakeSyncScheduler() },
        applicationScope = testScope
    ).apply {
        uuidGenerator = { UuidV7Generator.newId() }
    }

    runBlocking {
        sessionManager.loadSession()
    }

    return sessionManager to fakeAuthNetworkDataSource
}

fun createRealSessionManagerWithFakes(
    testScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
): SessionManager {
    val fakeUserDao = FakeUserDao()
    val fakeSecurePreferences = FakeSecurePreferences()
    val sessionManager = SessionManager(
        securePreferences = fakeSecurePreferences,
        authNetworkDataSource = { FakeAuthNetworkDataSource() },
        userDao = fakeUserDao,
        accountSwitchHandler = AccountSwitchHandler(
            securePreferences = fakeSecurePreferences,
            database = mockk<ChefAIDataBase>(relaxed = true),
            recipeDao = FakeRecipeDao(),
            userDao = fakeUserDao,
            recipeImageStore = mockk<RecipeImageStore>(relaxed = true),
        ),
        accountUpgradeUseCaseProvider = {
            AccountUpgradeUseCase(
                FakeTransactionRunner(), fakeUserDao, FakeRecipeDao(),
                FakeRecipeStepDao(), FakeRecipeIngredientDao(),
                FakeRecipeTagCrossRefDao(), FakeRecipeLabelCrossRefDao(),
                FakeBookmarkedRecipeDao(), FakeMealPlanDao(),
            )
        },
        syncSchedulerProvider = { FakeSyncManager() },
        applicationScope = testScope
    ).apply {
        uuidGenerator = { UuidV7Generator.newId() }
    }
    return sessionManager
}
