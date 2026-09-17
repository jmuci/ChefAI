package com.tenmilelabs.chefai.mealplans.domain.repository

import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.PlannedIngredient
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface ShoppingListRepository {

    /** Ingredient rows for the given recipes; empty flow for an empty id list. */
    fun observeIngredientsForRecipes(recipeIds: List<UUID>): Flow<List<PlannedIngredient>>

    /**
     * The plan's checked items and who checked them, as one flow — a caller that needs both (as
     * [com.tenmilelabs.chefai.mealplans.ui.shoppinglist.ShoppingListViewModel] does) gets a single
     * recompute per change instead of two independently-firing ones.
     */
    fun observeCheckedState(mealPlanId: UUID): Flow<CheckedItemsState>

    suspend fun setChecked(mealPlanId: UUID, itemKey: String, checked: Boolean)

    suspend fun clearChecks(mealPlanId: UUID)
}

/**
 * @property checkedKeys Item keys ticked off on this plan. See `ShoppingListBuilder.nameKey`.
 * @property checkedByUserIds Item key -> userId of whoever last checked it, for checked items with
 *   a known checker (a local toggle that hasn't yet round-tripped through a pull has none yet).
 */
data class CheckedItemsState(
    val checkedKeys: Set<String>,
    val checkedByUserIds: Map<String, UUID>,
)
