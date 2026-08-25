package com.tenmilelabs.chefai.mealplans.data.repository

import com.tenmilelabs.chefai.mealplans.domain.repository.ShoppingListRepository
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.PlannedIngredient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeShoppingListRepository : ShoppingListRepository {

    private val ingredientsByRecipe = mutableMapOf<UUID, List<PlannedIngredient>>()
    private val ingredientsTrigger = MutableStateFlow(0)
    private val checkedKeys = MutableStateFlow<Map<UUID, Set<String>>>(emptyMap())

    var shouldThrowOnObserveIngredients: Boolean = false
    var shouldThrowOnSetChecked: Boolean = false

    fun setIngredientsForRecipe(recipeId: UUID, ingredients: List<PlannedIngredient>) {
        ingredientsByRecipe[recipeId] = ingredients
        ingredientsTrigger.value++
    }

    override fun observeIngredientsForRecipes(recipeIds: List<UUID>): Flow<List<PlannedIngredient>> {
        if (shouldThrowOnObserveIngredients) throw RuntimeException("Fake ingredients error")
        val idSet = recipeIds.toHashSet()
        return ingredientsTrigger.map {
            ingredientsByRecipe.filterKeys { it in idSet }.values.flatten()
        }
    }

    override fun observeCheckedItems(mealPlanId: UUID): Flow<Set<String>> =
        checkedKeys.map { it[mealPlanId].orEmpty() }

    override suspend fun setChecked(mealPlanId: UUID, itemKey: String, checked: Boolean) {
        if (shouldThrowOnSetChecked) throw RuntimeException("Fake setChecked error")
        val current = checkedKeys.value[mealPlanId].orEmpty()
        val updated = if (checked) current + itemKey else current - itemKey
        checkedKeys.value = checkedKeys.value + (mealPlanId to updated)
    }

    override suspend fun clearChecks(mealPlanId: UUID) {
        checkedKeys.value = checkedKeys.value + (mealPlanId to emptySet())
    }
}
