package com.tenmilelabs.chefai.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RecipeEntity::class], version = 1, exportSchema = false)
abstract class ChefAIDataBase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
}