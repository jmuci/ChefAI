package com.tenmilelabs.chefai.core.data.sync

/**
 * Abstraction for scheduling sync operations.
 * Allows [SessionManager] to trigger sync without depending directly on
 * WorkManager or Android context, enabling easy testing.
 */
interface SyncScheduler {
    fun requestImmediateSync()
    fun requestMutationSync()
    /** Cancels any queued sync and enqueues a new one immediately (user-initiated). */
    fun requestManualSync()
    fun schedulePeriodicSync()
    fun cancelAllSync()
}
