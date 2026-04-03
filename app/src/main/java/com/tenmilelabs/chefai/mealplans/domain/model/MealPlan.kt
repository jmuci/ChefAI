package com.tenmilelabs.chefai.mealplans.domain.model

import java.util.UUID

data class MealPlan(
    val uuid: UUID,
    val userId: UUID,
    val name: String,
    val preferences: MealPlanPreferences,
    val status: MealPlanStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val days: List<MealPlanDay>,
)
