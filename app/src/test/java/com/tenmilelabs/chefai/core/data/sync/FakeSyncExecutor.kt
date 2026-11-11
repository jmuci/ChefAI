package com.tenmilelabs.chefai.core.data.sync

class FakeSyncExecutor : SyncExecutor {

    var syncCount = 0
        private set

    var shouldThrow: Boolean = false

    /** Thrown one per call, in order, before [shouldThrow] is consulted — e.g. a 401 then success. */
    val errorsToThrow: ArrayDeque<Exception> = ArrayDeque()

    /** Reported in every successful [SyncResult]. */
    var hasUnsentChanges: Boolean = false

    override suspend fun sync(): SyncResult {
        errorsToThrow.removeFirstOrNull()?.let { throw it }
        if (shouldThrow) throw RuntimeException("Fake sync error")
        syncCount++
        return SyncResult(
            pushResult = PushResult(accepted = 0, conflicts = 0, errors = 0),
            pullResult = PullResult(upserted = 0, deleted = 0, pages = 1),
            hasUnsentChanges = hasUnsentChanges,
        )
    }
}
