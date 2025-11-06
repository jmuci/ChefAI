package com.tenmilelabs.chefai.domain.model

data class Recipe(
    val title: String,
    val label: String,
    val description: String,
    val prepTime: Int,
    val recipeUrl: String,
    val imageUrl: String,
    val thumbnailUrl: String,
    val uuid: String = "", // Default value for id not set.
)