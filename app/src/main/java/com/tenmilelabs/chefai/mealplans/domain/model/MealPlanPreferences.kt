package com.tenmilelabs.chefai.mealplans.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MealPlanPreferences(
    val planLengthDays: Int,
    val mealType: MealType,
    val dietaryRestrictions: Set<DietaryRestriction>,
    val recipeSource: RecipeSource,
    val maxPrepTimeMinutes: Int?,
    val servingsPerMeal: Int,
    val batchCooking: Boolean,
    val leftoverFriendly: Boolean,
    val varietyPreference: VarietyPreference,
)
