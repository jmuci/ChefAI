package com.tenmilelabs.chefai.mealplans.domain.repository

import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.PlannedIngredient
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface ShoppingListRepository {

    /** Ingredient rows for the given recipes; empty flow for an empty id list. */
    fun observeIngredientsForRecipes(recipeIds: List<UUID>): Flow<List<PlannedIngredient>>

    /** Item keys ticked off on this plan. See `ShoppingListBuilder.nameKey`. */
    fun observeCheckedItems(mealPlanId: UUID): Flow<Set<String>>

    suspend fun setChecked(mealPlanId: UUID, itemKey: String, checked: Boolean)

    suspend fun clearChecks(mealPlanId: UUID)
}
