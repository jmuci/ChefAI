package com.tenmilelabs.chefai

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber
import timber.log.Timber.DebugTree
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Inject

/**
 * Custom Application class for ChefAI.
 * Sets up Timber logging and provides Coil ImageLoader via Hilt DI.
 */
@HiltAndroidApp
class ChefAIApplication : Application(), SingletonImageLoader.Factory {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ImageLoaderEntryPoint {
        fun imageLoader(): ImageLoader
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize Timber for debug logging
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
    }

    /**
     * Returns the ImageLoader provided by Hilt DI.
     * Uses EntryPoint to ensure the ImageLoader is available even before field injection.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val entryPoint = EntryPointAccessors.fromApplication(
            this, // Use 'this' Application instance, not the context parameter
            ImageLoaderEntryPoint::class.java
        )
        val loader = entryPoint.imageLoader()
        Timber.d("Providing ImageLoader to Coil: $loader")
        return loader
    }
}