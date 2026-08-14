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
    /** Download attempts, by [com.tenmilelabs.chefai.recipes.data.worker.RecipeImageBackfillWorker]. */
    val attempts: Int,
    val lastAttemptAt: Long,
    /**
     * Upload attempts, by [com.tenmilelabs.chefai.recipes.data.worker.RecipeImageUploadWorker].
     *
     * Counted separately from [attempts] so the two ladders cannot starve one another: a recipe
     * whose upload keeps failing must still be eligible for a download, and vice versa.
     */
    val uploadAttempts: Int = 0,
    val lastUploadAttemptAt: Long = 0,
    /**
     * `lastModified()` of the local image file at the moment it was last uploaded successfully.
     *
     * This is how a *replaced* photo is noticed. Files are keyed by recipe id, so picking a new
     * photo overwrites the same path in place and `imageBlobId` stays non-null — without this the
     * upload sweep would skip a recipe whose bytes had changed. Comparing mtime is what avoids
     * hashing every candidate file on every sweep; `RecipeImageStore.write` renames a temp file into
     * place, so the timestamp always moves.
     */
    val uploadedFileModifiedAt: Long? = null,
)
