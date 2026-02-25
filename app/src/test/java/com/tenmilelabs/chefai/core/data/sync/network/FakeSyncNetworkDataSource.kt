package com.tenmilelabs.chefai.core.data.sync.network

import com.tenmilelabs.chefai.core.data.sync.network.dto.AcceptedEntityDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPullResponse
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPushRequest
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPushResponse
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeDto

/**
 * A configurable fake [SyncNetworkDataSource] for testing.
 *
 * By default, push auto-accepts all recipes and pull returns empty.
 * Configure [pushResponses] and [pullResponses] to control behavior.
 */
class FakeSyncNetworkDataSource : SyncNetworkDataSource {

    /** Captured push requests for assertions. */
    val capturedPushRequests = mutableListOf<SyncPushRequest>()

    /** Captured pull parameters for assertions. */
    val capturedPullCalls = mutableListOf<Pair<Long, Int>>() // (since, limit)

    /**
     * Queue of push responses. When empty, auto-accepts all recipes.
     */
    val pushResponses = ArrayDeque<SyncPushResponse>()

    /**
     * Queue of pull responses. When empty, returns empty response.
     */
    val pullResponses = ArrayDeque<SyncPullResponse>()

    /**
     * If non-null, pushRecipes will throw this exception.
     */
    var pushException: Exception? = null

    /**
     * If non-null, pullRecipes will throw this exception.
     */
    var pullException: Exception? = null

    override suspend fun pushRecipes(request: SyncPushRequest): SyncPushResponse {
        pushException?.let { throw it }
        capturedPushRequests.add(request)

        return if (pushResponses.isNotEmpty()) {
            pushResponses.removeFirst()
        } else {
            // Auto-accept all recipes by default
            val serverTimestamp = System.currentTimeMillis()
            SyncPushResponse(
                accepted = request.recipes.map { AcceptedEntityDto(it.uuid, serverTimestamp) },
                conflicts = emptyList(),
                errors = emptyList(),
                serverTimestamp = serverTimestamp
            )
        }
    }

    override suspend fun pullRecipes(since: Long, limit: Int): SyncPullResponse {
        pullException?.let { throw it }
        capturedPullCalls.add(since to limit)

        return if (pullResponses.isNotEmpty()) {
            pullResponses.removeFirst()
        } else {
            SyncPullResponse(
                recipes = emptyList(),
                serverTimestamp = since,
                hasMore = false
            )
        }
    }
}
