package com.tenmilelabs.chefai.core.di

import com.tenmilelabs.chefai.auth.data.network.AuthApiService
import com.tenmilelabs.chefai.auth.data.network.AuthNetworkDataSource
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.TokenProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for providing authentication-related dependencies.
 *
 * Note: ApplicationScope and its CoroutineScope provider are defined in CoroutinesModule.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    /**
     * Binds SessionManager as the implementation of TokenProvider.
     * This breaks the circular dependency between SessionManager and HttpClient.
     */
    @Binds
    @Singleton
    abstract fun bindTokenProvider(sessionManager: SessionManager): TokenProvider

    /**
     * Binds AuthApiService as the implementation of AuthNetworkDataSource.
     */
    @Binds
    @Singleton
    abstract fun bindAuthNetworkDataSource(
        authApiService: AuthApiService
    ): AuthNetworkDataSource
}
