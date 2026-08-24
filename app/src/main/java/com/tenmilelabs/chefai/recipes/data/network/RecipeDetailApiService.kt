package com.tenmilelabs.chefai.recipes.data.network

import com.tenmilelabs.chefai.BuildConfig
import com.tenmilelabs.chefai.core.data.sync.network.dto.RecipeDetailResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of a single-recipe fetch — never thrown. [NotFound] is split out from [Error]
 * because it's an expected, non-transient result (the recipe is private/deleted/never
 * existed): retrying won't help, so callers shouldn't treat it the way they'd treat a
 * network blip. See ChefAI#186.
 */
sealed interface RecipeDetailNetworkResult {
    data class Success(val response: RecipeDetailResponseDto) : RecipeDetailNetworkResult
    data object NotFound : RecipeDetailNetworkResult
    data object Unauthorized : RecipeDetailNetworkResult
    data class Error(val message: String) : RecipeDetailNetworkResult
}

/**
 * Injects the unqualified (authenticated) [HttpClient] — never `@ScraperHttpClient`. Applies
 * its own request timeout, same rationale as [RecipeSearchApiService]: the shared client sets
 * no default `requestTimeoutMillis`, since search/sync/image-upload/this all need different
 * budgets. `AuthInterceptor` attaches `Authorization` only when a token exists, so an
 * anonymous caller's request goes out with none — the backend endpoint is mounted with
 * optional auth specifically to accept that.
 */
@Singleton
class RecipeDetailApiService @Inject constructor(
    private val client: HttpClient
) : RecipeDetailNetworkDataSource {
    override suspend fun fetchRecipe(recipeId: UUID): RecipeDetailNetworkResult {
        return try {
            val httpResponse = client.get("$RECIPE_DETAIL_ENDPOINT/$recipeId") {
                expectSuccess = false
                timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
            }
            when {
                httpResponse.status == HttpStatusCode.NotFound -> RecipeDetailNetworkResult.NotFound
                httpResponse.status == HttpStatusCode.Unauthorized -> RecipeDetailNetworkResult.Unauthorized
                httpResponse.status.isSuccess() -> RecipeDetailNetworkResult.Success(httpResponse.body())
                else -> RecipeDetailNetworkResult.Error("Recipe fetch failed: ${httpResponse.status}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RecipeDetailNetworkResult.Error(e.message ?: "Unknown network error")
        }
    }

    private companion object {
        val RECIPE_DETAIL_ENDPOINT = "${BuildConfig.API_BASE_URL}/api/v1/recipes"
        const val REQUEST_TIMEOUT_MILLIS = 5_000L
    }
}
