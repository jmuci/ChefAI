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
    version = 6,
    exportSchema = true
)
@TypeConverters(
    UuidConverters::class,

)
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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_metadata (
                entityType TEXT NOT NULL PRIMARY KEY,
                lastSyncedAt INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN version INTEGER NOT NULL DEFAULT 1")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recipe_drafts (
                recipeId BLOB NOT NULL PRIMARY KEY,
                isNewRecipe INTEGER NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                imageUrl TEXT NOT NULL,
                selectedImageUri TEXT,
                prepTimeMinutes TEXT NOT NULL,
                cookTimeMinutes TEXT NOT NULL,
                servings TEXT NOT NULL,
                externalUrl TEXT NOT NULL,
                ingredientsJson TEXT NOT NULL,
                stepsJson TEXT NOT NULL,
                tagsJson TEXT NOT NULL,
                labelsJson TEXT NOT NULL,
                version INTEGER NOT NULL DEFAULT 1,
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent()
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bookmarked_recipes (
                userId BLOB NOT NULL,
                recipeId BLOB NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                syncState TEXT NOT NULL,
                PRIMARY KEY(userId, recipeId),
                FOREIGN KEY(userId) REFERENCES users(uuid) ON DELETE CASCADE,
                FOREIGN KEY(recipeId) REFERENCES recipes(uuid) ON DELETE CASCADE
            )
        """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_bookmarked_recipes_recipeId ON bookmarked_recipes(recipeId)")
        db.execSQL("CREATE INDEX index_bookmarked_recipes_syncState_updatedAt ON bookmarked_recipes(syncState, updatedAt)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS meal_plans (
                uuid BLOB NOT NULL PRIMARY KEY,
                userId BLOB NOT NULL,
                name TEXT NOT NULL,
                status TEXT NOT NULL,
                preferencesJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                syncState TEXT NOT NULL,
                FOREIGN KEY(userId) REFERENCES users(uuid) ON DELETE CASCADE
            )
        """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_meal_plans_userId ON meal_plans(userId)")
        db.execSQL("CREATE INDEX index_meal_plans_syncState_updatedAt ON meal_plans(syncState, updatedAt)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS meal_plan_days (
                uuid BLOB NOT NULL PRIMARY KEY,
                mealPlanId BLOB NOT NULL,
                dayIndex INTEGER NOT NULL,
                dinnerRecipeId BLOB,
                lunchRecipeId BLOB,
                FOREIGN KEY(mealPlanId) REFERENCES meal_plans(uuid) ON DELETE CASCADE
            )
        """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_meal_plan_days_mealPlanId ON meal_plan_days(mealPlanId)")
    }
}

// TODO - merge migrations into new DB asset before rolling out to production
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Make email and avatarUrl NOT NULL in users table
        // SQLite doesn't support ALTER COLUMN, so we recreate the table

        // Step 1: Create new table with NOT NULL constraints
        db.execSQL(
            """
            CREATE TABLE users_new (
                uuid BLOB NOT NULL PRIMARY KEY,
                displayName TEXT NOT NULL,
                email TEXT NOT NULL,
                avatarUrl TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                syncState TEXT NOT NULL
            )
        """.trimIndent()
        )

        // Step 2: Copy data (only rows with non-null email and avatarUrl)
        db.execSQL(
            """
            INSERT INTO users_new (uuid, displayName, email, avatarUrl, updatedAt, deletedAt, syncState)
            SELECT uuid, displayName, email, avatarUrl, updatedAt, deletedAt, syncState
            FROM users
            WHERE email IS NOT NULL AND avatarUrl IS NOT NULL
        """.trimIndent()
        )

        // Step 3: Drop old table
        db.execSQL("DROP TABLE users")

        // Step 4: Rename new table
        db.execSQL("ALTER TABLE users_new RENAME TO users")

        // Step 5: Recreate index
        db.execSQL("CREATE INDEX index_users_syncState_updatedAt ON users(syncState, updatedAt)")
    }
}
