package com.tenmilelabs.chefai.core.data.sync.network

import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPullResponse
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPushRequest
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPushResponse

interface SyncNetworkDataSource {
    suspend fun pushRecipes(request: SyncPushRequest): SyncPushResponse
    suspend fun pullRecipes(since: Long, limit: Int = 100): SyncPullResponse
}
