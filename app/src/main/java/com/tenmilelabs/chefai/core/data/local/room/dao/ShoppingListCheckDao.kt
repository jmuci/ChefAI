package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.ShoppingListCheckEntity
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for the shopping_list_checks table.
 *
 * As of ADR-014's sync wiring, checking/unchecking an item is an ordinary upsert either way —
 * `DefaultShoppingListRepository.setChecked` never calls [delete] anymore, since an unchecked item
 * is still an item on the list (`checked = false`), not one that has left it. [delete]/
 * [clearForPlan] remain as plain hard-delete primitives (see their own tests) for a genuine
 * removal, which nothing in this codebase triggers yet — no "remove from list" UI exists, the list
 * is derived live from the plan's own ingredients (see `ShoppingListBuilder`).
 *
 * **Known gap**: [clearForPlan] (via `ShoppingListRepository.clearChecks`, the "uncheck all"
 * action) is still a hard delete, not an upsert-to-unchecked — so unlike a single [upsert]'d
 * uncheck, "uncheck all" does not yet produce PENDING rows for [getAllDirty] to push, and won't
 * sync to other household members. Follow-up, not fixed here.
 */
@Dao
interface ShoppingListCheckDao {

    @Query(
        "SELECT itemKey FROM shopping_list_checks " +
            "WHERE mealPlanId = :mealPlanId AND checked = 1 AND deletedAt IS NULL"
    )
    fun observeCheckedKeys(mealPlanId: UUID): Flow<List<String>>

    @Query("SELECT * FROM shopping_list_checks WHERE mealPlanId = :mealPlanId AND itemKey = :itemKey")
    suspend fun getCheck(mealPlanId: UUID, itemKey: String): ShoppingListCheckEntity?

    @Upsert
    suspend fun upsert(check: ShoppingListCheckEntity)

    @Query("DELETE FROM shopping_list_checks WHERE mealPlanId = :mealPlanId AND itemKey = :itemKey")
    suspend fun delete(mealPlanId: UUID, itemKey: String)

    @Query("DELETE FROM shopping_list_checks WHERE mealPlanId = :mealPlanId")
    suspend fun clearForPlan(mealPlanId: UUID)

    @Query("SELECT * FROM shopping_list_checks WHERE syncState IN ('PENDING', 'DELETED')")
    suspend fun getAllDirty(): List<ShoppingListCheckEntity>

    @Query(
        "UPDATE shopping_list_checks SET syncState = :state, updatedAt = :updatedAt " +
            "WHERE mealPlanId = :mealPlanId AND itemKey = :itemKey"
    )
    suspend fun updateSyncState(mealPlanId: UUID, itemKey: String, state: SyncState, updatedAt: Long)
}
