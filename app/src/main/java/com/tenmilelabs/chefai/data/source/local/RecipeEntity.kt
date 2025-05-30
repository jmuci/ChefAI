package com.tenmilelabs.chefai.data.source.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val uuid: String,
    val title: String,
    val label: String, //TODO there should be a table for labels
    val description: String,
    val prepTime: Int,
    val recipeUrl: String,
    val imageUrl: String
)