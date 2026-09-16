package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.local.util.SyncableCrossRef
import java.util.UUID

/**
 * One line on a meal plan's shopping list and whether it's ticked off. Keyed on the normalised
 * ingredient name (see `ShoppingListBuilder.nameKey`) rather than on `ingredients.uuid`, because
 * the catalog can hold several rows for the same ingredient — and rather than on a day/slot,
 * because [com.tenmilelabs.chefai.core.data.sync.SyncOrchestrator] regenerates `meal_plan_days`
 * UUIDs whenever a pull replaces a plan's days.
 *
 * Implements [SyncableCrossRef] rather than [com.tenmilelabs.chefai.core.data.local.util
 * .SyncableEntity]: like [com.tenmilelabs.chefai.core.data.local.room.BookmarkedRecipeEntity],
 * this row's identity is the composite key above, not a single `uuid`, which is exactly what
 * `SyncableEntity` requires and `SyncableCrossRef` does not.
 *
 * **Transitional note (ADR-014):** [checked] is the eventual source of truth once a shared plan's
 * list round-trips through sync, but until the sync wiring for it lands, unticking an item still
 * *deletes* the row — see [com.tenmilelabs.chefai.mealplans.data.repository
 * .DefaultShoppingListRepository.setChecked]. Every row that exists today therefore always has
 * `checked = true`; the column exists ahead of its consumer so the migration only has to run once.
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
    indices = [Index("mealPlanId"), Index(value = ["syncState", "updatedAt"])],
)
data class ShoppingListCheckEntity(
    val mealPlanId: UUID,
    val itemKey: String,
    /** Epoch millis the item was last ticked. Not shown anywhere yet; kept for a future "recently bought". */
    val checkedAt: Long,
    val checked: Boolean = true,
    override val updatedAt: Long = checkedAt,
    override val deletedAt: Long? = null,
    override val syncState: SyncState = SyncState.PENDING,
) : SyncableCrossRef
