package com.tenmilelabs.chefai.recipes.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class NetworkRecipe(
    val uuid: String,
    val title: String,
    val label: String,
    val description: String,
    val preparationTimeMinutes: Int,
    val recipeUrl: String,
    val imageUrl: String,
    val imageUrlThumbnail: String
)

@Serializable
data class NetworkRecipeList(
    val recipes: List<NetworkRecipe>
)
