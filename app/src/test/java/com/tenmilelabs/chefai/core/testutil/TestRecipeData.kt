package com.tenmilelabs.chefai.core.testutil

import com.tenmilelabs.chefai.core.data.local.room.AllergenEntity
import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.LabelEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.room.SourceClassificationEntity
import com.tenmilelabs.chefai.core.data.local.room.TagEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeWithDetails
import com.tenmilelabs.chefai.core.data.local.util.decodeHex
import com.tenmilelabs.chefai.core.data.local.util.toUuid
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.recipes.data.mapper.toDomain
import java.util.UUID

private const val updatedTimeSt = 10_000_000L

val testUser = UserEntity(
    uuid = "F47AC10B58CC4372A5670E02B2C3D479".decodeHex().toUuid(),
    displayName = "Test User",
    email = "test@test.com",
    avatarUrl = "",
    updatedAt = updatedTimeSt,
    deletedAt = null
)

val testIngredients = listOf(
    IngredientEntity(
        uuid = UUID.randomUUID(),
        displayName = "Flour",
        allergenId = null,
        sourcePrimaryId = null,
        updatedAt = updatedTimeSt,
        deletedAt = null
    ),
    IngredientEntity(
        uuid = UUID.randomUUID(),
        displayName = "Milk",
        allergenId = null,
        sourcePrimaryId = null,
        updatedAt = updatedTimeSt,
        deletedAt = null
    ),
    IngredientEntity(
        uuid = UUID.randomUUID(),
        displayName = "Eggs",
        allergenId = null,
        sourcePrimaryId = null,
        updatedAt = updatedTimeSt,
        deletedAt = null
    )
)
val testLabels = listOf(
    LabelEntity(
        uuid = UUID.randomUUID(),
        displayName = "Breakfast",
        updatedAt = updatedTimeSt,
        deletedAt = null
    ),
    LabelEntity(
        uuid = UUID.randomUUID(),
        displayName = "Dessert",
        updatedAt = updatedTimeSt,
        deletedAt = null
    ),
    LabelEntity(
        uuid = UUID.randomUUID(),
        displayName = "Vegetarian",
        updatedAt = updatedTimeSt,
        deletedAt = null
    )
)

val testTags = listOf(
    TagEntity(
        uuid = UUID.randomUUID(),
        displayName = "easy",
        updatedAt = updatedTimeSt,
        deletedAt = null
    ),
    TagEntity(
        uuid = UUID.randomUUID(),
        displayName = "quick",
        updatedAt = updatedTimeSt,
        deletedAt = null
    ),
    TagEntity(
        uuid = UUID.randomUUID(),
        displayName = "family-friendly",
        updatedAt = updatedTimeSt,
        deletedAt = null
    )
)

val recipeId1: UUID = UUID.randomUUID()
val recipeEntity1 = RecipeEntity(
    uuid = recipeId1,
    title = "Pancakes",
    description = "Fluffy American-style pancakes.",
    imageUrl = "",
    imageUrlThumbnail = "",
    prepTimeMinutes = 10,
    cookTimeMinutes = 15,
    servings = 4,
    creatorId = testUser.uuid,
    recipeExternalUrl = null,
    updatedAt = updatedTimeSt,
    deletedAt = null
)

val recipeId2: UUID = UUID.randomUUID()
val recipeEntity2 = RecipeEntity(
    uuid = recipeId2,
    title = "French Toast",
    description = "Classic sweet French toast.",
    imageUrl = "",
    imageUrlThumbnail = "",
    prepTimeMinutes = 5,
    cookTimeMinutes = 10,
    servings = 2,
    creatorId = testUser.uuid,
    recipeExternalUrl = null,
    updatedAt = updatedTimeSt,
    deletedAt = null
)

val recipeId3: UUID = UUID.randomUUID()
val recipeEntity3 = RecipeEntity(
    uuid = recipeId3,
    title = "Omelette",
    description = "A quick and easy omelette.",
    imageUrl = "",
    imageUrlThumbnail = "",
    prepTimeMinutes = 5,
    cookTimeMinutes = 5,
    servings = 1,
    creatorId = testUser.uuid,
    recipeExternalUrl = null,
    updatedAt = updatedTimeSt,
    deletedAt = null
)
val TEST_ROOM_RECIPES_LIST = listOf(recipeEntity1, recipeEntity2, recipeEntity3)

//Cross - ref tables
val testRecipeIngredients = listOf(
    RecipeIngredientEntity(
        recipeId = recipeId1,
        ingredientId = testIngredients[0].uuid,
        quantity = 200.0,
        unit = "grams",
        updatedAt = updatedTimeSt
    ),
    RecipeIngredientEntity(
        recipeId = recipeId1,
        ingredientId = testIngredients[1].uuid,
        quantity = 300.0,
        unit = "grams",
        updatedAt = updatedTimeSt
    ),
    RecipeIngredientEntity(
        recipeId = recipeId2,
        ingredientId = testIngredients[2].uuid,
        quantity = 200.0,
        unit = "grams",
        updatedAt = updatedTimeSt
    ),
    RecipeIngredientEntity(
        recipeId = recipeId3,
        ingredientId = testIngredients[0].uuid,
        quantity = 200.0,
        unit = "grams",
        updatedAt = updatedTimeSt
    )
)

val allergenGluten = AllergenEntity(UUID.randomUUID(), "Gluten", updatedTimeSt, null)
val allergenNuts = AllergenEntity(UUID.randomUUID(), "Nuts", updatedTimeSt, null)
val allergenEggs = AllergenEntity(UUID.randomUUID(), "Eggs", updatedTimeSt, null)
val allergenMilk = AllergenEntity(UUID.randomUUID(), "Milk", updatedTimeSt, null)
val allergenPeanuts = AllergenEntity(UUID.randomUUID(), "Peanuts", updatedTimeSt, null)

val srcPork = SourceClassificationEntity(UUID.randomUUID(), "Animal", "Pork", updatedTimeSt, null)
val srcBeef = SourceClassificationEntity(UUID.randomUUID(), "Animal", "Beef", updatedTimeSt, null)
val srcFish = SourceClassificationEntity(UUID.randomUUID(), "Animal", "Fish", updatedTimeSt, null)
val srcVegetable =
    SourceClassificationEntity(UUID.randomUUID(), "Plant", "Vegetable", updatedTimeSt, null)
val srcFruit = SourceClassificationEntity(UUID.randomUUID(), "Plant", "Fruit", updatedTimeSt, null)

val ingredient1 = RecipeIngredient(
    testIngredients[0].uuid,
    testIngredients[0].displayName,
    200.0,
    "grams",
    allergenName = allergenGluten.displayName,
    srcCategory = srcVegetable.category,
    srcSubcategory = srcVegetable.subcategory
)
val ingredient2 = RecipeIngredient(
    testIngredients[1].uuid,
    testIngredients[1].displayName,
    300.0,
    "grams",
    allergenName = allergenNuts.displayName,
    srcCategory = srcVegetable.category,
    srcSubcategory = srcVegetable.subcategory
)
val ingredient3 = RecipeIngredient(
    testIngredients[2].uuid,
    testIngredients[2].displayName,
    200.0,
    "grams",
    allergenName = allergenEggs.displayName,
    srcCategory = srcVegetable.category,
    srcSubcategory = srcVegetable.subcategory
)

val testRecipeIngredients1 = listOf(ingredient1, ingredient2)
val testRecipeIngredients2 = listOf(ingredient3)


val recipeLabel1 = RecipeLabelCrossRef(
    recipeId = recipeId1,
    labelId = testLabels[0].uuid,
    updatedAt = System.currentTimeMillis(),
    deletedAt = null
)
val recipeLabel2 = RecipeLabelCrossRef(
    recipeId = recipeId1,
    labelId = testLabels[1].uuid,
    updatedAt = System.currentTimeMillis(),
    deletedAt = null
)
val recipeLabel3 = RecipeLabelCrossRef(
    recipeId = recipeId2,
    labelId = testLabels[0].uuid,
    updatedAt = System.currentTimeMillis(),
    deletedAt = null
)
val recipeLabel4 = RecipeLabelCrossRef(
    recipeId = recipeId3,
    labelId = testLabels[2].uuid,
    updatedAt = System.currentTimeMillis(),
    deletedAt = null
)
val testRecipeLabels = listOf(recipeLabel1, recipeLabel2, recipeLabel3, recipeLabel4)

val recipeTag1 = RecipeTagCrossRef(
    recipeId = recipeId1,
    tagId = testTags[0].uuid,
    updatedAt = System.currentTimeMillis(),
    deletedAt = null
)
val recipeTag2 = RecipeTagCrossRef(
    recipeId = recipeId1,
    tagId = testTags[1].uuid,
    updatedAt = System.currentTimeMillis(),
    deletedAt = null
)
val testRecipeTags = listOf(recipeTag1, recipeTag2)

val testSteps1 = listOf(
    RecipeStepEntity(
        uuid = UUID.randomUUID(),
        recipeId = recipeId1,
        orderIndex = 0,
        instruction = "Mix dry ingredients.",
        updatedAt = updatedTimeSt,
        deletedAt = null
    ),
    RecipeStepEntity(
        uuid = UUID.randomUUID(),
        recipeId = recipeId1,
        orderIndex = 1,
        instruction = "Add wet ingredients.",
        updatedAt = updatedTimeSt,
        deletedAt = null
    ),
    RecipeStepEntity(
        uuid = UUID.randomUUID(),
        recipeId = recipeId1,
        orderIndex = 2,
        instruction = "Cook on a griddle.",
        updatedAt = updatedTimeSt,
        deletedAt = null
    )
)
val testSteps2 = listOf(
    RecipeStepEntity(
        uuid = UUID.randomUUID(),
        recipeId = recipeId2,
        orderIndex = 0,
        instruction = "Mix dry ingredients.",
        updatedAt = updatedTimeSt,
        deletedAt = null
    ),
    RecipeStepEntity(
        uuid = UUID.randomUUID(),
        recipeId = recipeId2,
        orderIndex = 1,
        instruction = "Add wet ingredients.",
        updatedAt = updatedTimeSt,
        deletedAt = null
    ),
)
val testSteps3 = listOf(
    RecipeStepEntity(
        uuid = UUID.randomUUID(),
        recipeId = recipeId3,
        orderIndex = 0,
        instruction = "Mix dry ingredients.",
        updatedAt = updatedTimeSt,
        deletedAt = null
    ),
)

val recipeWithDetails1 = RecipeWithDetails(
    recipe = recipeEntity1,
    creator = testUser,
    steps = testSteps1,
    tags = testTags,
    labels = testLabels
)

val recipeWithDetails2 = RecipeWithDetails(
    recipe = recipeEntity2,
    creator = testUser,
    steps = testSteps2,
    tags = listOf(testTags[1]),
    labels = listOf(testLabels.last())
)

val recipeWithDetails3 = RecipeWithDetails(
    recipe = recipeEntity3,
    creator = testUser,
    steps = testSteps3,
    tags = listOf(testTags[0]),
    labels = listOf(testLabels.first())
)

val TEST_ROOM_RECIPES_WITH_DETAILS_LIST =
    listOf(recipeWithDetails1, recipeWithDetails2, recipeWithDetails3)


val recipe1 = recipeWithDetails1.toDomain(testRecipeIngredients1)
val recipe2 = recipeWithDetails2.toDomain(testRecipeIngredients2)
val recipe3 = recipeWithDetails3.toDomain(testRecipeIngredients1)

val TEST_DOMAIN_RECIPES_LIST = listOf(recipe1, recipe2, recipe3)

// RecipePreview test data - manually constructed to avoid mapper dependency issues
val recipePreview1 = RecipePreview(
    uuid = recipeId1,
    title = "Pancakes",
    description = "Fluffy American-style pancakes.",
    imageUrlThumbnail = "",
    prepTimeMinutes = 10,
    cookTimeMinutes = 15,
    servings = 4,
    creatorId = testUser.uuid,
    tags = testTags.map { Tag(it.uuid, it.displayName) },
    labels = testLabels.map { Label(it.uuid, it.displayName) }
)

val recipePreview2 = RecipePreview(
    uuid = recipeId2,
    title = "French Toast",
    description = "Classic sweet French toast.",
    imageUrlThumbnail = "",
    prepTimeMinutes = 5,
    cookTimeMinutes = 10,
    servings = 2,
    creatorId = testUser.uuid,
    tags = listOf(Tag(testTags[1].uuid, testTags[1].displayName)),
    labels = listOf(Label(testLabels.last().uuid, testLabels.last().displayName))
)

val recipePreview3 = RecipePreview(
    uuid = recipeId3,
    title = "Omelette",
    description = "A quick and easy omelette.",
    imageUrlThumbnail = "",
    prepTimeMinutes = 5,
    cookTimeMinutes = 5,
    servings = 1,
    creatorId = testUser.uuid,
    tags = listOf(Tag(testTags[0].uuid, testTags[0].displayName)),
    labels = listOf(Label(testLabels.first().uuid, testLabels.first().displayName))
)

val TEST_DOMAIN_RECIPE_PREVIEWS_LIST = listOf(recipePreview1, recipePreview2, recipePreview3)
