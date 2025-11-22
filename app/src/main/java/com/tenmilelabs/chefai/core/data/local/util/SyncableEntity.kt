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

enum class SyncState {
    PENDING,  // newly created or edited locally
    SYNCED,   // in sync with backend
    DELETED,  // deleted locally, pending server delete
    CONFLICT,  // optional, if merge failed
    CANCELLED   // sync canelled for whaterver reason
}

