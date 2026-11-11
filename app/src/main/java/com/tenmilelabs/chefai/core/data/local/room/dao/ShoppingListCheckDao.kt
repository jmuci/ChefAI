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
 * hard-delete primitive (see its own test). The user removing a line is not that either: it is an
 * upsert with `removed = true`, because the list is derived live from the plan's own ingredients
 * (see `ShoppingListBuilder`) and a deleted row would let the item reappear.
 */
@Dao
interface ShoppingListCheckDao {

    /**
     * Every non-deleted row on the plan that carries any state worth applying to the derived list:
     * a tick (with whoever last checked it if known — `checkedBy` is null for a local toggle that
     * hasn't yet round-tripped through a pull, see [ShoppingListCheckEntity.checkedBy]), a rename,
     * or a removal. An unticked row with no edit is the same as no row, so it is left out. One
     * query backing all of it, so a single write triggers one Room invalidation for callers that
     * need the lot, not several independently-recomputing ones.
     */
    @Query(
        "SELECT itemKey, checked, checkedBy, displayNameOverride, removed FROM shopping_list_checks " +
            "WHERE mealPlanId = :mealPlanId AND deletedAt IS NULL " +
            "AND (checked = 1 OR removed = 1 OR displayNameOverride IS NOT NULL)"
    )
    fun observeItemStateRows(mealPlanId: UUID): Flow<List<CheckedItemRow>>

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
     * uncheck (see the repository's `setChecked`) already does. `updatedAt` follows nextUpdatedAt,
     * so each row's stamp sorts after the version it replaces.
     */
    @Query(
        "UPDATE shopping_list_checks SET checked = 0, checkedBy = NULL, checkedAt = :updatedAt, " +
            "syncState = :state, updatedAt = MAX(:updatedAt, updatedAt + 1) " +
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

data class CheckedItemRow(
    val itemKey: String,
    val checked: Boolean,
    val checkedBy: UUID?,
    val displayNameOverride: String?,
    val removed: Boolean,
)
