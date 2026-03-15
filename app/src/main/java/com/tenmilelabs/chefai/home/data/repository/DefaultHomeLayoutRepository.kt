package com.tenmilelabs.chefai.home.data.repository

import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.home.data.cache.LayoutCacheDataSource
import com.tenmilelabs.chefai.home.data.model.ComponentModel
import com.tenmilelabs.chefai.home.data.model.HomeLayoutModel
import com.tenmilelabs.chefai.home.data.network.HomeLayoutNetworkResult
import com.tenmilelabs.chefai.home.data.network.HomeNetworkDataSource
import com.tenmilelabs.chefai.home.domain.repository.HomeLayoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

class DefaultHomeLayoutRepository @Inject constructor(
    private val cacheDataSource: LayoutCacheDataSource,
    private val networkDataSource: HomeNetworkDataSource,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HomeLayoutRepository {

    override fun getHomeLayout(): Flow<HomeLayoutModel> = flow {
        // 1. Try disk cache first (stale-while-revalidate)
        val cached = cacheDataSource.readCachedLayout()
        var emittedFreshLayout = false
        if (cached != null) {
            Timber.d("SDUI: Found cached layout. Checksum: ${cached.layoutChecksum} and ${cached.components.size} components.")
            emit(cached)
        }

        // 2. Revalidate against backend checksum using If-None-Match/ETag
        when (val networkResult = networkDataSource.fetchLayout(ifNoneMatch = cached?.layoutChecksum)) {
            HomeLayoutNetworkResult.NotModified -> { // HTTP 304
                Timber.d("SDUI: BE layout: NOT MODIFIED. BE Result ${networkResult} == Cached Checksum: ${cached?.layoutChecksum} and ${cached?.components?.size ?: 0} components.")
                //Unit
            }
            is HomeLayoutNetworkResult.Modified -> { //HTTP 200
                val networkLayout = json.decodeFromString<HomeLayoutModel>(networkResult.rawJson)
                val networkChecksum = networkLayout.layoutChecksum ?: networkResult.eTag
                // Normalization: It ensures the layoutChecksum is correctly set, using the ETag from the network header if the JSON body doesn't contain a checksum.
                val normalizedLayout = if (networkChecksum != null && networkLayout.layoutChecksum != networkChecksum) {
                    networkLayout.copy(layoutChecksum = networkChecksum)
                } else {
                    networkLayout
                }
                // Validation: It checks shouldUpdate. It updates if there was no cache, or if the new checksum is different from the old one.
                val shouldUpdate = when {
                    cached == null -> true
                    networkChecksum != null -> networkChecksum != cached.layoutChecksum
                    else -> normalizedLayout != cached
                }
                Timber.d("SDUI: shouldUpdate: $shouldUpdate .")
                // Fresh Emission: It emits the new layout to the UI and sets emittedFreshLayout = true.
                if (shouldUpdate) {
                    emit(normalizedLayout)
                    emittedFreshLayout = true
                    val knownCount = normalizedLayout.knownComponentCount()
                    if (knownCount >= MIN_CACHEABLE_KNOWN_COMPONENTS) {
                        Timber.d("SDUI: Update cache and emit refreshed value. Normalized Layout Checksum: ${normalizedLayout.layoutChecksum} and $knownCount known components.")
                        cacheDataSource.writeCachedLayout(json.encodeToString(normalizedLayout))
                    } else {
                        Timber.w("SDUI: Skipping cache write — only $knownCount known components (threshold: $MIN_CACHEABLE_KNOWN_COMPONENTS). BE response may have unrecognized component types.")
                    }
                }
            }
        }

        // 3. If no cache was found and network had no fresh layout, fall back to bundled asset
        if (cached == null && !emittedFreshLayout) {
            Timber.w("SDUI: Falling back to bundled layout!! ")
            emit(cacheDataSource.readBundledLayout())
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        Timber.e(e, "Error loading home layout, falling back to bundled asset")
        emit(cacheDataSource.readBundledLayout())
    }.flowOn(ioDispatcher)

    companion object {
        private const val MIN_CACHEABLE_KNOWN_COMPONENTS = 5
    }
}

/**
 * Counts components that are NOT [ComponentModel.Unknown], including items inside carousels.
 * Used to guard against caching a layout that the client can't render.
 */
private fun HomeLayoutModel.knownComponentCount(): Int = components.sumOf { component ->
    when (component) {
        is ComponentModel.Unknown -> 0
        is ComponentModel.Carousel -> component.items.count { it !is ComponentModel.Unknown }
        else -> 1
    }
}
