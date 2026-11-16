package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A line a person typed onto a meal plan's shopping list, as opposed to one derived from the
 * plan's recipes (those have no row here — see `ShoppingListBuilder`).
 *
 * **Local-only for now.** Unlike [ShoppingListCheckEntity] this is not [com.tenmilelabs.chefai.core
 * .data.local.util.SyncableEntity]: the sync payload has no field for it yet, so on a shared plan
 * these lines are visible only on the device that added them. Making them sync means a backend
 * `SyncGroceryListItem` variant plus `syncState`/`updatedAt`/`deletedAt` columns here.
 *
 * [checked] lives on this row rather than in `shopping_list_checks`, whose key is the normalised
 * ingredient name — a free-text line has no stable name key, and two people can add "milk".
 *
 * @property addedByUserId the session's user id when added; null if there was none to record.
 * @property addedByName the adder's display name at the time, denormalised so the initials still
 *   render when the household cache doesn't (or no longer does) list them. Null for an anonymous
 *   session, which has no name.
 */
@Entity(
    tableName = "shopping_list_custom_items",
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
data class CustomShoppingItemEntity(
    @PrimaryKey val uuid: UUID,
    val mealPlanId: UUID,
    val name: String,
    val checked: Boolean = false,
    /** Taken off the list but kept, so the screen's Undo can bring it back. */
    val removed: Boolean = false,
    val addedByUserId: UUID? = null,
    val addedByName: String? = null,
    val createdAt: Long,
)
