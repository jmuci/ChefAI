package com.tenmilelabs.chefai.core.data.sync

/**
 * Abstraction for scheduling sync operations.
 * Allows [SessionManager] to trigger sync without depending directly on
 * WorkManager or Android context, enabling easy testing.
 */
interface SyncScheduler {
    fun requestImmediateSync()
    fun requestMutationSync()
    fun schedulePeriodicSync()
    fun cancelAllSync()
}
