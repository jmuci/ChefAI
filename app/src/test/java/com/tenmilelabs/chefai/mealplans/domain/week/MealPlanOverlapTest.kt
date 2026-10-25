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

class MealPlanOverlapTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val oct25: LocalDate = LocalDate.of(2026, 10, 25)

    @Test
    fun `dateRange spans planLengthDays from the creation date`() {
        val plan = plan(createdAt = oct25, lengthDays = 3)

        assertThat(MealPlanOverlap.dateRange(plan, zone)).isEqualTo(oct25..oct25.plusDays(2))
    }

    @Test
    fun `dateRange covers generated days beyond the requested length`() {
        val plan = plan(createdAt = oct25, lengthDays = 1, generatedDays = 4)

        assertThat(MealPlanOverlap.dateRange(plan, zone).endInclusive).isEqualTo(oct25.plusDays(3))
    }

    @Test
    fun `a new plan starting inside an existing one overlaps it`() {
        // Given the reported case: a 3-day plan from Oct 25
        val existing = plan(createdAt = oct25, lengthDays = 3)

        // When a 5-day plan also starts Oct 25
        val overlapping = MealPlanOverlap.overlapping(listOf(existing), oct25, lengthDays = 5, zone = zone)

        // Then it overlaps
        assertThat(overlapping).containsExactly(existing)
    }

    @Test
    fun `touching end to start is not an overlap`() {
        val existing = plan(createdAt = oct25, lengthDays = 3) // Oct 25-27

        val overlapping = MealPlanOverlap.overlapping(listOf(existing), oct25.plusDays(3), 5, zone)

        assertThat(overlapping).isEmpty()
    }

    @Test
    fun `the last shared day still counts`() {
        val existing = plan(createdAt = oct25, lengthDays = 3) // Oct 25-27

        val overlapping = MealPlanOverlap.overlapping(listOf(existing), oct25.plusDays(2), 5, zone)

        assertThat(overlapping).containsExactly(existing)
    }

    @Test
    fun `archived plans never overlap`() {
        val archived = plan(createdAt = oct25, lengthDays = 3, status = MealPlanStatus.ARCHIVED)

        assertThat(MealPlanOverlap.overlapping(listOf(archived), oct25, 5, zone)).isEmpty()
    }

    private fun plan(
        createdAt: LocalDate,
        lengthDays: Int,
        generatedDays: Int = 0,
        status: MealPlanStatus = MealPlanStatus.READY,
    ): MealPlan {
        val millis = createdAt.atStartOfDay(zone).toInstant().toEpochMilli()
        return MealPlan(
            uuid = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            name = "$lengthDays-day meal plan",
            preferences = MealPlanPreferences(
                planLengthDays = lengthDays,
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
            createdAt = millis,
            updatedAt = millis,
            days = (0 until generatedDays).map {
                MealPlanDay(UUID.randomUUID(), it, dinnerRecipeId = UUID.randomUUID(), lunchRecipeId = null)
            },
        )
    }
}
