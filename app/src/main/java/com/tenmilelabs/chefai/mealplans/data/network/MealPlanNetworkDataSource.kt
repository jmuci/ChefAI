package com.tenmilelabs.chefai.mealplans.data.network

import com.tenmilelabs.chefai.core.data.sync.network.dto.GenerateMealPlanResponse

interface MealPlanNetworkDataSource {
    suspend fun generateMealPlan(mealPlanId: String): GenerateMealPlanResponse

    /**
     * Anonymous-capable stateless generation — see `POST /api/v1/meal-plans/generate`.
     * [preferencesJson] is the same serialized form the client stores in
     * [com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity.preferencesJson] and pushes as
     * [com.tenmilelabs.chefai.core.data.sync.network.dto.SyncMealPlanDto.preferencesJson].
     */
    suspend fun generateStateless(preferencesJson: String): GenerateStatelessResult
}
