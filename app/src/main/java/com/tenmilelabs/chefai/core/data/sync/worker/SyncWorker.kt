package com.tenmilelabs.chefai.core.data.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.data.sync.SyncExecutor
import com.tenmilelabs.chefai.core.data.sync.SyncResult
import com.tenmilelabs.chefai.core.data.sync.SyncStatus
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.core.data.sync.SyncStatusHolder
import com.tenmilelabs.chefai.core.data.sync.network.SyncHttpException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    // The interface rather than SyncOrchestrator itself, so tests can drive the worker with a fake.
    private val syncExecutor: SyncExecutor,
    private val sessionManager: SessionManager,
    private val syncStatusHolder: SyncStatusHolder,
    private val syncScheduler: SyncScheduler
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val MAX_RETRY_COUNT = 3
        private const val SESSION_LOAD_TIMEOUT_MS = 10_000L
        private const val HTTP_UPGRADE_REQUIRED = 426
    }

    override suspend fun doWork(): Result {
        // Only sync when authenticated. SessionManager restores the session asynchronously, so a
        // worker started in a fresh process (periodic run, backoff retry) would otherwise nearly
        // always see Loading and skip — reporting success, so it is never retried either.
        val session = withTimeoutOrNull(SESSION_LOAD_TIMEOUT_MS) {
            sessionManager.userSession.first { it !is UserSession.Loading }
        }
        if (session !is UserSession.Authenticated) {
            Timber.d("SyncWorker: Skipping sync — user is not authenticated")
            return Result.success()
        }

        syncStatusHolder.emitStatus(SyncStatus.Syncing)

        return try {
            val result = syncExecutor.sync()
            Timber.d("SyncWorker: Sync completed — $result")
            onSyncSucceeded(result)
        } catch (e: SyncHttpException) {
            if (e.statusCode == 401) {
                Timber.d("SyncWorker: 401 received, attempting token refresh...")
                val refreshResult = sessionManager.refreshToken()
                if (refreshResult.isSuccess) {
                    return try {
                        val result = syncExecutor.sync()
                        Timber.d("SyncWorker: Sync after refresh completed — $result")
                        onSyncSucceeded(result)
                    } catch (retryException: CancellationException) {
                        syncStatusHolder.emitStatus(SyncStatus.Idle)
                        throw retryException
                    } catch (retryException: Exception) {
                        handleFailure(retryException)
                    }
                }
            }
            handleFailure(e)
        } catch (e: CancellationException) {
            // Not a failure: WorkManager stopped this worker — most often because something
            // enqueued replacement work under the same unique name (requestManualSync and
            // requestBookmarkSync both use ExistingWorkPolicy.REPLACE, which cancels a *running*
            // worker, not just a queued one). Reporting Error here put "Sync failed" in front of
            // the user for a sync that was merely superseded, and swallowing the cancellation
            // breaks structured concurrency besides.
            syncStatusHolder.emitStatus(SyncStatus.Idle)
            throw e
        } catch (e: Exception) {
            handleFailure(e)
        }
    }

    /**
     * Shared by the first attempt and the post-refresh retry, so every successful sync enqueues the
     * image sweeps — the retry path used to skip them.
     */
    private fun onSyncSucceeded(result: SyncResult): Result {
        syncStatusHolder.emitStatus(SyncStatus.Synced(System.currentTimeMillis()))
        // An edit made while this run was in flight asked for a sync, but mutation sync's KEEP
        // dropped that request because this work was still running.
        if (result.hasUnsentChanges) syncScheduler.requestFollowUpSync()
        // A pull can bring in recipes whose image only exists on the device that imported them.
        // Enqueued rather than chained: backfill waits for a charger and unmetered data, and a
        // chain would hold this sync's slot until those were satisfied.
        syncScheduler.scheduleImageBackfill()
        // The mirror of that: images this device holds that the backend doesn't. Enqueued after
        // the push specifically, since a recipe row must exist server-side before its image can
        // attach to it — which is why the upload sweep only considers SYNCED rows.
        syncScheduler.scheduleImageUpload()
        return Result.success()
    }

    private fun handleFailure(e: Exception): Result {
        if (e is SyncHttpException && e.statusCode == HTTP_UPGRADE_REQUIRED) {
            // The server no longer accepts this app's sync protocol: every retry would get the
            // same answer, so stop here and say so.
            Timber.w("SyncWorker: server requires an app update to sync (426)")
            syncStatusHolder.emitStatus(SyncStatus.UpdateRequired)
            return Result.failure()
        }
        Timber.e(e, "SyncWorker: Sync failed (attempt ${runAttemptCount + 1})")
        syncStatusHolder.emitStatus(SyncStatus.Error(e.message ?: "Sync failed"))
        return if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
    }
}
