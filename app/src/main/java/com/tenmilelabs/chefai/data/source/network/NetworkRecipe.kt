package com.tenmilelabs.chefai.data.source.network

import kotlinx.serialization.Serializable

@Serializable
data class NetworkRecipe(
    val uuid: String,
    val title: String,
    val label: String, //TODO there should be a table for labels
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
