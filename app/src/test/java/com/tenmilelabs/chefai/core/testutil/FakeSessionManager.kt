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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.util.UUID

/**
 * Builds a real [SessionManager] wired with in-memory fakes, ready for use in unit tests.
 *
 * Uses [UnconfinedTestDispatcher] by default so the anonymous session is resolved
 * eagerly during [SessionManager.init], meaning [SessionManager.getCurrentUserId]
 * returns a non-null UUID immediately after construction — no `advanceUntilIdle()`
 * call required at the call site.
 *
 * Pass a custom [testScope] when the test already owns a [CoroutineScope] and needs
 * the session coroutines to participate in the same virtual-time domain.
 */
fun createTestSessionManager(
    testScope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher())
): SessionManager {
    val fakeUserDao = FakeUserDao()
    return SessionManager(
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
        applicationScope = testScope
    ).apply {
        uuidGenerator = { UUID.randomUUID() }
    }
}
