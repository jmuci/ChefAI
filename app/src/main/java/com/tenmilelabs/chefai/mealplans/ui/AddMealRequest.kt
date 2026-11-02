package com.tenmilelabs.chefai.mealplans.ui

import androidx.lifecycle.SavedStateHandle
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import java.time.LocalDate

/**
 * The hand-off for "+ Add meal → Add a New Recipe": which date and meal the recipe is for, kept
 * while the user is in the importer and editor, so that saving the new recipe can open the
 * meal-plan wizard on that date.
 *
 * Lives in the Meal Plans tab's **back-stack entry** `SavedStateHandle` (`entry.savedStateHandle`),
 * so it outlives the screens the user passes through, and process death. That handle is *not*
 * [MealPlansViewModel]'s: Navigation backs it with its own internal ViewModel, and Hilt gives every
 * ViewModel a handle of its own. So every read and write goes through the entry's handle, from the
 * nav graph.
 */
object AddMealRequest {
    internal const val KEY_DATE = "addMeal.date"
    internal const val KEY_SLOT = "addMeal.slot"

    /** A waiting request: the new recipe is for [date]'s [slot]. */
    data class Pending(val date: LocalDate, val slot: MealSlot)

    /** Remembers that a recipe created from here on is for [date]'s [slot]. */
    fun begin(handle: SavedStateHandle, date: LocalDate, slot: MealSlot) {
        handle[KEY_DATE] = date.toString()
        handle[KEY_SLOT] = slot.name
    }

    /**
     * The request waiting in [handle], consumed so it is acted on once; `null` when none is waiting
     * or what is stored no longer parses.
     */
    fun take(handle: SavedStateHandle): Pending? {
        val date = handle.get<String>(KEY_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val slot = handle.get<String>(KEY_SLOT)?.let { name -> MealSlot.entries.firstOrNull { it.name == name } }
        cancel(handle)
        return if (date != null && slot != null) Pending(date, slot) else null
    }

    /**
     * Called when the tab is back on screen. A request still waiting at that point means the user
     * backed out of the importer or editor, so it is dropped — otherwise the next recipe saved
     * anywhere would open the wizard on a date nobody is thinking about.
     */
    fun cancel(handle: SavedStateHandle) {
        handle[KEY_DATE] = null
        handle[KEY_SLOT] = null
    }
}
