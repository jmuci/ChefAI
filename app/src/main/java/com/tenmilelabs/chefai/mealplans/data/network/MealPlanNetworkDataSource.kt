package com.tenmilelabs.chefai.mealplans.data.network

import com.tenmilelabs.chefai.core.data.sync.network.dto.GenerateMealPlanResponse

interface MealPlanNetworkDataSource {
    suspend fun generateMealPlan(mealPlanId: String): GenerateMealPlanResponse
}
