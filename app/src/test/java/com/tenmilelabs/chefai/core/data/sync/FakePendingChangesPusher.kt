package com.tenmilelabs.chefai.core.data.sync

import kotlinx.coroutines.delay

/**
 * Fake [PendingChangesPusher] (the push-only face of [SyncOrchestrator]) over a plain counter of
 * dirty rows. A successful push clears all of them except [rowsRejectedByServer].
 */
class FakePendingChangesPusher : PendingChangesPusher {

    /** Rows currently `PENDING`/`DELETED`. */
    var pending = 0

    /** Virtual-time delay before each push completes — set past the logout budget to time out. */
    var pushDelayMs = 0L

    /** Thrown by the next pushes, one per push, in order; empty means the push succeeds. */
    val failures = ArrayDeque<Exception>()

    /** Rows a successful push leaves dirty, as rows the server returned errors for do. */
    var rowsRejectedByServer = 0

    /** Runs at the start of each push, before the delay — to observe the session at that moment. */
    var onPush: () -> Unit = {}

    var pushCount = 0
        private set

    var countCalls = 0
        private set

    override suspend fun countPendingChanges(): Int {
        countCalls++
        return pending
    }

    override suspend fun pushPendingChanges(): PushResult {
        pushCount++
        onPush()
        delay(pushDelayMs)
        failures.removeFirstOrNull()?.let { throw it }
        val left = minOf(rowsRejectedByServer, pending)
        val accepted = pending - left
        pending = left
        return PushResult(accepted = accepted, conflicts = 0, errors = left)
    }
}
