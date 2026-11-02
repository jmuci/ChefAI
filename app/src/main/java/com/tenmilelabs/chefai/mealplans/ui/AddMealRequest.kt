package com.tenmilelabs.chefai.mealplans.ui

import androidx.lifecycle.SavedStateHandle
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import java.time.LocalDate
import java.util.UUID

/**
 * The hand-off for "+ Add meal → Add a New Recipe": where a recipe must land, and the recipe once
 * the user has created it.
 *
 * Lives in the Meal Plans tab's **back-stack entry** `SavedStateHandle` (`entry.savedStateHandle`),
 * so the destination date outlives the importer and editor screens the user passes through, and
 * process death. That handle is *not* [MealPlansViewModel]'s: Navigation backs it with its own
 * internal ViewModel, and Hilt gives every ViewModel a handle of its own. So every read and write
 * goes through the entry's handle, from the nav graph, and the ViewModel only ever sees a finished
 * [Delivered].
 */
object AddMealRequest {
    internal const val KEY_DATE = "addMeal.date"
    internal const val KEY_SLOT = "addMeal.slot"
    internal const val KEY_RECIPE = "addMeal.recipeId"

    /** A recipe that arrived for a pending request: put [recipeId] on [date]'s [slot]. */
    data class Delivered(val date: LocalDate, val slot: MealSlot, val recipeId: UUID)

    /** Remembers that a recipe created from here on belongs on [date]'s [slot]. */
    fun begin(handle: SavedStateHandle, date: LocalDate, slot: MealSlot) {
        handle[KEY_DATE] = date.toString()
        handle[KEY_SLOT] = slot.name
        handle[KEY_RECIPE] = null
    }

    /**
     * Hands [recipeId] to the tab whose entry owns [handle], if that tab has a request waiting.
     *
     * @return `true` when it was delivered, so the caller should pop back to the tab; `false` when
     *   no request is pending and the caller should do whatever it normally does.
     */
    fun deliver(handle: SavedStateHandle?, recipeId: UUID): Boolean {
        if (handle?.get<String>(KEY_DATE) == null) return false
        handle[KEY_RECIPE] = recipeId.toString()
        return true
    }

    /**
     * Each recipe delivered to [handle], once. Consuming a delivery clears the request, so a
     * recollection (rotation, returning to the tab) never adds the same meal twice. A delivery whose
     * date or slot no longer parses is dropped.
     */
    fun results(handle: SavedStateHandle): Flow<Delivered> =
        handle.getStateFlow<String?>(KEY_RECIPE, null)
            .filterNotNull()
            .mapNotNull { raw ->
                val date = handle.get<String>(KEY_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val slot = handle.get<String>(KEY_SLOT)?.let { name -> MealSlot.entries.firstOrNull { it.name == name } }
                val recipeId = runCatching { UUID.fromString(raw) }.getOrNull()
                clear(handle)
                if (date != null && slot != null && recipeId != null) Delivered(date, slot, recipeId) else null
            }

    /**
     * Called when the tab is back on screen. A request still waiting at that point with no recipe
     * delivered means the user backed out of the importer or editor, so it is dropped — otherwise
     * the next recipe saved anywhere would be dropped onto a date nobody is thinking about.
     */
    fun dropIfUndelivered(handle: SavedStateHandle) {
        if (handle.get<String>(KEY_RECIPE) == null) clear(handle)
    }

    private fun clear(handle: SavedStateHandle) {
        handle[KEY_DATE] = null
        handle[KEY_SLOT] = null
        handle[KEY_RECIPE] = null
    }
}
