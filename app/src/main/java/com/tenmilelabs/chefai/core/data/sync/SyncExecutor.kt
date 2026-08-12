package com.tenmilelabs.chefai.core.data.sync

/**
 * Executes a full sync cycle (push + pull) and suspends until complete.
 *
 * Unlike [SyncScheduler] which fires-and-forgets via WorkManager, this interface
 * lets callers await the result — useful when the next step depends on sync completion
 * (e.g., push a meal plan, then call generate, then pull results).
 */
interface SyncExecutor {
    suspend fun sync(): SyncResult
}
