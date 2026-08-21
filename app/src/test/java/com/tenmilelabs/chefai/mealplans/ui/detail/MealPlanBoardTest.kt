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

    // --- Nothing cooked ---

    @Test
    fun `a fresh plan puts every meal in upcoming, grouped by day`() {
        val board = MealPlanBoard.from(twoFullDays(), recipes)

        assertThat(board.cooked).isEmpty()
        assertThat(board.upcoming).hasSize(2)
        assertThat(board.totalCount).isEqualTo(4)
        assertThat(board.progress).isEqualTo(0f)
    }

    @Test
    fun `day headings are one-based`() {
        val board = MealPlanBoard.from(twoFullDays(), recipes)

        assertThat(board.upcoming.map { it.label }).containsExactly("Day 1", "Day 2").inOrder()
    }

    @Test
    fun `lunch is listed before dinner`() {
        val board = MealPlanBoard.from(twoFullDays(), recipes)

        assertThat(board.upcoming.first().meals.map { it.slot })
            .containsExactly(MealSlot.LUNCH, MealSlot.DINNER).inOrder()
    }

    @Test
    fun `recipes are resolved onto their meals`() {
        val board = MealPlanBoard.from(twoFullDays(), recipes)

        assertThat(board.upcoming.flatMap { it.meals }.all { it.recipe != null }).isTrue()
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

        assertThat(MealPlanBoard.from(plan, recipes).upcoming.map { it.dayIndex })
            .containsExactly(0, 2).inOrder()
    }

    // --- Cooking moves meals to the bottom ---

    @Test
    fun `a cooked meal leaves upcoming and joins the cooked list`() {
        val plan = plan(
            listOf(
                day(0, lunch = lunch0, dinner = dinner0, lunchCookedAt = 100L),
                day(1, lunch = lunch1, dinner = dinner1),
            )
        )

        val board = MealPlanBoard.from(plan, recipes)

        assertThat(board.cooked.map { it.slot }).containsExactly(MealSlot.LUNCH)
        assertThat(board.upcoming.first().meals.map { it.slot }).containsExactly(MealSlot.DINNER)
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
    fun `a fully cooked day disappears from upcoming`() {
        val plan = plan(
            listOf(
                day(0, lunch = lunch0, dinner = dinner0, lunchCookedAt = 1L, dinnerCookedAt = 2L),
                day(1, lunch = lunch1, dinner = dinner1),
            )
        )

        val board = MealPlanBoard.from(plan, recipes)

        assertThat(board.upcoming.map { it.dayIndex }).containsExactly(1)
        assertThat(board.cooked).hasSize(2)
    }

    @Test
    fun `cooked meals are listed most recently cooked first`() {
        val plan = plan(
            listOf(day(0, lunch = lunch0, dinner = dinner0, lunchCookedAt = 1L, dinnerCookedAt = 2L))
        )

        assertThat(MealPlanBoard.from(plan, recipes).cooked.map { it.cookedAt })
            .containsExactly(2L, 1L).inOrder()
    }

    @Test
    fun `a cooked meal remembers which day it came from`() {
        val plan = plan(listOf(day(1, lunch = lunch1, lunchCookedAt = 5L)))

        assertThat(MealPlanBoard.from(plan, recipes).cooked.single().dayLabel).isEqualTo("Day 2")
    }

    @Test
    fun `a fully cooked plan reports complete progress and an empty upcoming list`() {
        val plan = plan(
            listOf(day(0, lunch = lunch0, dinner = dinner0, lunchCookedAt = 1L, dinnerCookedAt = 2L))
        )

        val board = MealPlanBoard.from(plan, recipes)

        assertThat(board.upcoming).isEmpty()
        assertThat(board.progress).isEqualTo(1f)
    }

    // --- Edge cases ---

    @Test
    fun `an empty plan has nothing to show and does not divide by zero`() {
        val board = MealPlanBoard.from(plan(emptyList()), recipes)

        assertThat(board.upcoming).isEmpty()
        assertThat(board.cooked).isEmpty()
        assertThat(board.totalCount).isEqualTo(0)
        assertThat(board.progress).isEqualTo(0f)
    }

    @Test
    fun `an unfilled slot is not counted as a meal`() {
        val plan = plan(listOf(day(0, dinner = dinner0)), mealType = MealType.DINNER)

        val board = MealPlanBoard.from(plan, recipes)

        assertThat(board.totalCount).isEqualTo(1)
        assertThat(board.upcoming.single().meals.single().slot).isEqualTo(MealSlot.DINNER)
    }

    @Test
    fun `a recipe missing from the device still yields a row that can be ticked off`() {
        val board = MealPlanBoard.from(twoFullDays(), recipeMap = emptyMap())

        assertThat(board.totalCount).isEqualTo(4)
        assertThat(board.upcoming.flatMap { it.meals }.all { it.recipe == null }).isTrue()
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
