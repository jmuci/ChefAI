package com.tenmilelabs.chefai.data.mapper

import com.tenmilelabs.chefai.data.source.local.room.AllergenEntity
import com.tenmilelabs.chefai.data.source.local.room.IngredientEntity
import com.tenmilelabs.chefai.data.source.local.room.LabelEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.data.source.local.room.SourceClassificationEntity
import com.tenmilelabs.chefai.data.source.local.room.TagEntity
import com.tenmilelabs.chefai.data.source.local.room.UserEntity
import com.tenmilelabs.chefai.data.source.local.room.relations.IngredientWithDetails
import com.tenmilelabs.chefai.data.source.local.room.relations.RecipeWithDetails
import com.tenmilelabs.chefai.domain.model.Allergen
import com.tenmilelabs.chefai.domain.model.Ingredient
import com.tenmilelabs.chefai.domain.model.Label
import com.tenmilelabs.chefai.domain.model.Recipe
import com.tenmilelabs.chefai.domain.model.RecipeStep
import com.tenmilelabs.chefai.domain.model.SourceClassification
import com.tenmilelabs.chefai.domain.model.Tag
import com.tenmilelabs.chefai.domain.model.User

/**
 * Maps Room database entities to domain models.
 */

fun RecipeWithDetails.toDomain(): Recipe {
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
        ingredients = ingredients.map { it.toDomain() },
        steps = steps.map { it.toDomain() },
        tags = tags.map { it.toDomain() },
        labels = labels.map { it.toDomain() },
        updatedAt = recipe.updatedAt
    )
}

fun List<RecipeWithDetails>.toDomain() = map(RecipeWithDetails::toDomain)
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

fun RecipeStepEntity.toDomain(): RecipeStep = RecipeStep(
    uuid = uuid,
    orderIndex = orderIndex,
    instruction = instruction
)

fun TagEntity.toDomain(): Tag = Tag(
    uuid = uuid,
    displayName = displayName
)

fun LabelEntity.toDomain(): Label = Label(
    uuid = uuid,
    displayName = displayName
)

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
    syncState = com.tenmilelabs.chefai.data.source.local.util.SyncState.PENDING
)
fun List<Recipe>.toRoomEntity() = map(Recipe::toRoomEntity)

fun Ingredient.toRoomEntity(): IngredientEntity = IngredientEntity(
    uuid = uuid,
    displayName = displayName,
    allergenId = allergen?.uuid,
    sourcePrimaryId = sourcePrimary?.uuid,
    updatedAt = System.currentTimeMillis(), // This should be handled properly
    deletedAt = null,
    syncState = com.tenmilelabs.chefai.data.source.local.util.SyncState.PENDING
)
