package com.tenmilelabs.chefai.home.data.repository

import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.home.data.cache.LayoutCacheDataSource
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
            emit(cached)
        }

        // 2. Revalidate against backend checksum using If-None-Match/ETag
        when (val networkResult = networkDataSource.fetchLayout(ifNoneMatch = cached?.layoutChecksum)) {
            HomeLayoutNetworkResult.NotModified -> Unit
            is HomeLayoutNetworkResult.Modified -> {
                val networkLayout = json.decodeFromString<HomeLayoutModel>(networkResult.rawJson)
                val networkChecksum = networkLayout.layoutChecksum ?: networkResult.eTag
                val normalizedLayout = if (networkChecksum != null && networkLayout.layoutChecksum != networkChecksum) {
                    networkLayout.copy(layoutChecksum = networkChecksum)
                } else {
                    networkLayout
                }

                val shouldUpdate = when {
                    cached == null -> true
                    networkChecksum != null -> networkChecksum != cached.layoutChecksum
                    else -> normalizedLayout != cached
                }

                if (shouldUpdate) {
                    cacheDataSource.writeCachedLayout(json.encodeToString(normalizedLayout))
                    emit(normalizedLayout)
                    emittedFreshLayout = true
                }
            }
        }

        // 3. If no cache was found and network had no fresh layout, fall back to bundled asset
        if (cached == null && !emittedFreshLayout) {
            emit(cacheDataSource.readBundledLayout())
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        Timber.e(e, "Error loading home layout, falling back to bundled asset")
        emit(cacheDataSource.readBundledLayout())
    }.flowOn(ioDispatcher)
}
