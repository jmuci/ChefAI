package com.tenmilelabs.chefai.core.di

import android.content.Context
import com.tenmilelabs.chefai.BuildConfig
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
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

        val appFilesDir = context.filesDir.path
        return ImageLoader.Builder(context)
            .components {
                // Add OkHttp network engine for image loading
                add(OkHttpNetworkFetcherFactory())
                // Image URLs arrive over sync from any author (PUBLIC recipes), unvalidated. Only
                // https and this app's own files are loaded; see isAllowedImageModel.
                add(object : Interceptor {
                    override suspend fun intercept(chain: Interceptor.Chain): coil3.request.ImageResult {
                        val data = chain.request.data
                        if (!isAllowedImageModel(data, appFilesDir, allowCleartext = BuildConfig.DEBUG)) {
                            throw SecurityException("Blocked image source: ${data.toString().substringBefore(':')}")
                        }
                        return chain.proceed()
                    }
                })
                add(object : Interceptor {
                    override suspend fun intercept(chain: Interceptor.Chain): coil3.request.ImageResult {
                        val request = chain.request
                        val data = request.data
                        val dataType = data?.javaClass?.simpleName ?: "null"
                        val dataValue = data?.toString() ?: "null"

                        Timber.tag("ImageLoader_DEBUG").d(
                            "📸 INTERCEPTOR >> Attempting to load image:\n" +
                                    "  Data Type: $dataType\n" +
                                    "  Data Value: $dataValue\n" +
                                    "  Data is null: ${data == null}\n" +
                                    "  Data toString empty: ${dataValue.isEmpty()}"
                        )

                        return try {
                            val result = chain.proceed()
                            Timber.tag("ImageLoader_DEBUG").d("✅ INTERCEPTOR >> Load succeeded for: $dataValue")
                            result
                        } catch (e: Exception) {
                            Timber.tag("ImageLoader_DEBUG").e(
                                e,
                                "❌ INTERCEPTOR >> Load failed for data:\n" +
                                        "  Type: $dataType\n" +
                                        "  Value: $dataValue\n" +
                                        "  Error: ${e.message}"
                            )
                            throw e
                        }
                    }
                })
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

/**
 * Which Coil models the app loads (security review A7).
 *
 * String models are allowed only as `https://` URLs (plus `http://` when [allowCleartext], i.e. a
 * debug build talking to a local backend), or as files inside [appFilesDir], with or without a
 * `file://` scheme. Recipe image URLs come over sync from whoever authored the recipe, so without
 * this a server-supplied `content://`, `android.resource://` or `file:///sdcard/...` value would
 * be handed straight to Coil's local fetchers. Paths are normalised first, so `..` can't climb out
 * of [appFilesDir].
 *
 * Non-string models (`Uri`, `File`, drawables) are only ever built by the app itself and pass.
 */
internal fun isAllowedImageModel(data: Any?, appFilesDir: String, allowCleartext: Boolean): Boolean {
    if (data !is String) return true
    val lower = data.lowercase()
    if (lower.startsWith("https://")) return true
    if (lower.startsWith("http://")) return allowCleartext
    val path = when {
        lower.startsWith("file://") -> data.substring("file://".length)
        data.startsWith("/") -> data
        else -> return false
    }
    val normalized = runCatching { java.nio.file.Paths.get(path).normalize().toString() }.getOrNull()
        ?: return false
    return normalized.startsWith(appFilesDir.trimEnd('/') + "/")
}
