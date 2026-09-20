package com.tenmilelabs.chefai.mealplans.ui.detail

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
import java.util.UUID

class MealPlanBoardTest {

    private val lunch0 = UUID.randomUUID()
    private val dinner0 = UUID.randomUUID()
    private val lunch1 = UUID.randomUUID()
    private val dinner1 = UUID.randomUUID()

    private val recipes = listOf(lunch0, dinner0, lunch1, dinner1)
        .associateWith { preview(it) }

    @Test
    fun `a fresh plan lists every meal, none cooked`() {
        val board = MealPlanBoard.from(twoFullDays(), recipes)

        assertThat(board.meals).hasSize(4)
        assertThat(board.meals.none { it.isCooked }).isTrue()
        assertThat(board.totalCount).isEqualTo(4)
        assertThat(board.progress).isEqualTo(0f)
    }

    @Test
    fun `meals are ordered by day, then lunch before dinner`() {
        val board = MealPlanBoard.from(twoFullDays(), recipes)

        assertThat(board.meals.map { it.dayIndex to it.slot }).containsExactly(
            0 to MealSlot.LUNCH,
            0 to MealSlot.DINNER,
            1 to MealSlot.LUNCH,
            1 to MealSlot.DINNER,
        ).inOrder()
    }

    @Test
    fun `recipes are resolved onto their meals`() {
        val board = MealPlanBoard.from(twoFullDays(), recipes)

        assertThat(board.meals.all { it.recipe != null }).isTrue()
    }

    @Test
    fun `days arriving out of order are sorted`() {
        val plan = plan(
            listOf(
                day(dayIndex = 2, dinner = dinner1),
                day(dayIndex = 0, dinner = dinner0),
            ),
            mealType = MealType.DINNER,
        )

        assertThat(MealPlanBoard.from(plan, recipes).meals.map { it.dayIndex })
            .containsExactly(0, 2).inOrder()
    }

    @Test
    fun `a cooked meal stays in place, struck through rather than moved`() {
        val plan = plan(
            listOf(
                day(0, lunch = lunch0, dinner = dinner0, lunchCookedAt = 100L),
                day(1, lunch = lunch1, dinner = dinner1),
            )
        )

        val board = MealPlanBoard.from(plan, recipes)

        assertThat(board.meals.map { it.dayIndex to it.slot }).containsExactly(
            0 to MealSlot.LUNCH,
            0 to MealSlot.DINNER,
            1 to MealSlot.LUNCH,
            1 to MealSlot.DINNER,
        ).inOrder()
        assertThat(board.meals.first { it.dayIndex == 0 && it.slot == MealSlot.LUNCH }.isCooked).isTrue()
        assertThat(board.meals.first { it.dayIndex == 0 && it.slot == MealSlot.DINNER }.isCooked).isFalse()
    }

    @Test
    fun `cooking does not change the plan's total`() {
        val plan = plan(
            listOf(
                day(0, lunch = lunch0, dinner = dinner0, lunchCookedAt = 100L),
                day(1, lunch = lunch1, dinner = dinner1),
            )
        )

        val board = MealPlanBoard.from(plan, recipes)

        assertThat(board.totalCount).isEqualTo(4)
        assertThat(board.cookedCount).isEqualTo(1)
        assertThat(board.progress).isEqualTo(0.25f)
    }

    @Test
    fun `a cooked meal remembers which day it came from`() {
        val plan = plan(listOf(day(1, lunch = lunch1, lunchCookedAt = 5L)))

        assertThat(MealPlanBoard.from(plan, recipes).meals.single().dayLabel).isEqualTo("Day 2")
    }

    @Test
    fun `a fully cooked plan reports complete progress`() {
        val plan = plan(
            listOf(day(0, lunch = lunch0, dinner = dinner0, lunchCookedAt = 1L, dinnerCookedAt = 2L))
        )

        val board = MealPlanBoard.from(plan, recipes)

        assertThat(board.meals.all { it.isCooked }).isTrue()
        assertThat(board.progress).isEqualTo(1f)
    }

    // --- Edge cases ---

    @Test
    fun `an empty plan has nothing to show and does not divide by zero`() {
        val board = MealPlanBoard.from(plan(emptyList()), recipes)

        assertThat(board.meals).isEmpty()
        assertThat(board.totalCount).isEqualTo(0)
        assertThat(board.progress).isEqualTo(0f)
    }

    @Test
    fun `an unfilled slot is not counted as a meal`() {
        val plan = plan(listOf(day(0, dinner = dinner0)), mealType = MealType.DINNER)

        val board = MealPlanBoard.from(plan, recipes)

        assertThat(board.totalCount).isEqualTo(1)
        assertThat(board.meals.single().slot).isEqualTo(MealSlot.DINNER)
    }

    @Test
    fun `a recipe missing from the device still yields a row that can be ticked off`() {
        val board = MealPlanBoard.from(twoFullDays(), recipeMap = emptyMap())

        assertThat(board.totalCount).isEqualTo(4)
        assertThat(board.meals.all { it.recipe == null }).isTrue()
    }

    @Test
    fun `mealPlanDateFor treats createdAt as day zero`() {
        // 2024-01-01T00:00:00Z, well clear of any timezone rolling it to a different calendar day.
        val createdAt = 1_704_110_400_000L

        val day0 = mealPlanDateFor(createdAt, 0)
        val day2 = mealPlanDateFor(createdAt, 2)

        assertThat(day2).isEqualTo(day0.plusDays(2))
    }

    // --- Helpers ---

    private fun twoFullDays() = plan(
        listOf(
            day(0, lunch = lunch0, dinner = dinner0),
            day(1, lunch = lunch1, dinner = dinner1),
        )
    )

    private fun day(
        dayIndex: Int,
        lunch: UUID? = null,
        dinner: UUID? = null,
        lunchCookedAt: Long? = null,
        dinnerCookedAt: Long? = null,
    ) = MealPlanDay(
        uuid = UUID.randomUUID(),
        dayIndex = dayIndex,
        dinnerRecipeId = dinner,
        lunchRecipeId = lunch,
        dinnerCookedAt = dinnerCookedAt,
        lunchCookedAt = lunchCookedAt,
    )

    private fun plan(
        days: List<MealPlanDay>,
        mealType: MealType = MealType.DINNER_AND_LUNCH,
    ) = MealPlan(
        uuid = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        name = "Test plan",
        preferences = MealPlanPreferences(
            planLengthDays = days.size,
            mealType = mealType,
            dietaryRestrictions = setOf(DietaryRestriction.NONE),
            recipeSource = RecipeSource.INCLUDE_PUBLIC,
            maxPrepTimeMinutes = null,
            servingsPerMeal = 2,
            batchCooking = false,
            leftoverFriendly = false,
            varietyPreference = VarietyPreference.MEDIUM,
        ),
        status = MealPlanStatus.READY,
        createdAt = 0L,
        updatedAt = 0L,
        days = days,
    )

    private fun preview(id: UUID) = RecipePreview(
        uuid = id,
        title = "Recipe $id",
        description = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 5,
        cookTimeMinutes = 5,
        servings = 2,
        creatorId = UUID.randomUUID(),
        tags = emptyList(),
        labels = emptyList(),
    )
}
