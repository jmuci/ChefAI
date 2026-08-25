package com.tenmilelabs.chefai.mealplans.data.repository

import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.ShoppingListCheckDao
import com.tenmilelabs.chefai.core.data.local.room.ShoppingListCheckEntity
import com.tenmilelabs.chefai.core.data.local.room.relations.PlanIngredientRow
import com.tenmilelabs.chefai.mealplans.domain.repository.ShoppingListRepository
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.PlannedIngredient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultShoppingListRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val shoppingListCheckDao: ShoppingListCheckDao,
) : ShoppingListRepository {

    override fun observeIngredientsForRecipes(recipeIds: List<UUID>): Flow<List<PlannedIngredient>> {
        if (recipeIds.isEmpty()) return flowOf(emptyList())
        return recipeDao.observeIngredientsForRecipes(recipeIds).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeCheckedItems(mealPlanId: UUID): Flow<Set<String>> =
        shoppingListCheckDao.observeCheckedKeys(mealPlanId).map { it.toSet() }

    override suspend fun setChecked(mealPlanId: UUID, itemKey: String, checked: Boolean) {
        if (checked) {
            shoppingListCheckDao.upsert(
                ShoppingListCheckEntity(
                    mealPlanId = mealPlanId,
                    itemKey = itemKey,
                    checkedAt = System.currentTimeMillis(),
                )
            )
        } else {
            shoppingListCheckDao.delete(mealPlanId, itemKey)
        }
    }

    override suspend fun clearChecks(mealPlanId: UUID) = shoppingListCheckDao.clearForPlan(mealPlanId)
}

private fun PlanIngredientRow.toDomain() = PlannedIngredient(
    recipeId = recipeId,
    recipeServings = recipeServings,
    displayName = ingredientDisplayName,
    quantity = quantity,
    unit = unit,
)
