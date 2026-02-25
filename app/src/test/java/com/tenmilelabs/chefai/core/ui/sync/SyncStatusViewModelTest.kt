package com.tenmilelabs.chefai.core.ui.sync

import com.tenmilelabs.chefai.core.data.sync.ConnectivityObserver
import com.tenmilelabs.chefai.core.data.sync.SyncStatus
import com.tenmilelabs.chefai.core.data.sync.SyncStatusHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var syncStatusHolder: SyncStatusHolder
    private lateinit var fakeConnectivity: FakeConnectivityObserver

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        syncStatusHolder = SyncStatusHolder()
        fakeConnectivity = FakeConnectivityObserver()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SyncStatusViewModel {
        return SyncStatusViewModel(syncStatusHolder, fakeConnectivity)
    }

    @Test
    fun `initial state is Idle when online and sync idle`() = runTest {
        fakeConnectivity.setOnline(true)
        syncStatusHolder.emitStatus(SyncStatus.Idle)

        val viewModel = createViewModel()
        val status = viewModel.displayStatus.first()

        assertEquals(SyncDisplayStatus.Idle, status)
    }

    @Test
    fun `offline overrides sync status`() = runTest {
        fakeConnectivity.setOnline(false)
        syncStatusHolder.emitStatus(SyncStatus.Syncing)

        val viewModel = createViewModel()
        val status = viewModel.displayStatus.first()

        assertEquals(SyncDisplayStatus.Offline, status)
    }

    @Test
    fun `syncing maps to Syncing when online`() = runTest {
        fakeConnectivity.setOnline(true)
        syncStatusHolder.emitStatus(SyncStatus.Syncing)

        val viewModel = createViewModel()
        val status = viewModel.displayStatus.first()

        assertEquals(SyncDisplayStatus.Syncing, status)
    }

    @Test
    fun `synced maps to Synced with timestamp when online`() = runTest {
        fakeConnectivity.setOnline(true)
        syncStatusHolder.emitStatus(SyncStatus.Synced(lastSyncedAt = 1234567890L))

        val viewModel = createViewModel()
        val status = viewModel.displayStatus.first()

        assertEquals(SyncDisplayStatus.Synced(lastSyncedAt = 1234567890L), status)
    }

    @Test
    fun `error maps to Error with message when online`() = runTest {
        fakeConnectivity.setOnline(true)
        syncStatusHolder.emitStatus(SyncStatus.Error("Network timeout"))

        val viewModel = createViewModel()
        val status = viewModel.displayStatus.first()

        assertEquals(SyncDisplayStatus.Error("Network timeout"), status)
    }

    @Test
    fun `going offline during sync shows Offline`() = runTest {
        fakeConnectivity.setOnline(true)
        syncStatusHolder.emitStatus(SyncStatus.Syncing)

        val viewModel = createViewModel()
        assertEquals(SyncDisplayStatus.Syncing, viewModel.displayStatus.first())

        fakeConnectivity.setOnline(false)
        assertEquals(SyncDisplayStatus.Offline, viewModel.displayStatus.first())
    }

    @Test
    fun `coming back online shows underlying sync status`() = runTest {
        fakeConnectivity.setOnline(false)
        syncStatusHolder.emitStatus(SyncStatus.Error("Connection lost"))

        val viewModel = createViewModel()
        assertEquals(SyncDisplayStatus.Offline, viewModel.displayStatus.first())

        fakeConnectivity.setOnline(true)
        assertEquals(SyncDisplayStatus.Error("Connection lost"), viewModel.displayStatus.first())
    }
}

/**
 * Fake [ConnectivityObserver] that exposes a mutable [isOnline] flow for tests.
 */
private class FakeConnectivityObserver : ConnectivityObserver {
    private val _isOnline = MutableStateFlow(true)
    override val isOnline: StateFlow<Boolean> get() = _isOnline

    fun setOnline(online: Boolean) {
        _isOnline.value = online
    }
}
