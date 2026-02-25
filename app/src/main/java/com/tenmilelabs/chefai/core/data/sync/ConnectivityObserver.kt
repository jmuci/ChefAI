package com.tenmilelabs.chefai.core.data.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over device connectivity observation.
 * Implemented by [NetworkMonitor] in production and by fakes in tests.
 */
interface ConnectivityObserver {
    val isOnline: StateFlow<Boolean>
}
