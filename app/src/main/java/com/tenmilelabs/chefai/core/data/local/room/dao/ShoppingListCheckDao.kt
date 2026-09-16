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
 * [delete] is still the uncheck path today — see [ShoppingListCheckEntity]'s "Transitional note".
 * [getCheck]/[getAllDirty]/[updateSyncState] exist ahead of their consumer, added now so the ADR-014
 * migration only has to run once; they are unused until the sync wiring for a shared list lands.
 */
@Dao
interface ShoppingListCheckDao {

    @Query("SELECT itemKey FROM shopping_list_checks WHERE mealPlanId = :mealPlanId")
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
