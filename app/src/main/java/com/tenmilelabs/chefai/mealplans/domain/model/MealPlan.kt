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
    /** Null for a personal plan. Non-null means it's shared with a household — see ADR-014. */
    val householdId: UUID? = null,
)
