package com.tenmilelabs.chefai.mealplans.data.network

import com.tenmilelabs.chefai.BuildConfig
import com.tenmilelabs.chefai.core.data.sync.network.dto.GenerateMealPlanResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealPlanApiService @Inject constructor(
    private val client: HttpClient
) : MealPlanNetworkDataSource {

    companion object {
        private val BASE_URL = BuildConfig.API_BASE_URL
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
}

data class MealPlanApiException(
    override val message: String,
    val statusCode: Int
) : Exception(message)
