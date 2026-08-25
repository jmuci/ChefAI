package com.tenmilelabs.chefai.mealplans.domain.print

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.PlannedIngredient
import org.junit.Test
import java.util.UUID

class MealPlanPrintDocumentBuilderTest {

    private fun recipePreview(id: UUID, title: String) = RecipePreview(
        uuid = id,
        title = title,
        description = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 0,
        cookTimeMinutes = 0,
        servings = 1,
        creatorId = UUID.randomUUID(),
        tags = emptyList(),
        labels = emptyList(),
    )

    private fun day(index: Int, dinnerRecipeId: UUID?, lunchRecipeId: UUID? = null) = MealPlanDay(
        uuid = UUID.randomUUID(),
        dayIndex = index,
        dinnerRecipeId = dinnerRecipeId,
        lunchRecipeId = lunchRecipeId,
    )

    private fun mealPlan(
        days: List<MealPlanDay>,
        mealType: MealType = MealType.DINNER,
        name: String = "Test Plan",
    ) = MealPlan(
        uuid = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        name = name,
        preferences = MealPlanPreferences(
            planLengthDays = days.size,
            mealType = mealType,
            dietaryRestrictions = emptySet(),
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

    private fun ingredient(recipeId: UUID, name: String, quantity: Double) =
        PlannedIngredient(recipeId, recipeServings = 2, displayName = name, quantity = quantity, unit = "g")

    @Test
    fun `a plan's days become one column per day, in day-index order`() {
        val recipe = UUID.randomUUID()
        val recipeMap = mapOf(recipe to recipePreview(recipe, "Soup"))
        val plan = mealPlan(days = listOf(day(0, recipe), day(1, recipe), day(2, recipe)))

        val document = MealPlanPrintDocumentBuilder.build(plan, recipeMap, emptyList())

        assertThat(document.blocks).hasSize(1)
        assertThat(document.blocks.single().columns.map { it.label })
            .containsExactly("Day 1", "Day 2", "Day 3").inOrder()
    }

    @Test
    fun `more than seven days wrap into a second table block`() {
        val recipe = UUID.randomUUID()
        val recipeMap = mapOf(recipe to recipePreview(recipe, "Soup"))
        val plan = mealPlan(days = (0 until 10).map { day(it, recipe) })

        val document = MealPlanPrintDocumentBuilder.build(plan, recipeMap, emptyList())

        assertThat(document.blocks).hasSize(2)
        assertThat(document.blocks[0].columns.map { it.label })
            .containsExactly("Day 1", "Day 2", "Day 3", "Day 4", "Day 5", "Day 6", "Day 7").inOrder()
        assertThat(document.blocks[1].columns.map { it.label })
            .containsExactly("Day 8", "Day 9", "Day 10").inOrder()
    }

    @Test
    fun `exactly seven days stays a single block`() {
        val recipe = UUID.randomUUID()
        val recipeMap = mapOf(recipe to recipePreview(recipe, "Soup"))
        val plan = mealPlan(days = (0 until 7).map { day(it, recipe) })

        val document = MealPlanPrintDocumentBuilder.build(plan, recipeMap, emptyList())

        assertThat(document.blocks).hasSize(1)
        assertThat(document.blocks.single().columns).hasSize(7)
    }

    @Test
    fun `a dinner-only plan has no slot label, a dinner-and-lunch plan does`() {
        val recipe = UUID.randomUUID()
        val recipeMap = mapOf(recipe to recipePreview(recipe, "Soup"))

        val dinnerOnly = MealPlanPrintDocumentBuilder.build(
            mealPlan(days = listOf(day(0, dinnerRecipeId = recipe)), mealType = MealType.DINNER),
            recipeMap,
            emptyList(),
        )
        assertThat(dinnerOnly.blocks.single().columns.single().meals.single().slotLabel).isNull()

        val dinnerAndLunch = MealPlanPrintDocumentBuilder.build(
            mealPlan(
                days = listOf(day(0, dinnerRecipeId = recipe, lunchRecipeId = recipe)),
                mealType = MealType.DINNER_AND_LUNCH,
            ),
            recipeMap,
            emptyList(),
        )
        assertThat(dinnerAndLunch.blocks.single().columns.single().meals.map { it.slotLabel })
            .containsExactly("Lunch", "Dinner").inOrder()
    }

    @Test
    fun `top ingredients are ranked by quantity descending, capped at three`() {
        val recipe = UUID.randomUUID()
        val recipeMap = mapOf(recipe to recipePreview(recipe, "Soup"))
        val plan = mealPlan(days = listOf(day(0, recipe)))
        val ingredients = listOf(
            ingredient(recipe, "Salt", 1.0),
            ingredient(recipe, "Chicken", 500.0),
            ingredient(recipe, "Carrot", 50.0),
            ingredient(recipe, "Onion", 100.0),
            ingredient(recipe, "Pepper", 0.5),
        )

        val document = MealPlanPrintDocumentBuilder.build(plan, recipeMap, ingredients)

        assertThat(document.blocks.single().columns.single().meals.single().topIngredients)
            .containsExactly("Chicken", "Onion", "Carrot").inOrder()
    }

    @Test
    fun `a slot with no recipe assigned is skipped`() {
        val recipe = UUID.randomUUID()
        val recipeMap = mapOf(recipe to recipePreview(recipe, "Soup"))
        val plan = mealPlan(
            days = listOf(day(0, dinnerRecipeId = recipe, lunchRecipeId = null)),
            mealType = MealType.DINNER_AND_LUNCH,
        )

        val document = MealPlanPrintDocumentBuilder.build(plan, recipeMap, emptyList())

        assertThat(document.blocks.single().columns.single().meals).hasSize(1)
    }

    @Test
    fun `a slot whose recipe hasn't synced is skipped`() {
        val unsyncedRecipe = UUID.randomUUID()
        val plan = mealPlan(days = listOf(day(0, dinnerRecipeId = unsyncedRecipe)))

        val document = MealPlanPrintDocumentBuilder.build(plan, recipeMap = emptyMap(), ingredients = emptyList())

        assertThat(document.blocks.single().columns.single().meals).isEmpty()
    }
}
