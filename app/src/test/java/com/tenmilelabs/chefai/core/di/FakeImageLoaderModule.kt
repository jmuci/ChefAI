package com.tenmilelabs.chefai.core.di

import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.test.FakeImageLoaderEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Test module that replaces the real ImageLoaderModule with a fake implementation.
 * This allows tests to run without making real network requests for images.
 *
 * To use in tests, annotate your test class with:
 * @HiltAndroidTest
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [ImageLoaderModule::class]
)
object FakeImageLoaderModule {

    /**
     * Provides a fake ImageLoader that doesn't make real network requests.
     * Useful for UI tests that need to verify image loading behavior without network calls.
     */
    @OptIn(ExperimentalCoilApi::class)
    @Provides
    @Singleton
    fun provideFakeImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                // FakeImageLoaderEngine returns synthetic images instantly
                add(FakeImageLoaderEngine.Builder().build())
            }
            .build()
    }
}
