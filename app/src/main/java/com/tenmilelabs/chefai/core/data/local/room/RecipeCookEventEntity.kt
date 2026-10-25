package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One time a recipe was cooked, written when a meal-plan slot holding it is marked cooked.
 *
 * This is the durable "cooked before" history behind the Recipes tab's filter. It deliberately has
 * no foreign key to `meal_plans`, `meal_plan_days` or `recipes`: the cooked marks on
 * [MealPlanDayEntity] die with their plan (`ON DELETE CASCADE`) and with a pulled plan that swaps a
 * slot's recipe, but having cooked something is a fact about the user, not about the plan it
 * happened in.
 *
 * The slot is identified by ([mealPlanId], [dayIndex], [slot]) rather than a day uuid, because a
 * regenerated schedule reissues day rows with new ids while keeping the position (see
 * [carryForwardCookedMarks]). That triple plus [recipeId] is what un-marking a meal deletes, so a
 * mis-tap that is undone leaves no history behind.
 *
 * Local-only, like the cooked marks themselves: nothing here is in the sync payload. Wiped with
 * every other table on an account switch (`clearAllTables()`), which is what scopes it to a user.
 */
@Entity(
    tableName = "recipe_cook_events",
    indices = [
        Index("recipeId"),
        Index("mealPlanId", "dayIndex", "slot"),
    ]
)
data class RecipeCookEventEntity(
    @PrimaryKey val uuid: UUID,
    val recipeId: UUID,
    val mealPlanId: UUID,
    val dayIndex: Int,
    /** A [com.tenmilelabs.chefai.mealplans.domain.model.MealSlot] name. */
    val slot: String,
    /** Epoch millis the meal was marked cooked. */
    val cookedAt: Long,
)
