package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tenmilelabs.chefai.core.data.local.room.AllergenEntity
import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.LabelEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.room.SourceClassificationEntity
import com.tenmilelabs.chefai.core.data.local.room.TagEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.UuidConverters

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
    version = 2,
    exportSchema = true
)
@TypeConverters(
    UuidConverters::class,

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

// TODO - merge migrations into new DB asset before rolling out to production
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Make email and avatarUrl NOT NULL in users table
        // SQLite doesn't support ALTER COLUMN, so we recreate the table

        // Step 1: Create new table with NOT NULL constraints
        database.execSQL(
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
        database.execSQL(
            """
            INSERT INTO users_new (uuid, displayName, email, avatarUrl, updatedAt, deletedAt, syncState)
            SELECT uuid, displayName, email, avatarUrl, updatedAt, deletedAt, syncState
            FROM users
            WHERE email IS NOT NULL AND avatarUrl IS NOT NULL
        """.trimIndent()
        )

        // Step 3: Drop old table
        database.execSQL("DROP TABLE users")

        // Step 4: Rename new table
        database.execSQL("ALTER TABLE users_new RENAME TO users")

        // Step 5: Recreate index
        database.execSQL("CREATE INDEX index_users_syncState_updatedAt ON users(syncState, updatedAt)")
    }
}