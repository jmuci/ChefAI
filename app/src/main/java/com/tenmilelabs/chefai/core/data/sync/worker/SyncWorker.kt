package com.tenmilelabs.chefai.core.data.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.data.sync.SyncOrchestrator
import com.tenmilelabs.chefai.core.data.sync.SyncStatus
import com.tenmilelabs.chefai.core.data.sync.SyncStatusHolder
import com.tenmilelabs.chefai.core.data.sync.network.SyncHttpException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncOrchestrator: SyncOrchestrator,
    private val sessionManager: SessionManager,
    private val syncStatusHolder: SyncStatusHolder
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val MAX_RETRY_COUNT = 3
    }

    override suspend fun doWork(): Result {
        // Only sync when authenticated
        val session = sessionManager.userSession.value
        if (session !is UserSession.Authenticated) {
            Timber.d("SyncWorker: Skipping sync — user is not authenticated")
            return Result.success()
        }

        syncStatusHolder.emitStatus(SyncStatus.Syncing)

        return try {
            val result = syncOrchestrator.sync()
            Timber.d("SyncWorker: Sync completed — $result")
            syncStatusHolder.emitStatus(SyncStatus.Synced(System.currentTimeMillis()))
            Result.success()
        } catch (e: SyncHttpException) {
            if (e.statusCode == 401) {
                Timber.d("SyncWorker: 401 received, attempting token refresh...")
                val refreshResult = sessionManager.refreshToken()
                if (refreshResult.isSuccess) {
                    return try {
                        val result = syncOrchestrator.sync()
                        Timber.d("SyncWorker: Sync after refresh completed — $result")
                        syncStatusHolder.emitStatus(SyncStatus.Synced(System.currentTimeMillis()))
                        Result.success()
                    } catch (retryException: Exception) {
                        handleFailure(retryException)
                    }
                }
            }
            handleFailure(e)
        } catch (e: Exception) {
            handleFailure(e)
        }
    }

    private fun handleFailure(e: Exception): Result {
        Timber.e(e, "SyncWorker: Sync failed (attempt ${runAttemptCount + 1})")
        syncStatusHolder.emitStatus(SyncStatus.Error(e.message ?: "Sync failed"))
        return if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
    }
}
