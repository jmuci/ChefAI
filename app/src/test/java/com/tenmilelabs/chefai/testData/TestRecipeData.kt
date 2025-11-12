package com.tenmilelabs.chefai.testData

import com.tenmilelabs.chefai.data.mapper.toDomain
import com.tenmilelabs.chefai.data.source.local.room.IngredientEntity
import com.tenmilelabs.chefai.data.source.local.room.LabelEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.data.source.local.room.TagEntity
import com.tenmilelabs.chefai.data.source.local.room.UserEntity
import com.tenmilelabs.chefai.data.source.local.room.relations.RecipeWithDetails
import java.util.UUID

val testUser = UserEntity(
    uuid = UUID.randomUUID(),
    displayName = "Test User",
    email = "test@test.com",
    avatarUrl = null,
    updatedAt = System.currentTimeMillis(),
    deletedAt = null
)

val testIngredients = listOf(
    IngredientEntity(uuid = UUID.randomUUID(), displayName = "Flour", allergenId = null, sourcePrimaryId = null, updatedAt = System.currentTimeMillis(), deletedAt = null),
    IngredientEntity(uuid = UUID.randomUUID(), displayName = "Milk", allergenId = null, sourcePrimaryId = null, updatedAt = System.currentTimeMillis(), deletedAt = null),
    IngredientEntity(uuid = UUID.randomUUID(), displayName = "Eggs", allergenId = null, sourcePrimaryId = null, updatedAt = System.currentTimeMillis(), deletedAt = null)
)

val testLabels = listOf(
    LabelEntity(uuid = UUID.randomUUID(), displayName = "Breakfast", updatedAt = System.currentTimeMillis(), deletedAt = null),
    LabelEntity(uuid = UUID.randomUUID(), displayName = "Dessert", updatedAt = System.currentTimeMillis(), deletedAt = null),
    LabelEntity(uuid = UUID.randomUUID(), displayName = "Vegetarian", updatedAt = System.currentTimeMillis(), deletedAt = null)
)

val testTags = listOf(
    TagEntity(uuid = UUID.randomUUID(), displayName = "easy", updatedAt = System.currentTimeMillis(), deletedAt = null),
    TagEntity(uuid = UUID.randomUUID(), displayName = "quick", updatedAt = System.currentTimeMillis(), deletedAt = null),
    TagEntity(uuid = UUID.randomUUID(), displayName = "family-friendly", updatedAt = System.currentTimeMillis(), deletedAt = null)
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
    updatedAt = System.currentTimeMillis(),
    deletedAt = null
)

val recipeId2 = UUID.randomUUID()
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
    updatedAt = System.currentTimeMillis(),
    deletedAt = null
)

val recipeId3 = UUID.randomUUID()
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
    updatedAt = System.currentTimeMillis(),
    deletedAt = null
)
val  TEST_ROOM_RECIPES_LIST = listOf(recipeEntity1, recipeEntity2, recipeEntity3)

val testSteps = listOf(
    RecipeStepEntity(uuid = UUID.randomUUID(), recipeId = recipeId1, orderIndex = 0, instruction = "Mix dry ingredients.", updatedAt = System.currentTimeMillis(), deletedAt = null),
    RecipeStepEntity(uuid = UUID.randomUUID(), recipeId = recipeId1, orderIndex = 1, instruction = "Add wet ingredients.", updatedAt = System.currentTimeMillis(), deletedAt = null),
    RecipeStepEntity(uuid = UUID.randomUUID(), recipeId = recipeId1, orderIndex = 2, instruction = "Cook on a griddle.", updatedAt = System.currentTimeMillis(), deletedAt = null)
)
val recipeWithDetails1 = RecipeWithDetails(
    recipe = recipeEntity1,
    creator = testUser,
    steps = testSteps,
    ingredients = testIngredients,
    tags = testTags,
    labels = testLabels
)

val recipeWithDetails2 = RecipeWithDetails(
    recipe = recipeEntity2,
    creator = testUser,
    steps = emptyList(),
    ingredients = emptyList(),
    tags = emptyList(),
    labels = emptyList()
)

val recipeWithDetails3 = RecipeWithDetails(
    recipe = recipeEntity3,
    creator = testUser,
    steps = emptyList(),
    ingredients = emptyList(),
    tags = emptyList(),
    labels = emptyList()
)

val recipe1 = recipeWithDetails1.toDomain()
val recipe2 = recipeWithDetails2.toDomain()
val recipe3 = recipeWithDetails3.toDomain()

val TEST_DOMAIN_RECIPES_LIST = listOf(recipe1, recipe2, recipe3)
