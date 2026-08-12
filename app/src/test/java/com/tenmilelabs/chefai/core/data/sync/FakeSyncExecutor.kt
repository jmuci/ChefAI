package com.tenmilelabs.chefai.core.data.sync

class FakeSyncExecutor : SyncExecutor {

    var syncCount = 0
        private set

    var shouldThrow: Boolean = false

    override suspend fun sync(): SyncResult {
        if (shouldThrow) throw RuntimeException("Fake sync error")
        syncCount++
        return SyncResult(
            pushResult = PushResult(accepted = 0, conflicts = 0, errors = 0),
            pullResult = PullResult(upserted = 0, deleted = 0, pages = 1),
        )
    }
}
