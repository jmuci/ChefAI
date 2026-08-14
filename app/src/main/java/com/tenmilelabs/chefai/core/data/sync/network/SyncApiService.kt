package com.tenmilelabs.chefai.core.data.sync.network

import com.tenmilelabs.chefai.BuildConfig
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPullResponse
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPushRequest
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncPushResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncApiService @Inject constructor(
    private val client: HttpClient
) : SyncNetworkDataSource {

    companion object {
        private val SYNC_BASE_URL = BuildConfig.API_BASE_URL
        private val PUSH_ENDPOINT = "$SYNC_BASE_URL/sync/push"
        private val PULL_ENDPOINT = "$SYNC_BASE_URL/sync/pull"
    }

    override suspend fun pushRecipes(request: SyncPushRequest): SyncPushResponse {
        val httpResponse = client.post(PUSH_ENDPOINT) {
            contentType(ContentType.Application.Json)
            setBody(request)
            expectSuccess = false
        }

        if (!httpResponse.status.isSuccess()) {
            throw SyncHttpException(
                message = "Push failed: ${httpResponse.status}",
                statusCode = httpResponse.status.value
            )
        }

        return httpResponse.body()
    }

    override suspend fun pullRecipes(since: Long, limit: Int): SyncPullResponse {
        val httpResponse = client.get(PULL_ENDPOINT) {
            parameter("since", since)
            parameter("limit", limit)
            expectSuccess = false
        }

        if (!httpResponse.status.isSuccess()) {
            throw SyncHttpException(
                message = "Pull failed: ${httpResponse.status}",
                statusCode = httpResponse.status.value
            )
        }

        return httpResponse.body()
    }
}

data class SyncHttpException(
    override val message: String,
    val statusCode: Int
) : Exception(message)
