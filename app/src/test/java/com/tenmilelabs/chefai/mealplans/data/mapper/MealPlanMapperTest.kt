package com.tenmilelabs.chefai.mealplans.data.mapper

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.room.MealPlanDayEntity
import com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import kotlinx.serialization.json.Json
import org.junit.Test
import java.util.UUID

class MealPlanMapperTest {

    private val preferences = MealPlanPreferences(
        planLengthDays = 7,
        mealType = MealType.DINNER,
        dietaryRestrictions = setOf(DietaryRestriction.VEGETARIAN),
        recipeSource = RecipeSource.COLLECTION_ONLY,
        maxPrepTimeMinutes = 30,
        servingsPerMeal = 2,
        batchCooking = false,
        leftoverFriendly = true,
        varietyPreference = VarietyPreference.HIGH,
    )

    private fun mealPlan(days: List<MealPlanDay> = emptyList()) = MealPlan(
        uuid = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        name = "This week",
        preferences = preferences,
        status = MealPlanStatus.READY,
        createdAt = 1_000L,
        updatedAt = 2_000L,
        days = days,
    )

    private fun mealPlanDay() = MealPlanDay(
        uuid = UUID.randomUUID(),
        dayIndex = 3,
        dinnerRecipeId = UUID.randomUUID(),
        lunchRecipeId = UUID.randomUUID(),
        dinnerCookedAt = 5_000L,
        lunchCookedAt = null,
    )

    @Test
    fun `MealPlan to entity round-trips through toDomain unchanged`() {
        val plan = mealPlan()

        val entity = plan.toEntity()
        val restored = entity.toDomain(days = emptyList())

        assertThat(restored.uuid).isEqualTo(plan.uuid)
        assertThat(restored.userId).isEqualTo(plan.userId)
        assertThat(restored.name).isEqualTo(plan.name)
        assertThat(restored.status).isEqualTo(plan.status)
        assertThat(restored.preferences).isEqualTo(plan.preferences)
        assertThat(restored.createdAt).isEqualTo(plan.createdAt)
        assertThat(restored.updatedAt).isEqualTo(plan.updatedAt)
    }

    @Test
    fun `entity mapping serializes preferences as json and defaults deletedAt to null`() {
        val plan = mealPlan()

        val entity = plan.toEntity()

        assertThat(entity.deletedAt).isNull()
        assertThat(entity.status).isEqualTo(plan.status.name)
        assertThat(Json.decodeFromString<MealPlanPreferences>(entity.preferencesJson))
            .isEqualTo(plan.preferences)
    }

    @Test
    fun `MealPlanDay to entity round-trips through toDomain unchanged`() {
        val day = mealPlanDay()
        val mealPlanId = UUID.randomUUID()

        val entity = day.toEntity(mealPlanId)
        val restored = entity.toDomain()

        assertThat(entity.mealPlanId).isEqualTo(mealPlanId)
        assertThat(restored).isEqualTo(day)
    }

    @Test
    fun `MealPlanEntity toDomain wires in the supplied days in order`() {
        val entity = mealPlan().toEntity()
        val dayEntities = listOf(
            mealPlanDay().toEntity(entity.uuid),
            mealPlanDay().toEntity(entity.uuid),
        )

        val restored = entity.toDomain(dayEntities)

        assertThat(restored.days).containsExactlyElementsIn(dayEntities.map { it.toDomain() }).inOrder()
    }

    @Test
    fun `a day entity with no cooked timestamps maps to nulls, not zero`() {
        val entity = MealPlanDayEntity(
            uuid = UUID.randomUUID(),
            mealPlanId = UUID.randomUUID(),
            dayIndex = 0,
            dinnerRecipeId = null,
            lunchRecipeId = null,
        )

        val domain = entity.toDomain()

        assertThat(domain.dinnerCookedAt).isNull()
        assertThat(domain.lunchCookedAt).isNull()
        assertThat(domain.dinnerRecipeId).isNull()
        assertThat(domain.lunchRecipeId).isNull()
    }

    @Test
    fun `ignoreUnknownKeys tolerates a newer server adding a preferences field`() {
        val entity = MealPlanEntity(
            uuid = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            name = "Legacy",
            status = MealPlanStatus.READY.name,
            preferencesJson = """
                {
                  "planLengthDays": 5,
                  "mealType": "DINNER",
                  "dietaryRestrictions": [],
                  "recipeSource": "COLLECTION_ONLY",
                  "maxPrepTimeMinutes": null,
                  "servingsPerMeal": 2,
                  "batchCooking": false,
                  "leftoverFriendly": false,
                  "varietyPreference": "HIGH",
                  "somethingFromTheFuture": "ignored"
                }
            """.trimIndent(),
            createdAt = 0L,
            updatedAt = 0L,
            deletedAt = null,
        )

        val restored = entity.toDomain(days = emptyList())

        assertThat(restored.preferences.planLengthDays).isEqualTo(5)
    }
}
