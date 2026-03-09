package com.tenmilelabs.chefai.home.di

import android.content.Context
import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.home.data.cache.LayoutCacheDataSource
import com.tenmilelabs.chefai.home.data.repository.DefaultHomeLayoutRepository
import com.tenmilelabs.chefai.home.domain.repository.HomeLayoutRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {

    @Binds
    @Singleton
    abstract fun bindHomeLayoutRepository(
        impl: DefaultHomeLayoutRepository,
    ): HomeLayoutRepository

    companion object {
        @Provides
        @Singleton
        fun provideLayoutCacheDataSource(
            @ApplicationContext context: Context,
            json: Json,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): LayoutCacheDataSource = LayoutCacheDataSource(context, json, ioDispatcher)
    }
}
