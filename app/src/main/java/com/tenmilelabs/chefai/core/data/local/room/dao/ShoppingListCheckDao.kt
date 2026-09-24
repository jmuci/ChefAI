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

    /**
     * Every currently-checked, non-deleted item on the plan, with whoever last checked it if known
     * (`checkedBy` is null for a local toggle that hasn't yet round-tripped through a pull — see
     * [ShoppingListCheckEntity.checkedBy]'s doc). One query backing both the checked-set and the
     * "who checked this" attribution, so a single write to this table triggers one Room
     * invalidation for callers that need both, not two independently-recomputing ones.
     */
    @Query(
        "SELECT itemKey, checkedBy FROM shopping_list_checks " +
            "WHERE mealPlanId = :mealPlanId AND checked = 1 AND deletedAt IS NULL"
    )
    fun observeCheckedRows(mealPlanId: UUID): Flow<List<CheckedItemRow>>

    @Query("SELECT * FROM shopping_list_checks WHERE mealPlanId = :mealPlanId AND itemKey = :itemKey")
    suspend fun getCheck(mealPlanId: UUID, itemKey: String): ShoppingListCheckEntity?

    @Upsert
    suspend fun upsert(check: ShoppingListCheckEntity)

    @Query("DELETE FROM shopping_list_checks WHERE mealPlanId = :mealPlanId AND itemKey = :itemKey")
    suspend fun delete(mealPlanId: UUID, itemKey: String)

    /**
     * Unchecks every currently-checked item on the plan — an upsert-to-unchecked on each row, same
     * as a single [upsert]'d uncheck, so every affected row becomes `PENDING` and syncs to other
     * household members exactly like an individual toggle would. Excludes a soft-deleted row
     * (`deletedAt` set): that item already left the list server-side, and unchecking it here would
     * push a `checked = false` update that resurrects it. `checkedAt` is bumped to [updatedAt] too,
     * matching [ShoppingListCheckEntity.checkedAt]'s "last ticked" contract the way a single-item
     * uncheck (see the repository's `setChecked`) already does.
     */
    @Query(
        "UPDATE shopping_list_checks SET checked = 0, checkedBy = NULL, checkedAt = :updatedAt, " +
            "syncState = :state, updatedAt = :updatedAt " +
            "WHERE mealPlanId = :mealPlanId AND checked = 1 AND deletedAt IS NULL"
    )
    suspend fun clearForPlan(mealPlanId: UUID, state: SyncState, updatedAt: Long)

    @Query("SELECT * FROM shopping_list_checks WHERE syncState IN ('PENDING', 'DELETED')")
    suspend fun getAllDirty(): List<ShoppingListCheckEntity>

    /**
     * Marks a pushed row with the server's timestamp. Pass [expectedUpdatedAt] — the `updatedAt`
     * the row had when it was pushed — so an edit made while the push was in flight (which bumps
     * `updatedAt`) is left PENDING for the next push instead of being silently marked SYNCED.
     * @return the number of rows updated (0 when the row changed since it was pushed).
     */
    @Query(
        "UPDATE shopping_list_checks SET syncState = :state, updatedAt = :updatedAt " +
            "WHERE mealPlanId = :mealPlanId AND itemKey = :itemKey AND (:expectedUpdatedAt IS NULL OR updatedAt = :expectedUpdatedAt)"
    )
    suspend fun updateSyncState(mealPlanId: UUID, itemKey: String, state: SyncState, updatedAt: Long, expectedUpdatedAt: Long? = null): Int
}

data class CheckedItemRow(val itemKey: String, val checkedBy: UUID?)
