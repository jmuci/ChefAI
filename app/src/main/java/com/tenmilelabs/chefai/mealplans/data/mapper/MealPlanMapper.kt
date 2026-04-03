package com.tenmilelabs.chefai.mealplans.data.mapper

import com.tenmilelabs.chefai.core.data.local.room.MealPlanDayEntity
import com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun MealPlanEntity.toDomain(days: List<MealPlanDayEntity>): MealPlan = MealPlan(
    uuid = uuid,
    userId = userId,
    name = name,
    preferences = json.decodeFromString<MealPlanPreferences>(preferencesJson),
    status = MealPlanStatus.valueOf(status),
    createdAt = createdAt,
    updatedAt = updatedAt,
    days = days.map { it.toDomain() },
)

fun MealPlanDayEntity.toDomain(): MealPlanDay = MealPlanDay(
    uuid = uuid,
    dayIndex = dayIndex,
    dinnerRecipeId = dinnerRecipeId,
    lunchRecipeId = lunchRecipeId,
)

fun MealPlan.toEntity(): MealPlanEntity = MealPlanEntity(
    uuid = uuid,
    userId = userId,
    name = name,
    status = status.name,
    preferencesJson = json.encodeToString(MealPlanPreferences.serializer(), preferences),
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null,
)

fun MealPlanDay.toEntity(mealPlanId: java.util.UUID): MealPlanDayEntity = MealPlanDayEntity(
    uuid = uuid,
    mealPlanId = mealPlanId,
    dayIndex = dayIndex,
    dinnerRecipeId = dinnerRecipeId,
    lunchRecipeId = lunchRecipeId,
)
