package com.tenmilelabs.chefai.mealplans.domain.model

import java.util.UUID

data class MealPlanDay(
    val uuid: UUID,
    val dayIndex: Int,
    val dinnerRecipeId: UUID?,
    val lunchRecipeId: UUID?,
)
