package com.tenmilelabs.chefai.core.data.sync.worker

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.data.network.FakeAuthNetworkDataSource
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.data.sync.FakeSyncExecutor
import com.tenmilelabs.chefai.core.data.sync.FakeSyncManager
import com.tenmilelabs.chefai.core.data.sync.SyncStatus
import com.tenmilelabs.chefai.core.data.sync.SyncStatusHolder
import com.tenmilelabs.chefai.core.data.sync.network.SyncHttpException
import com.tenmilelabs.chefai.core.testutil.createTestSessionManagerWithAuthSource
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Robolectric only for the [Context] and [WorkerParameters] that [TestListenableWorkerBuilder]
 * needs; everything the worker talks to is a fake. Bare [Application] and pinned SDK for the same
 * reasons as `RecipeTimerNotifierTest`.
 */
@Config(application = Application::class, sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private lateinit var syncExecutor: FakeSyncExecutor
    private lateinit var syncScheduler: FakeSyncManager
    private lateinit var syncStatusHolder: SyncStatusHolder

    @Before
    fun setup() {
        syncExecutor = FakeSyncExecutor()
        syncScheduler = FakeSyncManager()
        syncStatusHolder = SyncStatusHolder()
    }

    @Test
    fun `successful sync enqueues both image sweeps`() = runTest {
        // Given
        val (sessionManager, _) = authenticatedSessionManager()

        // When
        val result = buildWorker(sessionManager).doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(syncExecutor.syncCount).isEqualTo(1)
        assertThat(syncScheduler.imageBackfillCount).isEqualTo(1)
        assertThat(syncScheduler.imageUploadCount).isEqualTo(1)
        assertThat(syncStatusHolder.syncStatus.value).isInstanceOf(SyncStatus.Synced::class.java)
    }

    @Test
    fun `sync that succeeds after a token refresh still enqueues both image sweeps`() = runTest {
        // Given — the first attempt is rejected with a 401, the retry after refresh goes through
        val (sessionManager, _) = authenticatedSessionManager()
        syncExecutor.errorsToThrow.add(SyncHttpException("Unauthorized", statusCode = 401))

        // When
        val result = buildWorker(sessionManager).doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(syncExecutor.syncCount).isEqualTo(1)
        assertThat(syncScheduler.imageBackfillCount).isEqualTo(1)
        assertThat(syncScheduler.imageUploadCount).isEqualTo(1)
        assertThat(syncStatusHolder.syncStatus.value).isInstanceOf(SyncStatus.Synced::class.java)
    }

    @Test
    fun `401 whose token refresh fails retries without enqueuing the image sweeps`() = runTest {
        // Given
        val (sessionManager, authSource) = authenticatedSessionManager()
        authSource.shouldThrowError = true
        syncExecutor.errorsToThrow.add(SyncHttpException("Unauthorized", statusCode = 401))

        // When
        val result = buildWorker(sessionManager).doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
        assertThat(syncScheduler.imageBackfillCount).isEqualTo(0)
        assertThat(syncScheduler.imageUploadCount).isEqualTo(0)
        assertThat(syncStatusHolder.syncStatus.value).isInstanceOf(SyncStatus.Error::class.java)
    }

    @Test
    fun `anonymous session skips the sync and the image sweeps`() = runTest {
        // Given
        val (sessionManager, _) = createTestSessionManagerWithAuthSource(backgroundScope)

        // When
        val result = buildWorker(sessionManager).doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(syncExecutor.syncCount).isEqualTo(0)
        assertThat(syncScheduler.imageBackfillCount).isEqualTo(0)
        assertThat(syncScheduler.imageUploadCount).isEqualTo(0)
    }

    private suspend fun TestScope.authenticatedSessionManager():
        Pair<SessionManager, FakeAuthNetworkDataSource> {
        val pair = createTestSessionManagerWithAuthSource(backgroundScope)
        assertThat(pair.first.login("cook@example.com", "hunter2").isSuccess).isTrue()
        return pair
    }

    private fun buildWorker(sessionManager: SessionManager): SyncWorker =
        TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = SyncWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    syncExecutor = syncExecutor,
                    sessionManager = sessionManager,
                    syncStatusHolder = syncStatusHolder,
                    syncScheduler = syncScheduler
                )
            })
            .build()
}
