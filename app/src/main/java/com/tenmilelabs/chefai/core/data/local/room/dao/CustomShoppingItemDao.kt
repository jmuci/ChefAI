package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tenmilelabs.chefai.core.data.local.room.CustomShoppingItemEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Data Access Object for the shopping_list_custom_items table. */
@Dao
interface CustomShoppingItemDao {

    /** Every line still on the plan's list — a [setRemoved]'d one is left out. */
    @Query("SELECT * FROM shopping_list_custom_items WHERE mealPlanId = :mealPlanId AND removed = 0 ORDER BY createdAt")
    fun observeForPlan(mealPlanId: UUID): Flow<List<CustomShoppingItemEntity>>

    @Insert
    suspend fun insert(item: CustomShoppingItemEntity)

    @Query("UPDATE shopping_list_custom_items SET checked = :checked WHERE uuid = :uuid")
    suspend fun setChecked(uuid: UUID, checked: Boolean)

    @Query("UPDATE shopping_list_custom_items SET checked = 0 WHERE mealPlanId = :mealPlanId AND checked = 1")
    suspend fun uncheckAllForPlan(mealPlanId: UUID)

    @Query("UPDATE shopping_list_custom_items SET name = :name WHERE uuid = :uuid")
    suspend fun rename(uuid: UUID, name: String)

    /** Soft removal, so an Undo can restore the line exactly as it was. */
    @Query("UPDATE shopping_list_custom_items SET removed = :removed WHERE uuid = :uuid")
    suspend fun setRemoved(uuid: UUID, removed: Boolean)
}
