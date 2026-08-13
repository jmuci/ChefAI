package com.tenmilelabs.chefai.core.data.sync

/**
 * Abstraction for scheduling sync operations.
 * Allows [SessionManager] to trigger sync without depending directly on
 * WorkManager or Android context, enabling easy testing.
 */
interface SyncScheduler {
    fun requestImmediateSync()
    fun requestMutationSync()
    /**
     * Enqueues a bookmark-specific sync with a short initial delay, using REPLACE policy.
     * Each call resets the delay window, naturally batching rapid bookmark actions into
     * a single sync cycle. Near-immediate for the first action, coalescing for bursts.
     */
    fun requestBookmarkSync()
    /** Cancels any queued sync and enqueues a new one immediately (user-initiated). */
    fun requestManualSync()
    fun schedulePeriodicSync()

    /**
     * Enqueues a sweep for recipe images this device is missing but a synced-from device already had.
     *
     * Constrained to a charger and unmetered network, unlike every other request here: the work is a
     * burst of image downloads with an off-screen `WebView` behind some of them, and nobody is
     * waiting on it.
     */
    fun scheduleImageBackfill()
    fun cancelAllSync()
}
