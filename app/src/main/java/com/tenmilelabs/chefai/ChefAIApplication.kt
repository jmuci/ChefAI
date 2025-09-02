package com.tenmilelabs.chefai

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class ChefAIApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber DebugTree planted.")
        } else {
            // Optional: Plant a release tree that logs to a crash reporting service
            // like Firebase Crashlytics, Sentry, etc.
            // Timber.plant(CrashReportingTree()) // Example, you'd need to create this class
            Timber.d("Timber Release mode: No DebugTree planted. Consider a CrashReportingTree.")
        }
    }

}

/**
 * TODO Set up crash reporting service integration for release builds.
 * Optional: A custom tree for release builds that logs to a crash reporting service.
 * This is just an example. You'd integrate this with your specific crash reporter.
 */
// class CrashReportingTree : Timber.Tree() {
//    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
//        if (priority == Log.VERBOSE || priority == Log.DEBUG) {
//            return // Don't log verbose or debug in release
//        }
//
//        // Example with Firebase Crashlytics
//        // val crashlytics = FirebaseCrashlytics.getInstance()
//        // crashlytics.log(message)
//        // t?.let { crashlytics.recordException(it) }
//
//        // For other services, adapt accordingly
//    }
// }