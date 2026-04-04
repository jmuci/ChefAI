package com.tenmilelabs.chefai.core.di

import com.tenmilelabs.chefai.core.data.sync.ConnectivityObserver
import com.tenmilelabs.chefai.core.data.sync.NetworkMonitor
import com.tenmilelabs.chefai.core.data.sync.SyncExecutor
import com.tenmilelabs.chefai.core.data.sync.SyncManager
import com.tenmilelabs.chefai.core.data.sync.SyncOrchestrator
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.core.data.sync.network.SyncApiService
import com.tenmilelabs.chefai.core.data.sync.network.SyncNetworkDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncNetworkDataSourceModule {

    @Binds
    @Singleton
    abstract fun bindSyncNetworkDataSource(
        syncApiService: SyncApiService
    ): SyncNetworkDataSource

    @Binds
    @Singleton
    abstract fun bindSyncScheduler(
        syncManager: SyncManager
    ): SyncScheduler

    @Binds
    @Singleton
    abstract fun bindSyncExecutor(
        syncOrchestrator: SyncOrchestrator
    ): SyncExecutor

    @Binds
    @Singleton
    abstract fun bindConnectivityObserver(
        networkMonitor: NetworkMonitor
    ): ConnectivityObserver
}
