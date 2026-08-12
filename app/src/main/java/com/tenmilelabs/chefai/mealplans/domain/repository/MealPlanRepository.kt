package com.tenmilelabs.chefai.mealplans.domain.repository

import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface MealPlanRepository {

    fun observeMealPlansForUser(userId: UUID): Flow<List<MealPlan>>

    fun observeMealPlan(uuid: UUID): Flow<MealPlan?>

    suspend fun createMealPlan(mealPlan: MealPlan)

    suspend fun deleteMealPlan(uuid: UUID)

    suspend fun requestGeneration(planId: UUID): Result<Unit>
}
