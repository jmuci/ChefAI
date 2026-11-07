package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.local.util.SyncableEntity
import java.util.UUID

/**
 * A meal plan saved as a reusable template.
 *
 * The lineup lives in [daysJson] rather than a child table: it is a snapshot that is replaced whole
 * and never edited slot by slot, it syncs as one atomic unit, and — like `recipe_cook_events`
 * (ADR-017) — it has no foreign key to `recipes`, so deleting a recipe must not cascade into the
 * template. [sourceMealPlanId] likewise has no FK: the template outlives the dated plan.
 *
 * [heroImagePath] is a device-local file and is never part of the sync payload.
 */
@Entity(
    tableName = "saved_meal_plans",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("userId"),
        Index(value = ["syncState", "updatedAt"]),
    ]
)
data class SavedMealPlanEntity(
    @PrimaryKey override val uuid: UUID,
    val userId: UUID,
    val title: String,
    val preferencesJson: String,
    /** JSON list of `{dayIndex, lunchRecipeId, dinnerRecipeId}`; see `SavedMealPlanMapper`. */
    val daysJson: String,
    val sourceMealPlanId: UUID? = null,
    val heroImagePath: String? = null,
    val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
    override val syncState: SyncState = SyncState.PENDING,
) : SyncableEntity
