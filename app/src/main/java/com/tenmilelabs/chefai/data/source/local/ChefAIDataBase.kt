package com.tenmilelabs.chefai.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tenmilelabs.chefai.data.source.local.room.AllergenEntity
import com.tenmilelabs.chefai.data.source.local.room.IngredientEntity
import com.tenmilelabs.chefai.data.source.local.room.LabelEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.data.source.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.data.source.local.room.SourceClassificationEntity
import com.tenmilelabs.chefai.data.source.local.room.TagEntity
import com.tenmilelabs.chefai.data.source.local.room.UserEntity

@Database(
    entities = [
        AllergenEntity::class,
        IngredientEntity::class,
        LabelEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        RecipeLabelCrossRef::class,
        RecipeStepEntity::class,
        RecipeTagCrossRef::class,
        SourceClassificationEntity::class,
        TagEntity::class,
        UserEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ChefAIDataBase : RoomDatabase() {
    abstract fun allergenDao(): AllergenDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun labelDao(): LabelDao
    abstract fun recipeDao(): RecipeDao
    abstract fun recipeIngredientDao(): RecipeIngredientDao
    abstract fun recipeStepDao(): RecipeStepDao
    abstract fun sourceClassificationDao(): SourceClassificationDao
    abstract fun tagDao(): TagDao
    abstract fun userDao(): UserDao
    abstract fun recipeTagCrossRefDao(): RecipeTagCrossRefDao
    abstract fun recipeLabelCrossRefDao(): RecipeLabelCrossRefDao
}
