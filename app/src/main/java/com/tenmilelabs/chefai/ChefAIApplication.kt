package com.tenmilelabs.chefai

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

/**
 * Custom Application class for ChefAI.
 * Sets up Timber logging, Coil ImageLoader, WorkManager via Hilt DI,
 * and foreground sync triggering via ProcessLifecycleOwner.
 */
@HiltAndroidApp
class ChefAIApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var sessionManager: SessionManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

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

        // Trigger immediate sync when app comes to foreground
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    if (sessionManager.userSession.value is UserSession.Authenticated) {
                        Timber.d("App foregrounded — requesting immediate sync")
                        syncScheduler.requestImmediateSync()
                    }
                }
            }
        )
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