package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tenmilelabs.chefai.core.data.local.room.AllergenEntity
import com.tenmilelabs.chefai.core.data.local.room.BookmarkedRecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.LabelEntity
import com.tenmilelabs.chefai.core.data.local.room.MealPlanDayEntity
import com.tenmilelabs.chefai.core.data.local.room.MealPlanEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeDraftEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.room.SourceClassificationEntity
import com.tenmilelabs.chefai.core.data.local.room.SyncMetadataEntity
import com.tenmilelabs.chefai.core.data.local.room.TagEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.UuidConverters

@Database(
    entities = [
        AllergenEntity::class,
        BookmarkedRecipeEntity::class,
        IngredientEntity::class,
        LabelEntity::class,
        MealPlanEntity::class,
        MealPlanDayEntity::class,
        RecipeDraftEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        RecipeLabelCrossRef::class,
        RecipeStepEntity::class,
        RecipeTagCrossRef::class,
        SourceClassificationEntity::class,
        SyncMetadataEntity::class,
        TagEntity::class,
        UserEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(UuidConverters::class)
abstract class ChefAIDataBase : RoomDatabase() {
    abstract fun allergenDao(): AllergenDao
    abstract fun bookmarkedRecipeDao(): BookmarkedRecipeDao
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
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun recipeDraftDao(): RecipeDraftDao
    abstract fun mealPlanDao(): MealPlanDao
}

/**
 * Adds [com.tenmilelabs.chefai.core.data.local.room.RecipeEntity.localImagePath] and
 * [com.tenmilelabs.chefai.core.data.local.room.RecipeDraftEntity.localImagePath] — the on-device
 * path to an image downloaded at import time, for sources whose CDN blocks a plain HTTP client. See
 * ADR-010 Decision 6.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN localImagePath TEXT")
        db.execSQL("ALTER TABLE recipe_drafts ADD COLUMN localImagePath TEXT")
    }
}
