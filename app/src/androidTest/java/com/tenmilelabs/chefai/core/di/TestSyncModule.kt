package com.tenmilelabs.chefai.core.di

import android.content.Context
import com.tenmilelabs.chefai.core.data.sync.ConnectivityObserver
import com.tenmilelabs.chefai.core.data.sync.FakeSyncNetworkDataSource
import com.tenmilelabs.chefai.core.data.sync.FakeSyncScheduler
import com.tenmilelabs.chefai.core.data.sync.NetworkMonitor
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.core.data.sync.network.SyncNetworkDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces [SyncNetworkDataSourceModule] in instrumented tests.
 *
 * - [SyncScheduler] → [FakeSyncScheduler]: no-op; prevents WorkManager from being
 *   called. WorkManager requires on-demand initialisation that isn't set up by the
 *   Hilt test application.
 * - [SyncNetworkDataSource] → [FakeSyncNetworkDataSource]: no-op; returns empty
 *   successful responses without touching the network.
 * - [ConnectivityObserver] → [NetworkMonitor]: kept real; it only reads
 *   ConnectivityManager which is always available on device.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [SyncNetworkDataSourceModule::class]
)
object TestSyncModule {

    @Provides
    @Singleton
    fun provideFakeSyncScheduler(): SyncScheduler = FakeSyncScheduler()

    @Provides
    @Singleton
    fun provideFakeSyncNetworkDataSource(): SyncNetworkDataSource = FakeSyncNetworkDataSource()

    @Provides
    @Singleton
    fun provideConnectivityObserver(
        @ApplicationContext context: Context
    ): ConnectivityObserver = NetworkMonitor(context)
}
