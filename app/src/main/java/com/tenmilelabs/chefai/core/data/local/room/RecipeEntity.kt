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
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    val creatorId: UUID,
    val recipeExternalUrl: String?,
    val privacy: RecipePrivacy = RecipePrivacy.PUBLIC,
    val version: Int = 1,
    override val updatedAt: Long,
    override val deletedAt: Long?,
    override val syncState: SyncState = SyncState.PENDING,
): SyncableEntity
