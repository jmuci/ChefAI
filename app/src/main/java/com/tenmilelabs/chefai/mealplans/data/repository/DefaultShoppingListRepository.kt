package com.tenmilelabs.chefai.mealplans.data.repository

import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.ShoppingListCheckDao
import com.tenmilelabs.chefai.core.data.local.room.ShoppingListCheckEntity
import com.tenmilelabs.chefai.core.data.local.room.relations.PlanIngredientRow
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.local.util.nextUpdatedAt
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

/** The backend's limit on `displayNameOverride`; a longer rename would be rejected on push. */
private const val MAX_NAME_LENGTH = 256

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
        shoppingListCheckDao.observeItemStateRows(mealPlanId).map { rows ->
            val checked = rows.filter { it.checked }
            CheckedItemsState(
                checkedKeys = checked.map { it.itemKey }.toSet(),
                checkedByUserIds = checked.mapNotNull { row ->
                    row.checkedBy?.let { row.itemKey to it }
                }.toMap(),
                displayNameOverrides = rows.mapNotNull { row ->
                    row.displayNameOverride?.let { row.itemKey to it }
                }.toMap(),
                removedKeys = rows.filter { it.removed }.map { it.itemKey }.toSet(),
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
     * current ingredients, independently of this row's `deletedAt`). Likewise carries forward the
     * row's rename and removal.
     */
    override suspend fun setChecked(mealPlanId: UUID, itemKey: String, checked: Boolean) {
        val now = System.currentTimeMillis()
        upsertItem(mealPlanId, itemKey, now) { existing ->
            existing.copy(checkedAt = now, checked = checked)
        }
    }

    override suspend fun renameItem(mealPlanId: UUID, itemKey: String, displayName: String?) {
        val name = displayName?.trim()?.take(MAX_NAME_LENGTH)?.takeIf { it.isNotEmpty() }
        upsertItem(mealPlanId, itemKey, System.currentTimeMillis()) { existing ->
            existing.copy(displayNameOverride = name)
        }
    }

    override suspend fun setRemoved(mealPlanId: UUID, itemKey: String, removed: Boolean) {
        upsertItem(mealPlanId, itemKey, System.currentTimeMillis()) { existing ->
            existing.copy(removed = removed)
        }
    }

    /**
     * Read-modify-write of one item's row: [edit] sees the stored row, or an untouched unticked
     * one when the item has never been edited, and everything it doesn't change is carried over.
     * Stamps `updatedAt` after the version it replaces and queues a sync.
     */
    private suspend fun upsertItem(
        mealPlanId: UUID,
        itemKey: String,
        now: Long,
        edit: (ShoppingListCheckEntity) -> ShoppingListCheckEntity,
    ) {
        val existing = shoppingListCheckDao.getCheck(mealPlanId, itemKey)
        val base = existing ?: ShoppingListCheckEntity(
            mealPlanId = mealPlanId,
            itemKey = itemKey,
            checkedAt = now,
            checked = false,
        )
        shoppingListCheckDao.upsert(
            edit(base).copy(
                updatedAt = nextUpdatedAt(existing?.updatedAt, now),
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
