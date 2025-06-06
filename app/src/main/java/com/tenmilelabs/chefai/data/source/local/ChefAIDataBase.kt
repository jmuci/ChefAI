package com.tenmilelabs.chefai.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RecipeEntity::class], version = 1, exportSchema = true)
abstract class ChefAIDataBase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
}