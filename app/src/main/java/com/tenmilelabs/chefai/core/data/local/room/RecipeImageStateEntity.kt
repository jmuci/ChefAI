package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Device-local bookkeeping for a recipe's cached image — how many times fetching it has been tried,
 * and when it was last attempted.
 *
 * Deliberately a sibling table rather than columns on [RecipeEntity]. Every device-local column added
 * to `recipes` is another field [com.tenmilelabs.chefai.core.data.sync.mapper.toRecipeEntity] has to
 * thread through by hand, or the full-row `@Upsert` on the pull path silently resets it — the exact
 * bug that made a freshly cached `localImagePath` vanish moments after import. A separate table is
 * structurally immune, and gives blob state somewhere to grow without touching the synced row.
 *
 * A row exists only while an image is unresolved: the backfill worker creates it on the first failed
 * attempt and deletes it once the bytes land.
 */
@Entity(
    tableName = "recipe_image_state",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RecipeImageStateEntity(
    @PrimaryKey val recipeId: UUID,
    val attempts: Int,
    val lastAttemptAt: Long,
)
