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
 * [checked], [displayNameOverride] and [removed] are the source of truth for a shared plan's list once it round-trips through sync —
 * toggling, individually or via "uncheck all", is always an upsert to this row, never a delete; see
 * [com.tenmilelabs.chefai.mealplans.data.repository.DefaultShoppingListRepository].
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
    /**
     * Whoever last checked this item — the userId of the pushing caller, server-derived, never
     * meaningful for a pushed row (see `SyncGroceryListItem`'s doc). Null while unchecked, or while
     * a local toggle hasn't yet round-tripped through a pull to confirm who the server credits it
     * to.
     */
    val checkedBy: UUID? = null,
    /**
     * The user's own name for this line, replacing the one the builder derives from the recipes.
     * Display-only: [itemKey], the section and the quantity are untouched, so a rename never
     * orphans the tick and a rebuild of the list never undoes it. Null = no rename.
     */
    val displayNameOverride: String? = null,
    /**
     * The user removed this line from the list. Distinct from [deletedAt], which means the item
     * left the list because the plan changed (server-side tombstone). The row is kept, hidden, so
     * "Undo" is a flag flip and the removal syncs to household members like any other edit.
     */
    val removed: Boolean = false,
    override val updatedAt: Long = checkedAt,
    override val deletedAt: Long? = null,
    override val syncState: SyncState = SyncState.PENDING,
) : SyncableCrossRef
