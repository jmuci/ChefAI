package com.tenmilelabs.chefai.core.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.core.data.sync.ConnectivityObserver
import com.tenmilelabs.chefai.core.data.sync.SyncStatus
import com.tenmilelabs.chefai.core.data.sync.SyncStatusHolder
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Display state for the sync indicator shown in the TopAppBar.
 */
sealed class SyncDisplayStatus {
    data object Idle : SyncDisplayStatus()
    data object Syncing : SyncDisplayStatus()
    data class Synced(val lastSyncedAt: Long) : SyncDisplayStatus()
    data class Error(val message: String) : SyncDisplayStatus()
    data object Offline : SyncDisplayStatus()
}

/**
 * Combines the underlying [SyncStatus] from [SyncStatusHolder] and
 * [ConnectivityObserver.isOnline] into a single [SyncDisplayStatus] for the UI layer.
 *
 * Priority: offline state always takes precedence, then maps directly from [SyncStatus].
 */
@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    syncStatusHolder: SyncStatusHolder,
    connectivityObserver: ConnectivityObserver
) : ViewModel() {

    val displayStatus: StateFlow<SyncDisplayStatus> =
        combine(syncStatusHolder.syncStatus, connectivityObserver.isOnline) { syncStatus, isOnline ->
            when {
                !isOnline -> SyncDisplayStatus.Offline
                else -> when (syncStatus) {
                    is SyncStatus.Idle -> SyncDisplayStatus.Idle
                    is SyncStatus.Syncing -> SyncDisplayStatus.Syncing
                    is SyncStatus.Synced -> SyncDisplayStatus.Synced(syncStatus.lastSyncedAt)
                    is SyncStatus.Error -> SyncDisplayStatus.Error(syncStatus.message)
                    is SyncStatus.Offline -> SyncDisplayStatus.Offline
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = SyncDisplayStatus.Idle
        )
}
