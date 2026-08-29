package com.tenmilelabs.chefai.mealplans.data.network

import com.tenmilelabs.chefai.BuildConfig
import com.tenmilelabs.chefai.core.data.sync.network.dto.GenerateMealPlanResponse
import com.tenmilelabs.chefai.core.data.sync.network.dto.GenerateMealPlanStatelessRequest
import com.tenmilelabs.chefai.core.data.sync.network.dto.GenerateMealPlanStatelessResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of a stateless generation call — never thrown. Modeled on
 * [com.tenmilelabs.chefai.recipes.data.network.RecipeDetailNetworkResult]: this endpoint is called
 * for anonymous sessions as part of the normal create/regenerate flow, not as an edge case, so
 * callers branch on the result rather than catching an exception.
 */
sealed interface GenerateStatelessResult {
    data class Success(val response: GenerateMealPlanStatelessResponseDto) : GenerateStatelessResult
    data class Error(val message: String) : GenerateStatelessResult
}

@Singleton
class MealPlanApiService @Inject constructor(
    private val client: HttpClient
) : MealPlanNetworkDataSource {

    companion object {
        private val BASE_URL = BuildConfig.API_BASE_URL
        private val GENERATE_STATELESS_ENDPOINT = "$BASE_URL/api/v1/meal-plans/generate"

        /**
         * Generation does real work server-side (candidate scan + ranking + up to 14 sequential
         * per-recipe aggregate fetches) rather than a single indexed lookup, so this budgets more
         * than [com.tenmilelabs.chefai.recipes.data.network.RecipeDetailApiService]'s 5s. Both
         * `requestTimeoutMillis` and `socketTimeoutMillis` need raising: Ktor's per-request
         * `timeout {}` block only overrides the fields it sets, so leaving `socketTimeoutMillis`
         * alone would still cap the wait for the first response byte at the client's 10s default
         * (see NetworkModule.kt) even though `requestTimeoutMillis` claims a 20s budget.
         */
        private const val GENERATE_STATELESS_TIMEOUT_MILLIS = 20_000L
    }

    override suspend fun generateMealPlan(mealPlanId: String): GenerateMealPlanResponse {
        val httpResponse = client.post("$BASE_URL/meal-plans/$mealPlanId/generate") {
            contentType(ContentType.Application.Json)
            expectSuccess = false
        }

        if (!httpResponse.status.isSuccess()) {
            throw MealPlanApiException(
                message = "Generate meal plan failed: ${httpResponse.status}",
                statusCode = httpResponse.status.value
            )
        }

        return httpResponse.body()
    }

    override suspend fun generateStateless(preferencesJson: String): GenerateStatelessResult {
        return try {
            val httpResponse = client.post(GENERATE_STATELESS_ENDPOINT) {
                contentType(ContentType.Application.Json)
                setBody(GenerateMealPlanStatelessRequest(preferencesJson))
                expectSuccess = false
                timeout {
                    requestTimeoutMillis = GENERATE_STATELESS_TIMEOUT_MILLIS
                    socketTimeoutMillis = GENERATE_STATELESS_TIMEOUT_MILLIS
                }
            }

            if (httpResponse.status.isSuccess()) {
                GenerateStatelessResult.Success(httpResponse.body())
            } else {
                GenerateStatelessResult.Error("Stateless generation failed: ${httpResponse.status}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GenerateStatelessResult.Error(e.message ?: "Unknown network error")
        }
    }
}

data class MealPlanApiException(
    override val message: String,
    val statusCode: Int
) : Exception(message)
