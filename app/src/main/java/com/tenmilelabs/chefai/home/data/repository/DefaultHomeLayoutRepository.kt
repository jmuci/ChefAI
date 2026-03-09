package com.tenmilelabs.chefai.home.data.repository

import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.home.data.cache.LayoutCacheDataSource
import com.tenmilelabs.chefai.home.domain.repository.HomeLayoutRepository
import com.tenmilelabs.chefai.home.data.model.HomeLayoutModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject

class DefaultHomeLayoutRepository @Inject constructor(
    private val cacheDataSource: LayoutCacheDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HomeLayoutRepository {

    override fun getHomeLayout(): Flow<HomeLayoutModel> = flow {
        // 1. Try disk cache first (stale-while-revalidate)
        val cached = cacheDataSource.readCachedLayout()
        if (cached != null) {
            emit(cached)
        }

        // 2. Network fetch — revalidate against cached checksum
        // TODO: Wire network data source when backend is ready
        // val networkJson = networkDataSource.fetchLayout()
        // if (networkJson != null) {
        //     val networkLayout = json.decodeFromString<HomeLayoutModel>(networkJson)
        //     if (networkLayout.layoutChecksum != cached?.layoutChecksum) {
        //         cacheDataSource.writeCachedLayout(networkJson)
        //         emit(networkLayout)
        //     }
        // }

        // 3. If no cache was found, fall back to the bundled asset
        if (cached == null) {
            emit(cacheDataSource.readBundledLayout())
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        Timber.e(e, "Error loading home layout, falling back to bundled asset")
        emit(cacheDataSource.readBundledLayout())
    }.flowOn(ioDispatcher)
}
