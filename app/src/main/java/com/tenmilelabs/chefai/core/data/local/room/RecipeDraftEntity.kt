package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity for persisting in-progress recipe drafts.
 *
 * Lists (ingredients, steps, tags, labels) are stored as JSON strings to avoid
 * complex relational modeling for transient data. Serialization is handled at
 * the mapper level, not with Room TypeConverters.
 */
@Entity(tableName = "recipe_drafts")
data class RecipeDraftEntity(
    @PrimaryKey val recipeId: UUID,
    val isNewRecipe: Boolean,
    val title: String,
    val description: String,
    val imageUrl: String,
    val selectedImageUri: String?,
    val localImagePath: String?,
    val prepTimeMinutes: String,
    val cookTimeMinutes: String,
    val servings: String,
    val externalUrl: String,
    val ingredientsJson: String,
    val stepsJson: String,
    val tagsJson: String,
    val labelsJson: String,
    val version: Int,
    val updatedAt: Long,
)
