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
 * As of ADR-014's sync wiring, checking/unchecking an item — one at a time or all at once via
 * [clearForPlan] — is an ordinary upsert-to-unchecked, never [delete]: an unchecked item is still
 * an item on the list (`checked = false`), not one that has left it. [delete] remains as a plain
 * hard-delete primitive (see its own test) for a genuine removal, which nothing in this codebase
 * triggers yet — no "remove from list" UI exists, the list is derived live from the plan's own
 * ingredients (see `ShoppingListBuilder`).
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

    /**
     * Unchecks every currently-checked item on the plan — an upsert-to-unchecked on each row, same
     * as a single [upsert]'d uncheck, so every affected row becomes `PENDING` and syncs to other
     * household members exactly like an individual toggle would.
     */
    @Query(
        "UPDATE shopping_list_checks SET checked = 0, checkedBy = NULL, syncState = :state, updatedAt = :updatedAt " +
            "WHERE mealPlanId = :mealPlanId AND checked = 1"
    )
    suspend fun clearForPlan(mealPlanId: UUID, state: SyncState, updatedAt: Long)

    @Query("SELECT * FROM shopping_list_checks WHERE syncState IN ('PENDING', 'DELETED')")
    suspend fun getAllDirty(): List<ShoppingListCheckEntity>

    @Query(
        "UPDATE shopping_list_checks SET syncState = :state, updatedAt = :updatedAt " +
            "WHERE mealPlanId = :mealPlanId AND itemKey = :itemKey"
    )
    suspend fun updateSyncState(mealPlanId: UUID, itemKey: String, state: SyncState, updatedAt: Long)

    /**
     * `itemKey` → the userId of whoever last checked it, for every currently-checked item with a
     * known checker (a local toggle that hasn't yet round-tripped through a pull has `checkedBy =
     * null` and is excluded — see [ShoppingListCheckEntity.checkedBy]'s doc). Not `DISTINCT` on
     * anything: `(mealPlanId, itemKey)` is this table's primary key, so one row per key already.
     */
    @Query(
        "SELECT itemKey, checkedBy FROM shopping_list_checks " +
            "WHERE mealPlanId = :mealPlanId AND checked = 1 AND deletedAt IS NULL AND checkedBy IS NOT NULL"
    )
    fun observeCheckedByUserIds(mealPlanId: UUID): Flow<List<CheckedByRow>>
}

data class CheckedByRow(val itemKey: String, val checkedBy: UUID)
