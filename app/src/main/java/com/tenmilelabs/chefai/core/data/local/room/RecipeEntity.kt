package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.local.util.SyncableEntity
import java.util.UUID


@Entity(
    tableName = "recipes",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["creatorId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("creatorId"),
        Index(value = ["syncState", "updatedAt"])
    ]
)
data class RecipeEntity(
    @PrimaryKey override val uuid: UUID,
    val title: String,
    val description: String,
    val imageUrl: String,
    val imageUrlThumbnail: String,
    /** Device-local only — deliberately absent from [com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeDto]. */
    val localImagePath: String? = null,
    /**
     * Content hash of this recipe's image as stored on the backend, or `null` if it has never been
     * uploaded. Server-owned: only the image upload endpoint sets it, and a push never changes it.
     *
     * Unlike [localImagePath] this *does* ride the sync payload, because it is exactly the pointer a
     * second device needs to fetch bytes it cannot re-derive. It is a hash, not an image — ADR-011
     * Decision 2's rule that bytes never travel in the JSON payload is intact.
     */
    val imageBlobId: String? = null,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    /**
     * Calories per serving, as published by the source or entered by hand — never computed from
     * ingredients. `null` means unknown, not zero. See [com.tenmilelabs.chefai.core.domain.model.Recipe.caloriesPerServing].
     */
    val caloriesPerServing: Int? = null,
    /** Protein grams per serving. Same provenance and null-means-unknown convention as [caloriesPerServing]. */
    val proteinGramsPerServing: Int? = null,
    val creatorId: UUID,
    val recipeExternalUrl: String?,
    val privacy: RecipePrivacy = RecipePrivacy.PUBLIC,
    val version: Int = 1,
    override val updatedAt: Long,
    override val deletedAt: Long?,
    override val syncState: SyncState = SyncState.PENDING,
): SyncableEntity
