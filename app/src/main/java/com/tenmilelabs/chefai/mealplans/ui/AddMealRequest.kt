package com.tenmilelabs.chefai.mealplans.ui

import androidx.navigation.NavBackStackEntry
import java.util.UUID

/**
 * The hand-off for "+ Add meal": where a recipe must land, and the recipe once the user has found
 * or created it.
 *
 * Lives in the Meal Plans tab's own back-stack entry (its `SavedStateHandle` *is*
 * [MealPlansViewModel]'s), so the destination date outlives the picker and editor screens the user
 * passes through. [MealPlansViewModel.onAddMealSourceChosen] writes [KEY_DATE]/[KEY_SLOT];
 * whichever screen finishes the job calls [deliver].
 */
object AddMealRequest {
    internal const val KEY_DATE = "addMeal.date"
    internal const val KEY_SLOT = "addMeal.slot"
    internal const val KEY_RECIPE = "addMeal.recipeId"

    /**
     * Hands [recipeId] to the Meal Plans entry [tab], if that entry has a request waiting.
     *
     * @return `true` when it was delivered, so the caller should pop back to the tab; `false` when
     *   no request is pending and the caller should do whatever it normally does.
     */
    fun deliver(tab: NavBackStackEntry?, recipeId: UUID): Boolean {
        val handle = tab?.savedStateHandle ?: return false
        if (handle.get<String>(KEY_DATE) == null) return false
        handle[KEY_RECIPE] = recipeId.toString()
        return true
    }
}
