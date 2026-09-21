package com.tenmilelabs.chefai.mealplans.domain.week

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class MealPlanWeekTest {

    private val zone: ZoneId = ZoneOffset.UTC

    /** Monday 2026-08-10; the design's own example week is the one that follows it. */
    private val monday: LocalDate = LocalDate.of(2026, 8, 10)

    // --- weekStartFor ---

    @Test
    fun `weekStartFor returns the date itself when it is already the first day`() {
        assertThat(weekStartFor(monday, DayOfWeek.MONDAY)).isEqualTo(monday)
    }

    @Test
    fun `weekStartFor walks back to the week's first day`() {
        val thursday = monday.plusDays(3)
        assertThat(weekStartFor(thursday, DayOfWeek.MONDAY)).isEqualTo(monday)
    }

    @Test
    fun `weekStartFor honours a locale whose week starts on Sunday`() {
        // The Monday belongs to the Sunday-started week that opened the day before.
        assertThat(weekStartFor(monday, DayOfWeek.SUNDAY)).isEqualTo(monday.minusDays(1))
    }

    // --- The shape of a week ---

    @Test
    fun `project always returns seven consecutive days`() {
        val week = project(plans = emptyList())

        assertThat(week.days).hasSize(DAYS_IN_WEEK)
        assertThat(week.days.map { it.date })
            .containsExactlyElementsIn((0..6).map { monday.plusDays(it.toLong()) })
            .inOrder()
        assertThat(week.weekEnd).isEqualTo(monday.plusDays(6))
    }

    @Test
    fun `an empty week plans nothing and names no plan`() {
        val week = project(plans = emptyList())

        assertThat(week.mealCount).isEqualTo(0)
        assertThat(week.planIds).isEmpty()
        assertThat(week.singlePlanId).isNull()
        assertThat(week.days.all { it.meals.isEmpty() }).isTrue()
    }

    // --- Anchoring on createdAt ---

    @Test
    fun `day zero lands on the date the plan was created`() {
        val recipeId = UUID.randomUUID()
        val plan = plan(createdAt = monday, days = listOf(day(0, dinner = recipeId)))

        val week = project(plans = listOf(plan))

        assertThat(week.days.first().meals.map { it.recipeId }).containsExactly(recipeId)
        assertThat(week.days.drop(1).all { it.meals.isEmpty() }).isTrue()
    }

    @Test
    fun `dayIndex offsets forward from the creation date`() {
        val plan = plan(
            createdAt = monday,
            days = (0..4).map { day(it, dinner = UUID.randomUUID()) },
        )

        val week = project(plans = listOf(plan))

        assertThat(week.days.take(5).all { it.meals.size == 1 }).isTrue()
        assertThat(week.days.drop(5).all { it.meals.isEmpty() }).isTrue()
    }

    @Test
    fun `a plan created mid-week straddles two weeks`() {
        // The known cost of anchoring on createdAt — ADR-015 Decision 2. Asserted rather than
        // merely documented, so that adding MealPlan.startDate fails this test loudly.
        val thursday = monday.plusDays(3)
        val plan = plan(
            createdAt = thursday,
            days = (0..6).map { day(it, dinner = UUID.randomUUID()) },
        )

        val thisWeek = project(plans = listOf(plan), weekStart = monday)
        val nextWeek = project(plans = listOf(plan), weekStart = monday.plusDays(7))

        assertThat(thisWeek.mealCount).isEqualTo(4)
        assertThat(nextWeek.mealCount).isEqualTo(3)
    }

    @Test
    fun `days outside the window are ignored`() {
        val plan = plan(
            createdAt = monday.minusDays(14),
            days = listOf(day(0, dinner = UUID.randomUUID())),
        )

        assertThat(project(plans = listOf(plan)).mealCount).isEqualTo(0)
    }

    // --- Slots ---

    @Test
    fun `a day holding both meals keeps both, lunch first`() {
        val lunch = UUID.randomUUID()
        val dinner = UUID.randomUUID()
        val plan = plan(createdAt = monday, days = listOf(day(0, lunch = lunch, dinner = dinner)))

        val meals = project(plans = listOf(plan)).days.first().meals

        assertThat(meals.map { it.slot }).containsExactly(MealSlot.LUNCH, MealSlot.DINNER).inOrder()
        assertThat(meals.map { it.recipeId }).containsExactly(lunch, dinner).inOrder()
    }

    @Test
    fun `an unfilled slot produces no meal`() {
        val plan = plan(createdAt = monday, days = listOf(day(0, dinner = null, lunch = null)))

        assertThat(project(plans = listOf(plan)).days.first().meals).isEmpty()
    }

    @Test
    fun `cooked state comes through per slot`() {
        val plan = plan(
            createdAt = monday,
            days = listOf(
                MealPlanDay(
                    uuid = UUID.randomUUID(),
                    dayIndex = 0,
                    dinnerRecipeId = UUID.randomUUID(),
                    lunchRecipeId = UUID.randomUUID(),
                    dinnerCookedAt = 1_000L,
                    lunchCookedAt = null,
                ),
            ),
        )

        val meals = project(plans = listOf(plan)).days.first().meals

        assertThat(meals.single { it.slot == MealSlot.DINNER }.isCooked).isTrue()
        assertThat(meals.single { it.slot == MealSlot.LUNCH }.isCooked).isFalse()
    }

    // --- Overlap resolution ---

    @Test
    fun `when two plans claim the same slot the most recently updated wins`() {
        val stale = UUID.randomUUID()
        val fresh = UUID.randomUUID()
        val older = plan(createdAt = monday, updatedAtMillis = 1_000L, days = listOf(day(0, dinner = stale)))
        val newer = plan(createdAt = monday, updatedAtMillis = 2_000L, days = listOf(day(0, dinner = fresh)))

        // Order of the input list must not matter.
        listOf(listOf(older, newer), listOf(newer, older)).forEach { plans ->
            val meals = project(plans = plans).days.first().meals
            assertThat(meals.single().recipeId).isEqualTo(fresh)
        }
    }

    @Test
    fun `two plans on different slots of one day both survive`() {
        val lunch = UUID.randomUUID()
        val dinner = UUID.randomUUID()
        val lunchPlan = plan(createdAt = monday, days = listOf(day(0, lunch = lunch)))
        val dinnerPlan = plan(createdAt = monday, days = listOf(day(0, dinner = dinner)))

        val week = project(plans = listOf(lunchPlan, dinnerPlan))

        assertThat(week.days.first().meals.map { it.recipeId }).containsExactly(lunch, dinner)
        assertThat(week.planIds).hasSize(2)
        assertThat(week.singlePlanId).isNull()
    }

    @Test
    fun `archived plans never contribute`() {
        val plan = plan(
            createdAt = monday,
            status = MealPlanStatus.ARCHIVED,
            days = listOf(day(0, dinner = UUID.randomUUID())),
        )

        assertThat(project(plans = listOf(plan)).mealCount).isEqualTo(0)
    }

    // --- Recipe resolution and the aggregation inputs ---

    @Test
    fun `a recipe the device does not have leaves the meal planned but unresolved`() {
        val recipeId = UUID.randomUUID()
        val plan = plan(createdAt = monday, days = listOf(day(0, dinner = recipeId)))

        val meal = project(plans = listOf(plan), recipes = emptyMap()).days.first().meals.single()

        assertThat(meal.recipe).isNull()
        assertThat(meal.recipeId).isEqualTo(recipeId)
    }

    @Test
    fun `a resolved recipe is attached to its meal`() {
        val recipe = preview("Grilled Salmon Teriyaki")
        val plan = plan(createdAt = monday, days = listOf(day(0, dinner = recipe.uuid)))

        val meal = project(
            plans = listOf(plan),
            recipes = mapOf(recipe.uuid to recipe),
        ).days.first().meals.single()

        assertThat(meal.recipe?.title).isEqualTo("Grilled Salmon Teriyaki")
    }

    @Test
    fun `recipeIds are deduplicated but slot counts are not`() {
        // A recipe cooked twice in a week needs one ingredient query and twice the shopping.
        val twice = UUID.randomUUID()
        val once = UUID.randomUUID()
        val plan = plan(
            createdAt = monday,
            days = listOf(
                day(0, dinner = twice),
                day(1, dinner = twice),
                day(2, dinner = once),
            ),
        )

        val week = project(plans = listOf(plan))

        assertThat(week.recipeIds).containsExactly(twice, once)
        assertThat(week.slotCountByRecipe).containsExactly(twice, 2, once, 1)
    }

    @Test
    fun `singlePlanId names the plan when the week is made of exactly one`() {
        val plan = plan(createdAt = monday, days = listOf(day(0, dinner = UUID.randomUUID())))

        assertThat(project(plans = listOf(plan)).singlePlanId).isEqualTo(plan.uuid)
    }

    // --- Helpers ---

    private fun project(
        plans: List<MealPlan>,
        weekStart: LocalDate = monday,
        recipes: Map<UUID, RecipePreview> = emptyMap(),
    ) = MealPlanWeekProjector.project(
        plans = plans,
        weekStart = weekStart,
        recipes = recipes,
        zone = zone,
    )

    private fun day(
        index: Int,
        dinner: UUID? = null,
        lunch: UUID? = null,
    ) = MealPlanDay(
        uuid = UUID.randomUUID(),
        dayIndex = index,
        dinnerRecipeId = dinner,
        lunchRecipeId = lunch,
    )

    private fun plan(
        createdAt: LocalDate,
        days: List<MealPlanDay>,
        status: MealPlanStatus = MealPlanStatus.READY,
        updatedAtMillis: Long = 0L,
        householdId: UUID? = null,
    ): MealPlan {
        val createdAtMillis = createdAt.atStartOfDay(zone).toInstant().toEpochMilli()
        return MealPlan(
            uuid = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            name = "Week plan",
            preferences = MealPlanPreferences(
                planLengthDays = days.size,
                mealType = MealType.DINNER,
                dietaryRestrictions = emptySet<DietaryRestriction>(),
                recipeSource = RecipeSource.COLLECTION_ONLY,
                maxPrepTimeMinutes = null,
                servingsPerMeal = 2,
                batchCooking = false,
                leftoverFriendly = false,
                varietyPreference = VarietyPreference.MEDIUM,
            ),
            status = status,
            createdAt = createdAtMillis,
            updatedAt = if (updatedAtMillis == 0L) createdAtMillis else updatedAtMillis,
            days = days,
            householdId = householdId,
        )
    }

    private fun preview(title: String) = RecipePreview(
        uuid = UUID.randomUUID(),
        title = title,
        description = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 10,
        cookTimeMinutes = 15,
        servings = 4,
        creatorId = UUID.randomUUID(),
        tags = emptyList(),
        labels = emptyList(),
    )
}
