package com.tenmilelabs.chefai.data

import com.tenmilelabs.chefai.data.source.local.RecipeEntity

data class Recipe(
    val title: String,
    val label: String,
    val description: String,
    val prepTime: Int,
    val recipeUrl: String,
    val imageUrl: String,
    val uuid: String = "", // Default value for id not set.
)