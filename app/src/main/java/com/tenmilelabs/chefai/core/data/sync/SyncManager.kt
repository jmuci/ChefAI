package com.tenmilelabs.chefai.core.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tenmilelabs.chefai.core.data.sync.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val PERIODIC_SYNC_INTERVAL = 1440L  // 24 hours in minutes
private const val BACKOFF_DELAY_SECONDS = 30L

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncStatusHolder: SyncStatusHolder
) : SyncScheduler {
    companion object {
        const val SYNC_WORK_NAME = "recipe_sync"
        const val PERIODIC_SYNC_WORK_NAME = "recipe_periodic_sync"
    }

    val syncStatus: StateFlow<SyncStatus> get() = syncStatusHolder.syncStatus

    /**
     * Enqueue a one-time sync immediately (e.g., on app foreground, after login).
     * Uses KEEP policy to avoid stacking duplicate requests.
     */
    override fun requestImmediateSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Enqueue a debounced sync after a local mutation.
     * 5-second delay allows batching multiple rapid mutations into one sync cycle.
     * Uses KEEP policy so rapid mutations don't stack requests.
     */
    override fun requestMutationSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(connectedConstraints())
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Schedule periodic sync approximately every 24 hours.
     * Only runs when device is charging and has network connectivity.
     */
    override fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_SYNC_INTERVAL, TimeUnit.MINUTES)
            .setConstraints(periodicSyncConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    /**
     * Cancel all sync work (e.g., on logout).
     */
    override fun cancelAllSync() {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(SYNC_WORK_NAME)
        wm.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
    }

    private fun connectedConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private fun periodicSyncConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresCharging(true)
        .build()
}
