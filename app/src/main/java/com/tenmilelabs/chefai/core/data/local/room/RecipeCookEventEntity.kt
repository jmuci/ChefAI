package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.local.util.SyncableEntity
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
 * Syncs as an append-only event log (ADR-017), unlike the cooked marks on [MealPlanDayEntity],
 * which stay local. An event is immutable apart from being tombstoned: un-marking a meal sets
 * [deletedAt] (and goes `PENDING`) instead of deleting the row, and the server treats a tombstone
 * as final for that [uuid]. Re-marking the meal therefore mints a **new** uuid rather than
 * reviving the tombstone, so there is no resurrection and no last-writer-wins conflict. Scoped to
 * a user by the server (never shared with a household) and, locally, by `clearAllTables()` on an
 * account switch.
 */
@Entity(
    tableName = "recipe_cook_events",
    indices = [
        Index("recipeId"),
        Index("mealPlanId", "dayIndex", "slot"),
    ]
)
data class RecipeCookEventEntity(
    @PrimaryKey override val uuid: UUID,
    val recipeId: UUID,
    val mealPlanId: UUID,
    val dayIndex: Int,
    /** A [com.tenmilelabs.chefai.mealplans.domain.model.MealSlot] name. */
    val slot: String,
    /** Epoch millis the meal was marked cooked. */
    val cookedAt: Long,
    /** Local edit time (epoch millis) while `PENDING`; the server's stamp once `SYNCED`. */
    override val updatedAt: Long = cookedAt,
    /** Set when the meal was un-marked: a tombstone, kept so the deletion can sync. */
    override val deletedAt: Long? = null,
    override val syncState: SyncState = SyncState.PENDING,
) : SyncableEntity
