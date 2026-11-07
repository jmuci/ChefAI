package com.tenmilelabs.chefai.mealplans.data.mapper

import com.tenmilelabs.chefai.core.data.local.room.SavedMealPlanEntity
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.SavedMealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.SavedMealPlanDay
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true }

/** Storage shape of one [SavedMealPlanDay]; ids are strings since `UUID` isn't serializable. */
@Serializable
private data class SavedDayJson(
    val dayIndex: Int,
    val lunchRecipeId: String? = null,
    val dinnerRecipeId: String? = null,
)

/** Encodes the lineup for `saved_meal_plans.daysJson`. */
fun List<SavedMealPlanDay>.toDaysJson(): String =
    json.encodeToString(
        ListSerializer(SavedDayJson.serializer()),
        map { SavedDayJson(it.dayIndex, it.lunchRecipeId?.toString(), it.dinnerRecipeId?.toString()) },
    )

/** Decodes `saved_meal_plans.daysJson`, ordered by day index. */
fun String.toSavedMealPlanDays(): List<SavedMealPlanDay> =
    json.decodeFromString(ListSerializer(SavedDayJson.serializer()), this)
        .map {
            SavedMealPlanDay(
                dayIndex = it.dayIndex,
                lunchRecipeId = it.lunchRecipeId?.let(UUID::fromString),
                dinnerRecipeId = it.dinnerRecipeId?.let(UUID::fromString),
            )
        }
        .sortedBy { it.dayIndex }

fun SavedMealPlanEntity.toDomain(): SavedMealPlan = SavedMealPlan(
    uuid = uuid,
    userId = userId,
    title = title,
    heroImagePath = heroImagePath,
    preferences = json.decodeFromString<MealPlanPreferences>(preferencesJson),
    days = daysJson.toSavedMealPlanDays(),
    sourceMealPlanId = sourceMealPlanId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Maps to a live (not deleted) `PENDING` row; callers set the sync state they need. */
fun SavedMealPlan.toEntity(): SavedMealPlanEntity = SavedMealPlanEntity(
    uuid = uuid,
    userId = userId,
    title = title,
    preferencesJson = preferences.toJson(),
    daysJson = days.toDaysJson(),
    sourceMealPlanId = sourceMealPlanId,
    heroImagePath = heroImagePath,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
