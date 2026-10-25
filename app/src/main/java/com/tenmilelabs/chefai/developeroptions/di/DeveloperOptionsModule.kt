package com.tenmilelabs.chefai.developeroptions.di

import com.tenmilelabs.chefai.developeroptions.data.repository.DataStoreApiEnvironmentRepository
import com.tenmilelabs.chefai.developeroptions.domain.repository.ApiEnvironmentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Kept apart from `RepositoryModule` on purpose: the instrumented tests replace that module
 * wholesale, and [NetworkModule][com.tenmilelabs.chefai.core.di.NetworkModule] needs this binding
 * in every graph. See docs/claude/gotchas.md #21.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DeveloperOptionsModule {

    @Singleton
    @Binds
    abstract fun bindApiEnvironmentRepository(
        repository: DataStoreApiEnvironmentRepository
    ): ApiEnvironmentRepository
}
