package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "meal_plan_days",
    foreignKeys = [
        ForeignKey(
            entity = MealPlanEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["mealPlanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("mealPlanId")
    ]
)
data class MealPlanDayEntity(
    @PrimaryKey val uuid: UUID,
    val mealPlanId: UUID,
    val dayIndex: Int,
    val dinnerRecipeId: UUID?,
    val lunchRecipeId: UUID?,
    /**
     * Epoch millis the user marked dinner cooked, `null` while outstanding.
     *
     * Local-only for now: the sync payload carries no cooked state, so
     * [com.tenmilelabs.chefai.core.data.sync.SyncOrchestrator] carries these forward by `dayIndex`
     * when a pull replaces a plan's days.
     */
    val dinnerCookedAt: Long? = null,
    /** Epoch millis the user marked lunch cooked. See [dinnerCookedAt]. */
    val lunchCookedAt: Long? = null,
)

/**
 * Carries this day's cooked marks forward from [previousByDayIndex] onto a freshly-assigned day,
 * keyed by [MealPlanDayEntity.dayIndex] rather than [MealPlanDayEntity.uuid] — a regenerated
 * schedule reissues day rows with new ids, but the position in the week is what the user marked
 * cooked. A mark only survives if the slot still holds the *same* recipe: when regeneration assigns
 * a different meal to a day, that meal has not been cooked.
 *
 * Shared by every place a plan's days get wholesale-replaced:
 * [com.tenmilelabs.chefai.core.data.sync.SyncOrchestrator]'s pull-applied regeneration and
 * [com.tenmilelabs.chefai.mealplans.data.repository.DefaultMealPlanRepository]'s stateless-generation
 * path.
 */
fun MealPlanDayEntity.carryForwardCookedMarks(
    previousByDayIndex: Map<Int, MealPlanDayEntity>
): MealPlanDayEntity {
    val previous = previousByDayIndex[dayIndex] ?: return this
    return copy(
        dinnerCookedAt = previous.dinnerCookedAt.takeIf { previous.dinnerRecipeId == dinnerRecipeId },
        lunchCookedAt = previous.lunchCookedAt.takeIf { previous.lunchRecipeId == lunchRecipeId },
    )
}
