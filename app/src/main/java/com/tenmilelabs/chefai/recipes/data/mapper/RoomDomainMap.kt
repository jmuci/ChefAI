package com.tenmilelabs.chefai.recipes.data.mapper

import com.tenmilelabs.chefai.core.data.local.room.AllergenEntity
import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.LabelEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.core.data.local.room.SourceClassificationEntity
import com.tenmilelabs.chefai.core.data.local.room.TagEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.relations.IngredientWithDetails
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeWithDetails
import com.tenmilelabs.chefai.core.domain.model.Allergen
import com.tenmilelabs.chefai.core.domain.model.Ingredient
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.model.SourceClassification
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.core.domain.model.User
import java.util.UUID

/**
 * Maps Room database entities to domain models.
 */

fun RecipeWithDetails.toDomain(ingredients: List<RecipeIngredient> = emptyList()): Recipe {
    return Recipe(
        uuid = recipe.uuid,
        title = recipe.title,
        description = recipe.description,
        imageUrl = recipe.imageUrl,
        imageUrlThumbnail = recipe.imageUrlThumbnail,
        prepTimeMinutes = recipe.prepTimeMinutes,
        cookTimeMinutes = recipe.cookTimeMinutes,
        servings = recipe.servings,
        creator = creator.toDomain(),
        recipeExternalUrl = recipe.recipeExternalUrl,
        ingredients = ingredients,
        steps = steps.map { it.toDomain() },
        tags = tags.map { it.toDomain() },
        labels = labels.map { it.toDomain() },
        updatedAt = recipe.updatedAt
    )
}

@JvmName("toFullRecipeDomain")
fun List<RecipeWithDetails>.toDomain() = map(RecipeWithDetails::toDomain)

fun RecipeWithDetails.toPreviewDomain(): RecipePreview {
    return RecipePreview(
        uuid = recipe.uuid,
        title = recipe.title,
        description = recipe.description,
        imageUrlThumbnail = recipe.imageUrlThumbnail,
        prepTimeMinutes = recipe.prepTimeMinutes,
        cookTimeMinutes = recipe.cookTimeMinutes,
        servings = recipe.servings,
        creatorId = creator.uuid,
        tags = tags.map { it.toDomain() },
        labels = labels.map { it.toDomain() },
    )
}

@JvmName("toRecipePreviewDomain")
fun List<RecipeWithDetails>.toRecipePreviewDomain() = map(RecipeWithDetails::toPreviewDomain)

fun UserEntity.toDomain(): User = User(
    uuid = uuid,
    displayName = displayName,
    email = email,
    avatarUrl = avatarUrl
)

fun IngredientWithDetails.toDomain(): Ingredient = Ingredient(
    uuid = ingredient.uuid,
    displayName = ingredient.displayName,
    allergen = allergen?.toDomain(),
    sourcePrimary = sourcePrimary?.toDomain()
)

/**
 * This is a partial mapping. For a complete domain model with allergen and source details,
 * the repository must use `IngredientWithDetails` and its `toDomain()` mapper.
 */
fun IngredientEntity.toDomain(): Ingredient = Ingredient(
    uuid = uuid,
    displayName = displayName,
    allergen = null,
    sourcePrimary = null
)

@JvmName("toIngredientDomain")
fun List<IngredientEntity>.toDomain() = map(IngredientEntity::toDomain)

fun RecipeStepEntity.toDomain(): RecipeStep = RecipeStep(
    uuid = uuid,
    orderIndex = orderIndex,
    instruction = instruction
)

fun TagEntity.toDomain(): Tag = Tag(
    uuid = uuid,
    displayName = displayName
)

@JvmName("toTagDomain")
fun List<TagEntity>.toDomain() = map(TagEntity::toDomain)
fun LabelEntity.toDomain(): Label = Label(
    uuid = uuid,
    displayName = displayName
)

@JvmName("toLabelDomain")
fun List<LabelEntity>.toDomain() = map(LabelEntity::toDomain)

fun AllergenEntity.toDomain(): Allergen = Allergen(
    uuid = uuid,
    displayName = displayName
)

fun SourceClassificationEntity.toDomain(): SourceClassification = SourceClassification(
    uuid = uuid,
    category = category,
    subcategory = subcategory
)

fun Recipe.toRoomEntity(): RecipeEntity = RecipeEntity(
    uuid = uuid,
    title = title,
    description = description,
    imageUrl = imageUrl,
    imageUrlThumbnail = imageUrlThumbnail,
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    servings = servings,
    creatorId = creator.uuid,
    recipeExternalUrl = recipeExternalUrl,
    updatedAt = updatedAt,
    deletedAt = null, // or appropriate value
    syncState = com.tenmilelabs.chefai.core.data.local.util.SyncState.PENDING
)

fun List<Recipe>.toRoomEntity() = map(Recipe::toRoomEntity)

fun Ingredient.toRoomEntity(): IngredientEntity = IngredientEntity(
    uuid = uuid,
    displayName = displayName,
    allergenId = allergen?.uuid,
    sourcePrimaryId = sourcePrimary?.uuid,
    updatedAt = System.currentTimeMillis(), // This should be handled properly
    deletedAt = null,
    syncState = com.tenmilelabs.chefai.core.data.local.util.SyncState.PENDING
)

fun Tag.toRoomEntity(): TagEntity = TagEntity(
    uuid = uuid,
    displayName = displayName,
    updatedAt = System.currentTimeMillis(),
    deletedAt = null,
    syncState = com.tenmilelabs.chefai.core.data.local.util.SyncState.PENDING
)

fun Label.toRoomEntity(): LabelEntity = LabelEntity(
    uuid = uuid,
    displayName = displayName,
    updatedAt = System.currentTimeMillis(),
    deletedAt = null,
    syncState = com.tenmilelabs.chefai.core.data.local.util.SyncState.PENDING
)

fun RecipeStep.toRoomEntity(recipeId: UUID): RecipeStepEntity =
    com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity(
        uuid = uuid,
        recipeId = recipeId,
        orderIndex = orderIndex,
        instruction = instruction,
        updatedAt = System.currentTimeMillis(),
        deletedAt = null,
        syncState = com.tenmilelabs.chefai.core.data.local.util.SyncState.PENDING
    )

fun RecipeIngredient.toRoomEntity(): IngredientEntity = IngredientEntity(
    uuid = ingredientId,
    displayName = ingredientDisplayName,
    allergenId = null, // Not available in RecipeIngredient
    sourcePrimaryId = null, // Not available in RecipeIngredient
    updatedAt = System.currentTimeMillis(),
    deletedAt = null,
    syncState = com.tenmilelabs.chefai.core.data.local.util.SyncState.PENDING
)

/**
 * Converts a RecipeIngredient domain model to a RecipeIngredientEntity cross-reference.
 * Used for persisting the many-to-many relationship between recipes and ingredients.
 */
fun RecipeIngredient.toCrossRef(recipeId: UUID): RecipeIngredientEntity =
    RecipeIngredientEntity(
        recipeId = recipeId,
        ingredientId = ingredientId,
        quantity = quantity,
        unit = unit,
        updatedAt = System.currentTimeMillis(),
        syncState = com.tenmilelabs.chefai.core.data.local.util.SyncState.PENDING
    )
