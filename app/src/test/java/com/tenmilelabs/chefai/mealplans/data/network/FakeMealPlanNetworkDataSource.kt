package com.tenmilelabs.chefai.mealplans.data.network

import com.tenmilelabs.chefai.core.data.sync.network.dto.GenerateMealPlanResponse

/** Configurable fake [MealPlanNetworkDataSource] for testing. */
class FakeMealPlanNetworkDataSource : MealPlanNetworkDataSource {

    /** Meal plan ids passed to [generateMealPlan], in call order. */
    val requestedMealPlanIds = mutableListOf<String>()

    /** Response to return from [generateMealPlan]. Auto-derived from the request when null. */
    var response: GenerateMealPlanResponse? = null

    /** When non-null, [generateMealPlan] throws this instead of returning. */
    var exception: Exception? = null

    override suspend fun generateMealPlan(mealPlanId: String): GenerateMealPlanResponse {
        requestedMealPlanIds.add(mealPlanId)
        exception?.let { throw it }
        return response ?: GenerateMealPlanResponse(
            uuid = mealPlanId,
            status = "GENERATING",
            updatedAt = 0L,
        )
    }
}
