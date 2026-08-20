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
import com.tenmilelabs.chefai.core.data.local.room.RecipeImageStateEntity
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
        RecipeImageStateEntity::class,
        RecipeIngredientEntity::class,
        RecipeLabelCrossRef::class,
        RecipeStepEntity::class,
        RecipeTagCrossRef::class,
        SourceClassificationEntity::class,
        SyncMetadataEntity::class,
        TagEntity::class,
        UserEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(UuidConverters::class)
abstract class ChefAIDataBase : RoomDatabase() {
    abstract fun allergenDao(): AllergenDao
    abstract fun bookmarkedRecipeDao(): BookmarkedRecipeDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun labelDao(): LabelDao
    abstract fun recipeDao(): RecipeDao
    abstract fun recipeImageStateDao(): RecipeImageStateDao
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

/**
 * Retires `recipe_drafts.selectedImageUri` and adds
 * [com.tenmilelabs.chefai.core.data.local.room.RecipeImageStateEntity]'s table.
 *
 * `selectedImageUri` held the raw `content://` string from the photo picker, which nothing ever read
 * back — `RecipeDraft.toRecipe()` dropped it on save, and the picker's read grant did not survive
 * process death anyway. A picked photo is now copied into `RecipeImageStore` at pick time and lives
 * in `localImagePath` alongside a scraped one, so the column has no remaining job. See ADR-011.
 *
 * The column is removed by rebuilding the table rather than with `ALTER TABLE … DROP COLUMN`, which
 * needs SQLite 3.35 — only guaranteed from API 34, below this module's `minSdk`.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recipe_drafts_new` (
                `recipeId` BLOB NOT NULL, `isNewRecipe` INTEGER NOT NULL, `title` TEXT NOT NULL,
                `description` TEXT NOT NULL, `imageUrl` TEXT NOT NULL, `localImagePath` TEXT,
                `prepTimeMinutes` TEXT NOT NULL, `cookTimeMinutes` TEXT NOT NULL,
                `servings` TEXT NOT NULL, `externalUrl` TEXT NOT NULL, `ingredientsJson` TEXT NOT NULL,
                `stepsJson` TEXT NOT NULL, `tagsJson` TEXT NOT NULL, `labelsJson` TEXT NOT NULL,
                `version` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`recipeId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `recipe_drafts_new` (
                `recipeId`, `isNewRecipe`, `title`, `description`, `imageUrl`, `localImagePath`,
                `prepTimeMinutes`, `cookTimeMinutes`, `servings`, `externalUrl`, `ingredientsJson`,
                `stepsJson`, `tagsJson`, `labelsJson`, `version`, `updatedAt`
            )
            SELECT
                `recipeId`, `isNewRecipe`, `title`, `description`, `imageUrl`, `localImagePath`,
                `prepTimeMinutes`, `cookTimeMinutes`, `servings`, `externalUrl`, `ingredientsJson`,
                `stepsJson`, `tagsJson`, `labelsJson`, `version`, `updatedAt`
            FROM `recipe_drafts`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `recipe_drafts`")
        db.execSQL("ALTER TABLE `recipe_drafts_new` RENAME TO `recipe_drafts`")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recipe_image_state` (
                `recipeId` BLOB NOT NULL, `attempts` INTEGER NOT NULL, `lastAttemptAt` INTEGER NOT NULL,
                PRIMARY KEY(`recipeId`),
                FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`uuid`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}

/**
 * Adds the columns Stage 2 of #132 needs to upload a recipe's image to the backend and fetch it back
 * on another device.
 *
 * `recipes.imageBlobId` is the content hash of the uploaded image, and is the one image-related
 * field that *does* travel through sync — it is the pointer a second device needs for bytes it
 * cannot re-derive. It is server-owned; see ADR-011 and the Stage 2 plan.
 *
 * The three `recipe_image_state` columns track the upload half of the ladder, deliberately separate
 * from the existing download counters so a recipe stuck on one cannot block the other.
 *
 * Plain `ADD COLUMN` throughout — unlike [MIGRATION_2_3] nothing is being removed, so none of the
 * table-rebuild dance is needed.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN imageBlobId TEXT")
        db.execSQL("ALTER TABLE recipe_image_state ADD COLUMN uploadAttempts INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE recipe_image_state ADD COLUMN lastUploadAttemptAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE recipe_image_state ADD COLUMN uploadedFileModifiedAt INTEGER")
    }
}

/**
 * Adds [com.tenmilelabs.chefai.core.data.local.room.RecipeDraftEntity.privacy].
 *
 * Existing rows are backfilled to 'PUBLIC' rather than to the new Kotlin default of PRIVATE:
 * before this change `RecipeDraft.toRecipe()` hardcoded PUBLIC, so a draft already in flight was
 * going to save as public, and a migration should preserve that rather than silently change it.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe_drafts ADD COLUMN privacy TEXT NOT NULL DEFAULT 'PUBLIC'")
    }
}

/**
 * Adds the per-slot cooked timestamps on
 * [com.tenmilelabs.chefai.core.data.local.room.MealPlanDayEntity] that back "mark as cooked" on the
 * meal-plan detail screen.
 *
 * Nullable with no default: `null` means "not cooked yet", which is the right state for every row
 * that already exists, so no backfill is needed. Plain `ADD COLUMN` — nothing is being removed, so
 * none of [MIGRATION_2_3]'s table-rebuild dance applies.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE meal_plan_days ADD COLUMN dinnerCookedAt INTEGER")
        db.execSQL("ALTER TABLE meal_plan_days ADD COLUMN lunchCookedAt INTEGER")
    }
}
