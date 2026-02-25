package com.tenmilelabs.chefai.core.testutil

import com.tenmilelabs.chefai.auth.data.local.FakeSecurePreferences
import com.tenmilelabs.chefai.auth.data.network.FakeAuthNetworkDataSource
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.usecase.AccountUpgradeUseCase
import com.tenmilelabs.chefai.core.data.local.room.FakeTransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeUserDao
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * No-op implementation of [SyncScheduler] for testing.
 */
class FakeSyncScheduler : SyncScheduler {
    override fun requestImmediateSync() {}
    override fun requestMutationSync() {}
    override fun schedulePeriodicSync() {}
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
    val sessionManager = SessionManager(
        securePreferences = FakeSecurePreferences(),
        authNetworkDataSource = { FakeAuthNetworkDataSource() },
        userDao = fakeUserDao,
        accountUpgradeUseCaseProvider = {
            AccountUpgradeUseCase(
                FakeTransactionRunner(),
                fakeUserDao,
                FakeRecipeDao(),
                FakeRecipeStepDao(),
                FakeRecipeIngredientDao(),
                FakeRecipeTagCrossRefDao(),
                FakeRecipeLabelCrossRefDao()
            )
        },
        syncSchedulerProvider = { FakeSyncScheduler() },
        applicationScope = testScope
    ).apply {
        uuidGenerator = { UUID.randomUUID() }
    }

    // Synchronously load the session to ensure it's fully initialized before the test runs.
    // This prevents race conditions and uncaught exceptions in async coroutines.
    runBlocking {
        sessionManager.loadSession()
    }

    return sessionManager
}
