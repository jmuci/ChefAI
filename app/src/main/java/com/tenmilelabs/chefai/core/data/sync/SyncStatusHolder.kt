package com.tenmilelabs.chefai.core.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Synced(val lastSyncedAt: Long) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
    data object Offline : SyncStatus()
}

@Singleton
class SyncStatusHolder @Inject constructor() {
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    fun emitStatus(status: SyncStatus) {
        _syncStatus.value = status
    }
}
