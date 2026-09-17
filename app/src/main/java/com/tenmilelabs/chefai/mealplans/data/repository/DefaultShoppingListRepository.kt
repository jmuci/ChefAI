package com.tenmilelabs.chefai.mealplans.data.repository

import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.ShoppingListCheckDao
import com.tenmilelabs.chefai.core.data.local.room.ShoppingListCheckEntity
import com.tenmilelabs.chefai.core.data.local.room.relations.PlanIngredientRow
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.mealplans.domain.repository.CheckedItemsState
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
    private val syncScheduler: SyncScheduler,
) : ShoppingListRepository {

    override fun observeIngredientsForRecipes(recipeIds: List<UUID>): Flow<List<PlannedIngredient>> {
        if (recipeIds.isEmpty()) return flowOf(emptyList())
        return recipeDao.observeIngredientsForRecipes(recipeIds).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeCheckedState(mealPlanId: UUID): Flow<CheckedItemsState> =
        shoppingListCheckDao.observeCheckedRows(mealPlanId).map { rows ->
            CheckedItemsState(
                checkedKeys = rows.map { it.itemKey }.toSet(),
                checkedByUserIds = rows.mapNotNull { row ->
                    row.checkedBy?.let { row.itemKey to it }
                }.toMap(),
            )
        }

    /**
     * Always an upsert, never [ShoppingListCheckDao.delete] — an unchecked item is still on the
     * list (`checked = false`), not one that left it (`deletedAt`). See ADR-014 §5.2.
     *
     * Carries forward any existing row's `deletedAt` rather than defaulting it to null: a plain
     * checked/unchecked toggle must never resurrect an item a sync pull already marked as removed
     * from the plan server-side, even though [com.tenmilelabs.chefai.mealplans.domain.shoppinglist
     * .ShoppingListBuilder] can still be showing it (it derives the visible list from the plan's
     * current ingredients, independently of this row's `deletedAt`).
     */
    override suspend fun setChecked(mealPlanId: UUID, itemKey: String, checked: Boolean) {
        val now = System.currentTimeMillis()
        val existingDeletedAt = shoppingListCheckDao.getCheck(mealPlanId, itemKey)?.deletedAt
        shoppingListCheckDao.upsert(
            ShoppingListCheckEntity(
                mealPlanId = mealPlanId,
                itemKey = itemKey,
                checkedAt = now,
                checked = checked,
                updatedAt = now,
                deletedAt = existingDeletedAt,
                syncState = SyncState.PENDING,
            )
        )
        syncScheduler.requestMutationSync()
    }

    override suspend fun clearChecks(mealPlanId: UUID) {
        shoppingListCheckDao.clearForPlan(mealPlanId, state = SyncState.PENDING, updatedAt = System.currentTimeMillis())
        syncScheduler.requestMutationSync()
    }
}

private fun PlanIngredientRow.toDomain() = PlannedIngredient(
    recipeId = recipeId,
    recipeServings = recipeServings,
    displayName = ingredientDisplayName,
    quantity = quantity,
    unit = unit,
)
