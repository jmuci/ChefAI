package com.tenmilelabs.chefai.mealplans.data.network

import com.tenmilelabs.chefai.core.data.sync.network.dto.GenerateMealPlanResponse
import com.tenmilelabs.chefai.core.data.sync.network.dto.GenerateMealPlanStatelessResponseDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncReferenceDataDto

/** Configurable fake [MealPlanNetworkDataSource] for testing. */
class FakeMealPlanNetworkDataSource : MealPlanNetworkDataSource {

    /** Meal plan ids passed to [generateMealPlan], in call order. */
    val requestedMealPlanIds = mutableListOf<String>()

    /** Response to return from [generateMealPlan]. Auto-derived from the request when null. */
    var response: GenerateMealPlanResponse? = null

    /** When non-null, [generateMealPlan] throws this instead of returning. */
    var exception: Exception? = null

    /** preferencesJson passed to [generateStateless], in call order. */
    val statelessRequestedPreferences = mutableListOf<String>()

    /** Result returned by [generateStateless]. Defaults to an empty but well-formed success. */
    var statelessResult: GenerateStatelessResult = GenerateStatelessResult.Success(
        GenerateMealPlanStatelessResponseDto(
            days = emptyList(),
            recipes = emptyList(),
            referenceData = SyncReferenceDataDto(),
            creators = emptyList(),
        )
    )

    override suspend fun generateMealPlan(mealPlanId: String): GenerateMealPlanResponse {
        requestedMealPlanIds.add(mealPlanId)
        exception?.let { throw it }
        return response ?: GenerateMealPlanResponse(
            uuid = mealPlanId,
            status = "GENERATING",
            updatedAt = 0L,
        )
    }

    override suspend fun generateStateless(preferencesJson: String): GenerateStatelessResult {
        statelessRequestedPreferences.add(preferencesJson)
        return statelessResult
    }
}
