package com.tenmilelabs.chefai.home.data.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

private const val HOME_BASE_URL = "http://10.0.2.2:8080"
private const val HOME_LAYOUT_ENDPOINT = "$HOME_BASE_URL/api/v1/home/layout"

@Singleton
class HomeLayoutApiService @Inject constructor(
    private val client: HttpClient,
) : HomeNetworkDataSource {

    override suspend fun fetchLayout(ifNoneMatch: String?): HomeLayoutNetworkResult {
        val httpResponse = client.get(HOME_LAYOUT_ENDPOINT) {
            if (!ifNoneMatch.isNullOrBlank()) {
                header(HttpHeaders.IfNoneMatch, ifNoneMatch)
            }
            expectSuccess = false
        }

        if (httpResponse.status == HttpStatusCode.NotModified) {
            return HomeLayoutNetworkResult.NotModified
        }

        if (!httpResponse.status.isSuccess()) {
            throw HomeLayoutHttpException(
                message = "Home layout fetch failed: ${httpResponse.status}",
                statusCode = httpResponse.status.value,
            )
        }

        val eTag = normalizeETag(httpResponse.headers[HttpHeaders.ETag])

        return HomeLayoutNetworkResult.Modified(
            rawJson = httpResponse.bodyAsText(),
            eTag = eTag,
        )
    }

    private fun normalizeETag(rawETag: String?): String? {
        if (rawETag.isNullOrBlank()) return null
        return rawETag
            .trim()
            .removePrefix("W/")
            .trim()
            .removeSurrounding("\"")
            .ifBlank { null }
    }
}

data class HomeLayoutHttpException(
    override val message: String,
    val statusCode: Int,
) : Exception(message)
