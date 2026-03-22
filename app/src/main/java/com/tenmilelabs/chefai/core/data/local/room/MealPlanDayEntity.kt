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
)
