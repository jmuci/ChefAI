package com.tenmilelabs.chefai.home.data.cache

import android.content.Context
import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.home.data.model.HomeLayoutModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class LayoutCacheDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val cacheFile: File
        get() = File(context.filesDir, "home_layout_cache.json")

    /**
     * Reads the cached home layout from disk.
     *
     * @return the deserialized [HomeLayoutModel], or `null` on cache miss or read/parse error.
     */
    suspend fun readCachedLayout(): HomeLayoutModel? = withContext(ioDispatcher) {
        try {
            val file = cacheFile
            if (!file.exists()) return@withContext null
            val rawString = file.readText()
            json.decodeFromString<HomeLayoutModel>(rawString)
        } catch (e: Exception) {
            Timber.w(e, "Failed to read cached home layout")
            null
        }
    }

    /**
     * Atomically writes the raw JSON layout to the cache file.
     *
     * Writes to a `.tmp` file first and then renames, so readers never see a partial write.
     */
    suspend fun writeCachedLayout(rawJson: String): Unit = withContext(ioDispatcher) {
        try {
            val tmpFile = File(context.filesDir, "home_layout_cache.json.tmp")
            tmpFile.writeText(rawJson)
            tmpFile.renameTo(cacheFile)
        } catch (e: Exception) {
            Timber.w(e, "Failed to write cached home layout")
        }
    }

    /**
     * Reads the bundled fallback layout shipped with the APK from `assets/home.json`.
     */
    suspend fun readBundledLayout(): HomeLayoutModel = withContext(ioDispatcher) {
        val rawString = context.assets.open("home.json").bufferedReader().use { it.readText() }
        json.decodeFromString<HomeLayoutModel>(rawString)
    }
}
