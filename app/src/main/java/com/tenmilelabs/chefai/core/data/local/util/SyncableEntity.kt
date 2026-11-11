package com.tenmilelabs.chefai.core.data.local.util

import java.util.UUID

interface SyncableEntity {
    val uuid: UUID
    val updatedAt: Long
    val deletedAt: Long?
    val syncState: SyncState
}

interface SyncableCrossRef {
    val updatedAt: Long
    val deletedAt: Long?
    val syncState: SyncState
}

/**
 * The `updatedAt` for a local edit of a row whose current stamp is [previous] (null for a new row).
 * The result always sorts after the version it replaces, whatever the device clock says, so a slow
 * or rewound clock cannot make an edit lose last-writer-wins to the version it was made on top of.
 * The DAO `softDelete` queries apply the same rule in SQL: `MAX(:deletedAt, updatedAt + 1)`.
 */
fun nextUpdatedAt(previous: Long?, now: Long): Long =
    if (previous == null) now else maxOf(now, previous + 1)

enum class SyncState {
    PENDING,  // newly created or edited locally
    SYNCED,   // in sync with backend
    DELETED,  // deleted locally, pending server delete
    CONFLICT,  // optional, if merge failed
    CANCELLED   // sync canelled for whaterver reason
}

