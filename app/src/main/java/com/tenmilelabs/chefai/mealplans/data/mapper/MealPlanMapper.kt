package com.tenmilelabs.chefai.mealplans.data.mapper

import com.tenmilelabs.chefai.core.data.local.room.MealPlanDayEntity
import com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncMealPlanDayDto
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import kotlinx.serialization.json.Json
import java.util.UUID

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
    dinnerCookedAt = dinnerCookedAt,
    lunchCookedAt = lunchCookedAt,
)

fun MealPlan.toEntity(): MealPlanEntity = MealPlanEntity(
    uuid = uuid,
    userId = userId,
    name = name,
    status = status.name,
    preferencesJson = preferences.toJson(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null,
)

/**
 * The single encoding of [MealPlanPreferences] to JSON — shared so a push's `preferencesJson`
 * (via [MealPlan.toEntity]) and a stateless-generation request body (see
 * [com.tenmilelabs.chefai.mealplans.data.repository.DefaultMealPlanRepository.generateStatelessAndSave])
 * are structurally guaranteed to match, not just documented to.
 */
fun MealPlanPreferences.toJson(): String = json.encodeToString(MealPlanPreferences.serializer(), this)

/**
 * Maps a stateless-generation day straight to the domain model, bypassing [MealPlanDayEntity] —
 * unlike the pull path (`SyncMealPlanDayDto.toMealPlanDayEntity` in `SyncMapper.kt`), the caller
 * here has no plan row yet to attach an entity to.
 */
fun SyncMealPlanDayDto.toDomain(): MealPlanDay = MealPlanDay(
    uuid = UUID.fromString(uuid),
    dayIndex = dayIndex,
    dinnerRecipeId = dinnerRecipeId?.let { UUID.fromString(it) },
    lunchRecipeId = lunchRecipeId?.let { UUID.fromString(it) },
)

fun MealPlanDay.toEntity(mealPlanId: java.util.UUID): MealPlanDayEntity = MealPlanDayEntity(
    uuid = uuid,
    mealPlanId = mealPlanId,
    dayIndex = dayIndex,
    dinnerRecipeId = dinnerRecipeId,
    lunchRecipeId = lunchRecipeId,
    dinnerCookedAt = dinnerCookedAt,
    lunchCookedAt = lunchCookedAt,
)
