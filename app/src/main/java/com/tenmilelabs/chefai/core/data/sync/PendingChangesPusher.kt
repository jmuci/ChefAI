package com.tenmilelabs.chefai.core.data.sync

/**
 * Push-only access to the sync engine, for a caller that must upload local changes but must not
 * pull — logout, where anything pulled would land for an account that is on its way out.
 *
 * Unlike [SyncScheduler] this runs in the caller's coroutine and suspends until done, and unlike
 * [SyncExecutor] it skips `GET /sync/pull`.
 */
interface PendingChangesPusher {

    /**
     * How many rows a push would send right now: everything `PENDING` or `DELETED`, with
     * bookmarks limited to the signed-in user as [pushPendingChanges] limits them.
     */
    suspend fun countPendingChanges(): Int

    /**
     * One `POST /sync/push` pass over every dirty row. Throws on a network or HTTP failure
     * ([com.tenmilelabs.chefai.core.data.sync.network.SyncHttpException] carries the status), as
     * [SyncExecutor.sync] does.
     */
    suspend fun pushPendingChanges(): PushResult
}
