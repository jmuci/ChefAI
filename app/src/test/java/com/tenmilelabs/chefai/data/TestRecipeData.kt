package com.tenmilelabs.chefai.data

val recipe1 = Recipe(
    uuid = "1",
    title = "Title1",
    description = "Description1",
    imageUrl = "ImageUrl1",
    thumbnailUrl = "ThumbnailUrl1",
    prepTime = 10,
    recipeUrl = "https://example.com/recipe1",
    label = "Label1"
)
val recipe2 = Recipe(
    uuid = "2",
    title = "Title2",
    description = "Description2",
    imageUrl = "ImageUrl2",
    thumbnailUrl = "ThumbnailUrl1",
    prepTime = 10,
    recipeUrl = "https://example.com/recipe2",
    label = "Label2"
)
val recipe3 = Recipe(
    uuid = "3",
    title = "Title3",
    description = "Description3",
    imageUrl = "ImageUrl3",
    thumbnailUrl = "ThumbnailUrl3",
    prepTime = 10,
    recipeUrl = "https://example.com/recipe3",
    label = "Label3"
)

val recipe4 = Recipe(
    uuid = "4",
    title = "Title4",
    description = "Description4",
    imageUrl = "ImageUrl4",
    thumbnailUrl = "ThumbnailUrl4",
    prepTime = 10,
    recipeUrl = "https://example.com/recipe3",
    label = "Label4"
)

public val TEST_RECIPES_LIST = listOf(recipe1, recipe2, recipe3)