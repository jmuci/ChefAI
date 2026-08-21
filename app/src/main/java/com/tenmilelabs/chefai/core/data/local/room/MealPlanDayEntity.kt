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
