package com.tenmilelabs.chefai.mealplans.ui.detail

import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlanPreferenceRowsTest {

    private fun prefs(
        dietary: Set<DietaryRestriction> = setOf(DietaryRestriction.NONE),
        maxPrep: Int? = null,
        batchCooking: Boolean = false,
        leftoverFriendly: Boolean = false,
    ) = MealPlanPreferences(
        planLengthDays = 5,
        mealType = MealType.DINNER_AND_LUNCH,
        dietaryRestrictions = dietary,
        recipeSource = RecipeSource.INCLUDE_PUBLIC,
        maxPrepTimeMinutes = maxPrep,
        servingsPerMeal = 2,
        batchCooking = batchCooking,
        leftoverFriendly = leftoverFriendly,
        varietyPreference = VarietyPreference.HIGH,
    )

    private fun List<PlanPreferenceRow>.valueOf(labelRes: Int): PlanPreferenceValue? =
        firstOrNull { it.labelRes == labelRes }?.value

    @Test
    fun `defaults show only the always-present rows in display order`() {
        // Given a plan with no restrictions, no prep limit and no cooking-style options
        // When it is mapped to display rows
        val rows = prefs().toDisplayRows()

        // Then only meals, duration, servings, recipe source and variety remain
        assertEquals(
            listOf(
                R.string.meal_plan_pref_meals,
                R.string.meal_plan_pref_duration,
                R.string.meal_plan_pref_servings,
                R.string.meal_plan_pref_recipe_source,
                R.string.meal_plan_pref_variety,
            ),
            rows.map { it.labelRes },
        )
    }

    @Test
    fun `always-present rows carry the stored values`() {
        val rows = prefs().toDisplayRows()

        assertEquals(PlanPreferenceValue.Literal(MealType.DINNER_AND_LUNCH.label), rows.valueOf(R.string.meal_plan_pref_meals))
        assertEquals(PlanPreferenceValue.Days(5), rows.valueOf(R.string.meal_plan_pref_duration))
        assertEquals(PlanPreferenceValue.Servings(2), rows.valueOf(R.string.meal_plan_pref_servings))
        assertEquals(
            PlanPreferenceValue.Literal(RecipeSource.INCLUDE_PUBLIC.label),
            rows.valueOf(R.string.meal_plan_pref_recipe_source),
        )
        assertEquals(
            PlanPreferenceValue.Literal(VarietyPreference.HIGH.label),
            rows.valueOf(R.string.meal_plan_pref_variety),
        )
    }

    @Test
    fun `dietary restrictions are listed and NONE is dropped`() {
        val rows = prefs(dietary = setOf(DietaryRestriction.VEGAN, DietaryRestriction.GLUTEN_FREE)).toDisplayRows()

        assertEquals(
            PlanPreferenceValue.Literal("Vegan, Gluten-Free"),
            rows.valueOf(R.string.meal_plan_pref_dietary),
        )
    }

    @Test
    fun `an empty dietary set omits the row`() {
        val rows = prefs(dietary = emptySet()).toDisplayRows()

        assertNull(rows.valueOf(R.string.meal_plan_pref_dietary))
    }

    @Test
    fun `max prep is shown only when set`() {
        assertNull(prefs(maxPrep = null).toDisplayRows().valueOf(R.string.meal_plan_pref_max_prep))
        assertEquals(
            PlanPreferenceValue.Minutes(30),
            prefs(maxPrep = 30).toDisplayRows().valueOf(R.string.meal_plan_pref_max_prep),
        )
    }

    @Test
    fun `cooking style lists each enabled option and is omitted when none`() {
        assertFalse(
            prefs().toDisplayRows().any { it.labelRes == R.string.meal_plan_pref_cooking_style },
        )
        assertEquals(
            PlanPreferenceValue.Resources(listOf(R.string.meal_plan_pref_leftover_friendly)),
            prefs(leftoverFriendly = true).toDisplayRows().valueOf(R.string.meal_plan_pref_cooking_style),
        )
        assertEquals(
            PlanPreferenceValue.Resources(
                listOf(R.string.meal_plan_pref_batch_cooking, R.string.meal_plan_pref_leftover_friendly),
            ),
            prefs(batchCooking = true, leftoverFriendly = true)
                .toDisplayRows().valueOf(R.string.meal_plan_pref_cooking_style),
        )
    }
}
