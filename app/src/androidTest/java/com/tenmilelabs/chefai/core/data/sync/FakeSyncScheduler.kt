package com.tenmilelabs.chefai.core.data.sync

import timber.log.Timber

/**
 * No-op [SyncScheduler] for instrumented tests.
 * Prevents WorkManager from being called — WorkManager is not initialised
 * in the Hilt test application and would throw otherwise.
 */
class FakeSyncScheduler : SyncScheduler {
    override fun requestImmediateSync() {
        Timber.d("FakeSyncScheduler: requestImmediateSync (no-op)")
    }

    override fun requestMutationSync() {
        Timber.d("FakeSyncScheduler: requestMutationSync (no-op)")
    }

    override fun requestBookmarkSync() {
        Timber.d("FakeSyncScheduler: requestBookmarkSync (no-op)")
    }

    override fun requestManualSync() {
        Timber.d("FakeSyncScheduler: requestManualSync (no-op)")
    }

    override fun schedulePeriodicSync() {
        Timber.d("FakeSyncScheduler: schedulePeriodicSync (no-op)")
    }

    override fun cancelAllSync() {
        Timber.d("FakeSyncScheduler: cancelAllSync (no-op)")
    }
}
