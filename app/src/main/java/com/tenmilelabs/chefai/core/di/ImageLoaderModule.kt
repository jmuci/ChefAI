package com.tenmilelabs.chefai.core.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okio.Path.Companion.toOkioPath
import timber.log.Timber
import javax.inject.Singleton

/**
 * Hilt module for providing Coil ImageLoader.
 * Using DI makes it easier to provide fake ImageLoaders in tests.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    /**
     * Provides a singleton ImageLoader for the entire app.
     * Configured with OkHttp for network requests, memory and disk caching.
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context
    ): ImageLoader {
        // Custom logger to debug image loading
        val logger = object : coil3.util.Logger {
            override var minLevel: coil3.util.Logger.Level = coil3.util.Logger.Level.Debug

            override fun log(
                tag: String,
                level: coil3.util.Logger.Level,
                message: String?,
                throwable: Throwable?
            ) {
                when (level) {
                    coil3.util.Logger.Level.Verbose -> Timber.tag(tag).v(throwable, message ?: "")
                    coil3.util.Logger.Level.Debug -> Timber.tag(tag).d(throwable, message ?: "")
                    coil3.util.Logger.Level.Info -> Timber.tag(tag).i(throwable, message ?: "")
                    coil3.util.Logger.Level.Warn -> Timber.tag(tag).w(throwable, message ?: "")
                    coil3.util.Logger.Level.Error -> Timber.tag(tag).e(throwable, message ?: "")
                }
            }
        }

        return ImageLoader.Builder(context)
            .components {
                // Add OkHttp network engine for image loading
                add(OkHttpNetworkFetcherFactory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, percent = 0.25) // Use 25% of available RAM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizePercent(0.03) // 3% of disk space
                    .build()
            }
            .logger(logger) // Enable detailed logging
            .crossfade(true) // Enable crossfade animation
            .build()
            .also { Timber.d("ImageLoader created via DI with logging enabled: $it") }
    }
}
