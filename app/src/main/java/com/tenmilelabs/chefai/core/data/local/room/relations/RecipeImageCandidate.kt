package com.tenmilelabs.chefai.core.data.local.room.relations

import java.util.UUID

/**
 * The three columns the image backfill needs to decide whether a recipe's picture is missing and
 * where to get it — a projection rather than the whole entity, since the sweep reads far more rows
 * than it acts on.
 */
data class RecipeImageCandidate(
    val recipeId: UUID,
    val imageUrl: String,
    val localImagePath: String?,
    /**
     * Non-null when the backend holds these bytes, in which case fetching them from there beats
     * re-deriving from [imageUrl] — and is the *only* option when [imageUrl] is blank, because a
     * user's own photo has no source to re-derive from.
     */
    val imageBlobId: String?,
)

/**
 * What the image *upload* sweep needs to decide whether a recipe's cached bytes are already on the
 * backend, and to notice when they have been replaced since.
 *
 * [imageBlobId] non-null means something was uploaded once; whether it is still the *current* image
 * is a question about the file on disk, which SQL cannot answer — see
 * [com.tenmilelabs.chefai.recipes.data.worker.RecipeImageUploadWorker].
 */
data class RecipeImageUploadCandidate(
    val recipeId: UUID,
    val localImagePath: String,
    val imageBlobId: String?,
    val uploadedFileModifiedAt: Long?,
)
