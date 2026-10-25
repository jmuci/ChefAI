package com.tenmilelabs.chefai.mealplans.domain.summary

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class MealPlanSummaryBuilderTest {

    private val zone = ZoneOffset.UTC
    private val start = LocalDate.of(2026, 8, 10)

    @Test
    fun `a default or blank name reads as untitled so the UI can generate one`() {
        val recipe = preview("Carbonara", tags = listOf("Italian"))
        val plans = listOf(
            plan(name = "Week plan", days = listOf(day(0, recipe.uuid))),
            plan(name = "  ", days = listOf(day(0, recipe.uuid)), createdOn = start.minusDays(1)),
            plan(name = "Birthday week", days = listOf(day(0, recipe.uuid)), createdOn = start.minusDays(2)),
        )

        val summaries = MealPlanSummaryBuilder.build(plans, mapOf(recipe.uuid to recipe), zone)

        assertThat(summaries.map { it.customName }).containsExactly(null, null, "Birthday week").inOrder()
        assertThat(summaries.first().dominantTag).isEqualTo("Italian")
    }

    @Test
    fun `the dominant tag is the one most recipes share, ties alphabetical`() {
        val a = preview("A", tags = listOf("Quick", "Italian"))
        val b = preview("B", tags = listOf("Quick"))
        val c = preview("C", tags = listOf("Italian"))
        val plan = plan(days = listOf(day(0, a.uuid, b.uuid), day(1, c.uuid)))

        val summary = MealPlanSummaryBuilder.build(plan.asList(), listOf(a, b, c).associateBy { it.uuid }, zone).single()

        assertThat(summary.dominantTag).isEqualTo("Italian")
        assertThat(summary.recipeCount).isEqualTo(3)
    }

    @Test
    fun `the hero is the first recipe with an image and the dates follow the plan length`() {
        val bare = preview("Bare")
        val pictured = preview("Pictured", image = "https://example.com/a.jpg")
        val plan = plan(days = listOf(day(0, bare.uuid), day(1, pictured.uuid)), length = 5)

        val summary = MealPlanSummaryBuilder.build(plan.asList(), listOf(bare, pictured).associateBy { it.uuid }, zone).single()

        assertThat(summary.heroRecipe?.title).isEqualTo("Pictured")
        assertThat(summary.startDate).isEqualTo(start)
        assertThat(summary.endDate).isEqualTo(start.plusDays(4))
        assertThat(summary.dayCount).isEqualTo(5)
        assertThat(summary.isPast(start.plusDays(5))).isTrue()
        assertThat(summary.isPast(start.plusDays(4))).isFalse()
    }

    @Test
    fun `last cooked is the latest cooked slot and archived plans are dropped`() {
        val recipe = preview("R")
        val cooked = start.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val live = plan(days = listOf(day(0, recipe.uuid).copy(dinnerCookedAt = cooked)))
        val archived = plan(days = listOf(day(0, recipe.uuid)), status = MealPlanStatus.ARCHIVED)

        val summaries = MealPlanSummaryBuilder.build(listOf(live, archived), mapOf(recipe.uuid to recipe), zone)

        assertThat(summaries).hasSize(1)
        assertThat(summaries.single().lastCookedOn).isEqualTo(start.plusDays(2))
    }

    @Test
    fun `the hero skips cooked recipes and falls back once everything is cooked`() {
        val first = preview("First", image = "https://example.com/1.jpg")
        val second = preview("Second", image = "https://example.com/2.jpg")
        val cooked = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val half = plan(days = listOf(day(0, first.uuid).copy(dinnerCookedAt = cooked), day(1, second.uuid)))
        val done = plan(
            days = listOf(
                day(0, first.uuid).copy(dinnerCookedAt = cooked),
                day(1, second.uuid).copy(dinnerCookedAt = cooked),
            ),
            createdOn = start.minusDays(1),
        )

        val summaries = MealPlanSummaryBuilder.build(
            listOf(half, done),
            listOf(first, second).associateBy { it.uuid },
            zone,
        )

        assertThat(summaries[0].heroRecipe?.title).isEqualTo("Second")
        assertThat(summaries[1].heroRecipe?.title).isEqualTo("First")
    }

    private fun MealPlan.asList() = listOf(this)

    private fun day(index: Int, dinner: UUID? = null, lunch: UUID? = null) = MealPlanDay(
        uuid = UUID.randomUUID(),
        dayIndex = index,
        dinnerRecipeId = dinner,
        lunchRecipeId = lunch,
    )

    private fun plan(
        name: String = "Week plan",
        days: List<MealPlanDay>,
        createdOn: LocalDate = start,
        length: Int = days.size,
        status: MealPlanStatus = MealPlanStatus.READY,
    ): MealPlan {
        val millis = createdOn.atStartOfDay(zone).toInstant().toEpochMilli()
        return MealPlan(
            uuid = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            name = name,
            preferences = MealPlanPreferences(
                planLengthDays = length,
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
            days = days,
        )
    }

    private fun preview(title: String, tags: List<String> = emptyList(), image: String = "") = RecipePreview(
        uuid = UUID.randomUUID(),
        title = title,
        description = "",
        imageUrlThumbnail = image,
        prepTimeMinutes = 10,
        cookTimeMinutes = 15,
        servings = 4,
        creatorId = UUID.randomUUID(),
        tags = tags.map { Tag(UUID.randomUUID(), it) },
        labels = emptyList(),
    )
}
