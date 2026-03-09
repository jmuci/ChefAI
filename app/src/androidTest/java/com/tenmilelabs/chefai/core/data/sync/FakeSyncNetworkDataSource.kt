package com.tenmilelabs.chefai.core.data.sync

import com.tenmilelabs.chefai.core.data.sync.network.SyncNetworkDataSource
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPullResponse
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPushRequest
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPushResponse

/**
 * No-op [SyncNetworkDataSource] for instrumented tests.
 * Returns empty successful responses without touching the network.
 */
class FakeSyncNetworkDataSource : SyncNetworkDataSource {
    override suspend fun pushRecipes(request: SyncPushRequest): SyncPushResponse =
        SyncPushResponse(
            accepted = emptyList(),
            conflicts = emptyList(),
            errors = emptyList(),
            serverTimestamp = System.currentTimeMillis()
        )

    override suspend fun pullRecipes(since: Long, limit: Int): SyncPullResponse =
        SyncPullResponse(
            recipes = emptyList(),
            serverTimestamp = System.currentTimeMillis(),
            hasMore = false
        )
}
