package com.tenmilelabs.chefai.mealplans.ui.detail

import androidx.annotation.StringRes
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences

/** The value half of a [PlanPreferenceRow]; resolved to text by the composable that draws it. */
internal sealed interface PlanPreferenceValue {
    /** Text that already is display text — the wizard's option labels carried by the domain enums. */
    data class Literal(val text: String) : PlanPreferenceValue

    data class Days(val count: Int) : PlanPreferenceValue

    data class Servings(val count: Int) : PlanPreferenceValue

    data class Minutes(val count: Int) : PlanPreferenceValue

    /** Several string resources shown comma-separated, e.g. the cooking-style options. */
    data class Resources(val ids: List<Int>) : PlanPreferenceValue
}

internal data class PlanPreferenceRow(
    @StringRes val labelRes: Int,
    val value: PlanPreferenceValue,
)

/**
 * The plan's wizard answers that are persisted on the plan, in display order. Meals, duration,
 * servings, recipe source and variety always have a value. Dietary restrictions, max prep time and
 * cooking-style options are omitted when they are at their "nothing chosen" default, so the panel
 * lists choices rather than "No restrictions" / "No limit" filler.
 */
internal fun MealPlanPreferences.toDisplayRows(): List<PlanPreferenceRow> = buildList {
    add(PlanPreferenceRow(R.string.meal_plan_pref_meals, PlanPreferenceValue.Literal(mealType.label)))
    add(PlanPreferenceRow(R.string.meal_plan_pref_duration, PlanPreferenceValue.Days(planLengthDays)))
    add(PlanPreferenceRow(R.string.meal_plan_pref_servings, PlanPreferenceValue.Servings(servingsPerMeal)))

    val dietary = dietaryRestrictions.filter { it != DietaryRestriction.NONE }
    if (dietary.isNotEmpty()) {
        add(
            PlanPreferenceRow(
                R.string.meal_plan_pref_dietary,
                PlanPreferenceValue.Literal(dietary.joinToString(", ") { it.label }),
            ),
        )
    }

    maxPrepTimeMinutes?.let {
        add(PlanPreferenceRow(R.string.meal_plan_pref_max_prep, PlanPreferenceValue.Minutes(it)))
    }
    add(PlanPreferenceRow(R.string.meal_plan_pref_recipe_source, PlanPreferenceValue.Literal(recipeSource.label)))
    add(PlanPreferenceRow(R.string.meal_plan_pref_variety, PlanPreferenceValue.Literal(varietyPreference.label)))

    val cookingStyle = buildList {
        if (batchCooking) add(R.string.meal_plan_pref_batch_cooking)
        if (leftoverFriendly) add(R.string.meal_plan_pref_leftover_friendly)
    }
    if (cookingStyle.isNotEmpty()) {
        add(PlanPreferenceRow(R.string.meal_plan_pref_cooking_style, PlanPreferenceValue.Resources(cookingStyle)))
    }
}
