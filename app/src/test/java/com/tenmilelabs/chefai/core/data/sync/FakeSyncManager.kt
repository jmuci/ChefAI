package com.tenmilelabs.chefai.core.data.sync

/**
 * Test double for [SyncManager] that records method calls for verification.
 * Since SyncManager depends on Android's WorkManager, this fake replaces
 * it entirely in unit tests.
 */
class FakeSyncManager : SyncScheduler {

    var immediateSyncCount = 0
        private set

    var mutationSyncCount = 0
        private set

    var bookmarkSyncCount = 0
        private set

    var manualSyncCount = 0
        private set

    var periodicSyncCount = 0
        private set

    var imageBackfillCount = 0
        private set

    var imageUploadCount = 0
        private set

    var cancelAllCount = 0
        private set

    override fun requestImmediateSync() {
        immediateSyncCount++
    }

    override fun requestMutationSync() {
        mutationSyncCount++
    }

    override fun requestBookmarkSync() {
        bookmarkSyncCount++
    }

    override fun requestManualSync() {
        manualSyncCount++
    }

    override fun schedulePeriodicSync() {
        periodicSyncCount++
    }

    override fun scheduleImageBackfill() {
        imageBackfillCount++
    }

    override fun scheduleImageUpload() {
        imageUploadCount++
    }

    override fun cancelAllSync() {
        cancelAllCount++
    }

    fun reset() {
        immediateSyncCount = 0
        mutationSyncCount = 0
        bookmarkSyncCount = 0
        manualSyncCount = 0
        periodicSyncCount = 0
        imageBackfillCount = 0
        imageUploadCount = 0
        cancelAllCount = 0
    }
}
