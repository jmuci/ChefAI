package com.tenmilelabs.chefai.mealplans.domain.repository

import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.PlannedIngredient
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface ShoppingListRepository {

    /** Ingredient rows for the given recipes; empty flow for an empty id list. */
    fun observeIngredientsForRecipes(recipeIds: List<UUID>): Flow<List<PlannedIngredient>>

    /**
     * The plan's per-item state — ticks and who ticked them, renames, removals — as one flow — a caller that needs both (as
     * [com.tenmilelabs.chefai.mealplans.ui.shoppinglist.ShoppingListViewModel] does) gets a single
     * recompute per change instead of two independently-firing ones.
     */
    fun observeCheckedState(mealPlanId: UUID): Flow<CheckedItemsState>

    suspend fun setChecked(mealPlanId: UUID, itemKey: String, checked: Boolean)

    suspend fun clearChecks(mealPlanId: UUID)

    /**
     * Gives the line a display name of the user's choosing; `null` restores the derived one. The
     * item's key — and so its tick — is unaffected.
     */
    suspend fun renameItem(mealPlanId: UUID, itemKey: String, displayName: String?)

    /** Hides ([removed] = true) or restores a line. Its tick and rename are kept across both. */
    suspend fun setRemoved(mealPlanId: UUID, itemKey: String, removed: Boolean)
}

/**
 * @property checkedKeys Item keys ticked off on this plan. See `ShoppingListBuilder.nameKey`.
 * @property checkedByUserIds Item key -> userId of whoever last checked it, for checked items with
 *   a known checker (a local toggle that hasn't yet round-tripped through a pull has none yet).
 * @property displayNameOverrides Item key -> the name the user gave the line.
 * @property removedKeys Item keys the user removed from the list.
 */
data class CheckedItemsState(
    val checkedKeys: Set<String>,
    val checkedByUserIds: Map<String, UUID>,
    val displayNameOverrides: Map<String, String> = emptyMap(),
    val removedKeys: Set<String> = emptySet(),
)
