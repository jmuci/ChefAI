package com.tenmilelabs.chefai.mealplans.domain.week

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Test

class MealPlanStartDateTest {

    private val zone: ZoneId = ZoneOffset.UTC

    // Monday 2026-10-26.
    private val monday: LocalDate = LocalDate.of(2026, 10, 26)
    private val week = monday..monday.plusDays(6)

    @Test
    fun `nothing planned defaults to today`() {
        assertThat(defaultMealPlanStartDate(emptyList(), today = monday, zone = zone)).isEqualTo(monday)
    }

    @Test
    fun `defaults to the day after the last planned meal`() {
        // Given a meal on the 28th
        val plan = plan(start = monday.plusDays(2), plannedDays = listOf(0))

        // Then a new plan defaults to the 29th
        assertThat(defaultMealPlanStartDate(listOf(plan), today = monday, zone = zone))
            .isEqualTo(monday.plusDays(3))
    }

    @Test
    fun `week view skips gaps before the last planned day of that week`() {
        // Given Monday and Wednesday planned, Tuesday empty
        val plan = plan(start = monday, plannedDays = listOf(0, 2))

        // Then the default is Thursday, not Tuesday
        assertThat(defaultMealPlanStartDate(listOf(plan), today = monday, zone = zone, week = week))
            .isEqualTo(monday.plusDays(3))
    }

    @Test
    fun `week view ignores meals planned after that week`() {
        val nextWeek = plan(start = monday.plusDays(9), plannedDays = listOf(0))
        val thisWeek = plan(start = monday, plannedDays = listOf(1))

        assertThat(defaultMealPlanStartDate(listOf(thisWeek, nextWeek), today = monday, zone = zone, week = week))
            .isEqualTo(monday.plusDays(2))
    }

    @Test
    fun `an empty future week defaults to its first day`() {
        val nextWeek = monday.plusDays(7)..monday.plusDays(13)

        assertThat(defaultMealPlanStartDate(emptyList(), today = monday, zone = zone, week = nextWeek))
            .isEqualTo(monday.plusDays(7))
    }

    @Test
    fun `a week already under way never defaults to a past day`() {
        val thursday = monday.plusDays(3)
        val plan = plan(start = monday, plannedDays = listOf(0, 1))

        assertThat(defaultMealPlanStartDate(listOf(plan), today = thursday, zone = zone, week = week))
            .isEqualTo(thursday)
    }

    @Test
    fun `without a week, every future meal counts`() {
        val plan = plan(start = monday.plusDays(10), plannedDays = listOf(0))

        assertThat(defaultMealPlanStartDate(listOf(plan), today = monday, zone = zone))
            .isEqualTo(monday.plusDays(11))
    }

    @Test
    fun `days with no recipe and archived plans do not count`() {
        val emptyDays = plan(start = monday, plannedDays = emptyList(), totalDays = 5)
        val archived = plan(start = monday, plannedDays = listOf(4), status = MealPlanStatus.ARCHIVED)

        assertThat(defaultMealPlanStartDate(listOf(emptyDays, archived), today = monday, zone = zone))
            .isEqualTo(monday)
    }

    @Test
    fun `plans without a start date are read from their creation date`() {
        val legacy = plan(start = null, createdOn = monday.plusDays(1), plannedDays = listOf(0, 1))

        assertThat(defaultMealPlanStartDate(listOf(legacy), today = monday, zone = zone))
            .isEqualTo(monday.plusDays(3))
    }

    // --- Helpers ---

    private fun plan(
        start: LocalDate?,
        plannedDays: List<Int>,
        totalDays: Int = (plannedDays.maxOrNull() ?: 0) + 1,
        createdOn: LocalDate = monday.minusDays(7),
        status: MealPlanStatus = MealPlanStatus.READY,
    ) = MealPlan(
        uuid = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        name = "plan",
        preferences = MealPlanPreferences(
            planLengthDays = totalDays,
            mealType = MealType.DINNER,
            dietaryRestrictions = emptySet(),
            recipeSource = RecipeSource.COLLECTION_ONLY,
            maxPrepTimeMinutes = null,
            servingsPerMeal = 2,
            batchCooking = false,
            leftoverFriendly = false,
            varietyPreference = VarietyPreference.MEDIUM,
        ),
        status = status,
        createdAt = createdOn.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        updatedAt = 0L,
        days = (0 until totalDays).map { index ->
            MealPlanDay(
                uuid = UUID.randomUUID(),
                dayIndex = index,
                dinnerRecipeId = if (index in plannedDays) UUID.randomUUID() else null,
                lunchRecipeId = null,
            )
        },
        startDate = start,
    )
}
