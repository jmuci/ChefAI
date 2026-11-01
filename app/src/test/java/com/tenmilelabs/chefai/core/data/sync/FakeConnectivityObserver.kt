package com.tenmilelabs.chefai.core.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fake [ConnectivityObserver] that exposes a mutable [isOnline] flow for tests. Online by default.
 */
class FakeConnectivityObserver : ConnectivityObserver {
    private val _isOnline = MutableStateFlow(true)
    override val isOnline: StateFlow<Boolean> get() = _isOnline

    fun setOnline(online: Boolean) {
        _isOnline.value = online
    }
}
