package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tenmilelabs.chefai.core.data.local.room.RecipeCookEventEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface RecipeCookEventDao {

    @Insert
    suspend fun insert(event: RecipeCookEventEntity)

    /**
     * Removes the event for one slot's [recipeId]. Scoped by recipe so un-marking a slot that has
     * since been given a different recipe cannot erase the history of the one cooked before.
     */
    @Query(
        """
        DELETE FROM recipe_cook_events
        WHERE mealPlanId = :mealPlanId AND dayIndex = :dayIndex AND slot = :slot AND recipeId = :recipeId
        """
    )
    suspend fun deleteForSlot(mealPlanId: UUID, dayIndex: Int, slot: String, recipeId: UUID)

    /** Ids of every recipe cooked at least once. */
    @Query("SELECT DISTINCT recipeId FROM recipe_cook_events")
    fun observeCookedRecipeIds(): Flow<List<UUID>>
}
