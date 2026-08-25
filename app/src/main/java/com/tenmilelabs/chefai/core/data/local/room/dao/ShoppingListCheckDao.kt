package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.ShoppingListCheckEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for the shopping_list_checks table.
 */
@Dao
interface ShoppingListCheckDao {

    @Query("SELECT itemKey FROM shopping_list_checks WHERE mealPlanId = :mealPlanId")
    fun observeCheckedKeys(mealPlanId: UUID): Flow<List<String>>

    @Upsert
    suspend fun upsert(check: ShoppingListCheckEntity)

    @Query("DELETE FROM shopping_list_checks WHERE mealPlanId = :mealPlanId AND itemKey = :itemKey")
    suspend fun delete(mealPlanId: UUID, itemKey: String)

    @Query("DELETE FROM shopping_list_checks WHERE mealPlanId = :mealPlanId")
    suspend fun clearForPlan(mealPlanId: UUID)
}
