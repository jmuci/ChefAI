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
)
