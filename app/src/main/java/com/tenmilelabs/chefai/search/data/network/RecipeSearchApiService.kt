package com.tenmilelabs.chefai.search.data.network

import com.tenmilelabs.chefai.BuildConfig
import com.tenmilelabs.chefai.search.data.network.dto.RecipeSearchResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a single search call — never thrown, this fires on every keystroke. */
sealed interface RecipeSearchNetworkResult {
    data class Success(val response: RecipeSearchResponseDto) : RecipeSearchNetworkResult

    /**
     * Distinct from [Error] because the shared client has no automatic 401 refresh outside
     * `SyncWorker` — the repository retries this once via `SessionManager.refreshToken()` rather
     * than treating it as a generic failure.
     */
    data object Unauthorized : RecipeSearchNetworkResult
    data class Error(val message: String) : RecipeSearchNetworkResult
}

/**
 * Injects the unqualified (authenticated) [HttpClient] — never `@ScraperHttpClient`, see gotcha
 * #18. Applies its own tight request timeout: this fires on every keystroke, and the shared
 * client deliberately sets no default `requestTimeoutMillis` (search/sync/image-upload all need
 * different budgets).
 */
@Singleton
class RecipeSearchApiService @Inject constructor(
    private val client: HttpClient
) : RecipeSearchNetworkDataSource {
    override suspend fun search(query: String, limit: Int, offset: Int): RecipeSearchNetworkResult {
        return try {
            val httpResponse = client.get(SEARCH_ENDPOINT) {
                parameter("q", query)
                parameter("limit", limit)
                parameter("offset", offset)
                expectSuccess = false
                timeout { requestTimeoutMillis = SEARCH_REQUEST_TIMEOUT_MILLIS }
            }
            when {
                httpResponse.status == HttpStatusCode.Unauthorized -> RecipeSearchNetworkResult.Unauthorized
                httpResponse.status.isSuccess() -> RecipeSearchNetworkResult.Success(httpResponse.body())
                else -> RecipeSearchNetworkResult.Error("Search failed: ${httpResponse.status}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RecipeSearchNetworkResult.Error(e.message ?: "Unknown network error")
        }
    }

    private companion object {
        val SEARCH_ENDPOINT = "${BuildConfig.API_BASE_URL}/api/v1/recipes/search"
        const val SEARCH_REQUEST_TIMEOUT_MILLIS = 5_000L
    }
}
