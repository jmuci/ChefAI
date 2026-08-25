package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

/**
 * One ticked-off line on a meal plan's shopping list.
 *
 * A row's presence *is* the tick; unticking deletes it. Keyed on the normalised ingredient name
 * (see `ShoppingListBuilder.nameKey`) rather than on `ingredients.uuid`, because the catalog can
 * hold several rows for the same ingredient — and rather than on a day/slot, because
 * [com.tenmilelabs.chefai.core.data.sync.SyncOrchestrator] regenerates `meal_plan_days` UUIDs
 * whenever a pull replaces a plan's days.
 *
 * Local-only, like the cooked timestamps on [MealPlanDayEntity]: the sync payload has no field for
 * it, so this deliberately carries no `syncState`/`updatedAt` and does not implement
 * [com.tenmilelabs.chefai.core.data.local.util.SyncableEntity].
 */
@Entity(
    tableName = "shopping_list_checks",
    primaryKeys = ["mealPlanId", "itemKey"],
    foreignKeys = [
        ForeignKey(
            entity = MealPlanEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["mealPlanId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("mealPlanId")],
)
data class ShoppingListCheckEntity(
    val mealPlanId: UUID,
    val itemKey: String,
    /** Epoch millis the item was ticked. Not shown anywhere yet; kept for a future "recently bought". */
    val checkedAt: Long,
)
