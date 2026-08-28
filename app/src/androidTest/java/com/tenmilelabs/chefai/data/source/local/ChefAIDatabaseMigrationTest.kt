package com.tenmilelabs.chefai.data.source.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_1_2
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_2_3
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_3_4
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_4_5
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_5_6
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_6_7
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_7_8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.util.UUID

private const val TEST_DB = "migration-test"

/**
 * Verifies the hand-written migrations against the exported schemas.
 *
 * `MigrationTestHelper.runMigrationsAndValidate` fails if the post-migration tables differ from what
 * Room generated for that version in any respect — column order, types, nullability, indices, foreign
 * keys — so the assertions below only need to cover the *data* surviving the move.
 */
@RunWith(AndroidJUnit4::class)
class ChefAIDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ChefAIDataBase::class.java
    )

    /** Room stores UUIDs as 16-byte blobs — see `UuidConverters`. */
    private fun UUID.toBlob(): ByteArray = ByteBuffer.allocate(16)
        .putLong(mostSignificantBits)
        .putLong(leastSignificantBits)
        .array()

    @Test
    fun migrate2To3_keepsDraftsAndDropsSelectedImageUri() {
        val draftId = UUID.randomUUID()

        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO recipe_drafts (
                    recipeId, isNewRecipe, title, description, imageUrl, selectedImageUri,
                    localImagePath, prepTimeMinutes, cookTimeMinutes, servings, externalUrl,
                    ingredientsJson, stepsJson, tagsJson, labelsJson, version, updatedAt
                ) VALUES (?, 1, 'Carbonara', 'Classic', 'https://example.com/x.jpg',
                    'content://media/42', '/files/recipe_images/x', '10', '20', '2', '',
                    '[]', '[]', '[]', '[]', 1, 555)
                """.trimIndent(),
                arrayOf(draftId.toBlob())
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        db.query("SELECT title, imageUrl, localImagePath, updatedAt FROM recipe_drafts").use { cursor ->
            assertTrue("the draft row should survive the table rebuild", cursor.moveToFirst())
            assertEquals("Carbonara", cursor.getString(0))
            assertEquals("https://example.com/x.jpg", cursor.getString(1))
            assertEquals("/files/recipe_images/x", cursor.getString(2))
            assertEquals(555L, cursor.getLong(3))
            assertEquals(1, cursor.count)
        }

        db.query("SELECT * FROM recipe_drafts LIMIT 0").use { cursor ->
            assertTrue(
                "selectedImageUri should be gone",
                cursor.columnNames.none { it == "selectedImageUri" }
            )
        }

        db.query("SELECT COUNT(*) FROM recipe_image_state").use { cursor ->
            assertTrue("recipe_image_state should exist and be empty", cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun migrate3To4_addsBlobColumnsAndKeepsExistingRows() {
        val recipeId = UUID.randomUUID()

        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO recipes (
                    uuid, title, description, imageUrl, imageUrlThumbnail, localImagePath,
                    prepTimeMinutes, cookTimeMinutes, servings, creatorId, recipeExternalUrl,
                    privacy, version, updatedAt, deletedAt, syncState
                ) VALUES (?, 'Carbonara', 'Classic', 'https://example.com/x.jpg',
                    'https://example.com/x.jpg', '/files/recipe_images/x', 10, 20, 2, ?, NULL,
                    'PUBLIC', 1, 555, NULL, 'SYNCED')
                """.trimIndent(),
                arrayOf(recipeId.toBlob(), UUID.randomUUID().toBlob())
            )
            db.execSQL(
                "INSERT INTO recipe_image_state (recipeId, attempts, lastAttemptAt) VALUES (?, 2, 999)",
                arrayOf(recipeId.toBlob())
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        db.query("SELECT title, localImagePath, imageBlobId FROM recipes").use { cursor ->
            assertTrue("the recipe row should survive", cursor.moveToFirst())
            assertEquals("Carbonara", cursor.getString(0))
            assertEquals("/files/recipe_images/x", cursor.getString(1))
            assertTrue("a pre-existing recipe has never been uploaded", cursor.isNull(2))
        }

        // The download counters must come through untouched: an in-flight backfill that has already
        // burned two of its three attempts must not silently get them back.
        db.query(
            "SELECT attempts, lastAttemptAt, uploadAttempts, lastUploadAttemptAt, " +
                "uploadedFileModifiedAt FROM recipe_image_state"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
            assertEquals(999L, cursor.getLong(1))
            assertEquals("upload attempts start at the NOT NULL default", 0, cursor.getInt(2))
            assertEquals(0L, cursor.getLong(3))
            assertTrue("nothing has been uploaded yet", cursor.isNull(4))
        }
    }

    @Test
    fun migrate4To5_addsPrivacyColumnBackfilledToPublic() {
        val draftId = UUID.randomUUID()

        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO recipe_drafts (
                    recipeId, isNewRecipe, title, description, imageUrl, localImagePath,
                    prepTimeMinutes, cookTimeMinutes, servings, externalUrl,
                    ingredientsJson, stepsJson, tagsJson, labelsJson, version, updatedAt
                ) VALUES (?, 1, 'Carbonara', 'Classic', 'https://example.com/x.jpg', NULL,
                    '10', '20', '2', '', '[]', '[]', '[]', '[]', 1, 555)
                """.trimIndent(),
                arrayOf(draftId.toBlob())
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        db.query("SELECT title, privacy FROM recipe_drafts").use { cursor ->
            assertTrue("the pre-existing draft row should survive the migration", cursor.moveToFirst())
            assertEquals("Carbonara", cursor.getString(0))
            assertEquals(
                "a draft in flight before this migration was going to save as PUBLIC — " +
                    "RecipeDraft.toRecipe() hardcoded it — so the backfill preserves that",
                "PUBLIC",
                cursor.getString(1)
            )
            assertEquals(1, cursor.count)
        }
    }

    @Test
    fun migrate5To6_keepsPlannedDaysAndStartsThemUncooked() {
        val planId = UUID.randomUUID()
        val dayId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val dinnerId = UUID.randomUUID()

        helper.createDatabase(TEST_DB, 5).use { db ->
            // meal_plans.userId is a CASCADE foreign key onto users, so the owner has to exist.
            db.execSQL(
                """
                INSERT INTO users (uuid, displayName, email, avatarUrl, updatedAt, deletedAt, syncState)
                VALUES (?, 'Tester', 'tester@example.com', '', 1, NULL, 'SYNCED')
                """.trimIndent(),
                arrayOf(userId.toBlob())
            )
            db.execSQL(
                """
                INSERT INTO meal_plans (
                    uuid, userId, name, status, preferencesJson, createdAt, updatedAt,
                    deletedAt, syncState
                ) VALUES (?, ?, '3-day meal plan', 'READY', '{}', 1, 1, NULL, 'SYNCED')
                """.trimIndent(),
                arrayOf(planId.toBlob(), userId.toBlob())
            )
            db.execSQL(
                """
                INSERT INTO meal_plan_days (uuid, mealPlanId, dayIndex, dinnerRecipeId, lunchRecipeId)
                VALUES (?, ?, 0, ?, NULL)
                """.trimIndent(),
                arrayOf(dayId.toBlob(), planId.toBlob(), dinnerId.toBlob())
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        db.query("SELECT dayIndex, dinnerCookedAt, lunchCookedAt FROM meal_plan_days").use { cursor ->
            assertTrue("the planned day should survive the migration", cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertTrue(
                "a day that existed before cooked tracking has not been cooked",
                cursor.isNull(1) && cursor.isNull(2)
            )
            assertEquals(1, cursor.count)
        }
    }

    @Test
    fun migrate6To7_addsShoppingListChecksTable() {
        val planId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                """
                INSERT INTO users (uuid, displayName, email, avatarUrl, updatedAt, deletedAt, syncState)
                VALUES (?, 'Tester', 'tester@example.com', '', 1, NULL, 'SYNCED')
                """.trimIndent(),
                arrayOf(userId.toBlob())
            )
            db.execSQL(
                """
                INSERT INTO meal_plans (
                    uuid, userId, name, status, preferencesJson, createdAt, updatedAt,
                    deletedAt, syncState
                ) VALUES (?, ?, '3-day meal plan', 'READY', '{}', 1, 1, NULL, 'SYNCED')
                """.trimIndent(),
                arrayOf(planId.toBlob(), userId.toBlob())
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)

        db.query("SELECT name FROM meal_plans WHERE uuid = ?", arrayOf(planId.toBlob())).use { cursor ->
            assertTrue("the meal plan should survive the migration", cursor.moveToFirst())
            assertEquals("3-day meal plan", cursor.getString(0))
        }

        db.execSQL(
            "INSERT INTO shopping_list_checks (mealPlanId, itemKey, checkedAt) VALUES (?, 'onion', 123)",
            arrayOf(planId.toBlob())
        )
        db.query("SELECT itemKey, checkedAt FROM shopping_list_checks").use { cursor ->
            assertTrue("the newly inserted check row should be readable", cursor.moveToFirst())
            assertEquals("onion", cursor.getString(0))
            assertEquals(123L, cursor.getLong(1))
            assertEquals(1, cursor.count)
        }
    }

    @Test
    fun migrate7To8_addsNutritionColumnsNullableOnRecipesBlankOnDrafts() {
        val recipeId = UUID.randomUUID()
        val draftId = UUID.randomUUID()

        helper.createDatabase(TEST_DB, 7).use { db ->
            db.execSQL(
                """
                INSERT INTO recipes (
                    uuid, title, description, imageUrl, imageUrlThumbnail, localImagePath,
                    prepTimeMinutes, cookTimeMinutes, servings, creatorId, recipeExternalUrl,
                    privacy, version, updatedAt, deletedAt, syncState
                ) VALUES (?, 'Carbonara', 'Classic', 'https://example.com/x.jpg',
                    'https://example.com/x.jpg', NULL, 10, 20, 2, ?, NULL,
                    'PUBLIC', 1, 555, NULL, 'SYNCED')
                """.trimIndent(),
                arrayOf(recipeId.toBlob(), UUID.randomUUID().toBlob())
            )
            db.execSQL(
                """
                INSERT INTO recipe_drafts (
                    recipeId, isNewRecipe, title, description, imageUrl, localImagePath,
                    prepTimeMinutes, cookTimeMinutes, servings, externalUrl, privacy,
                    ingredientsJson, stepsJson, tagsJson, labelsJson, version, updatedAt
                ) VALUES (?, 1, 'Carbonara', 'Classic', 'https://example.com/x.jpg', NULL,
                    '10', '20', '2', '', 'PUBLIC', '[]', '[]', '[]', '[]', 1, 555)
                """.trimIndent(),
                arrayOf(draftId.toBlob())
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        db.query("SELECT title, caloriesPerServing, proteinGramsPerServing FROM recipes").use { cursor ->
            assertTrue("the pre-existing recipe should survive the migration", cursor.moveToFirst())
            assertEquals("Carbonara", cursor.getString(0))
            assertTrue(
                "a recipe that existed before nutrition tracking has unknown, not zero, values",
                cursor.isNull(1) && cursor.isNull(2)
            )
            assertEquals(1, cursor.count)
        }

        db.query(
            "SELECT title, caloriesPerServing, proteinGramsPerServing FROM recipe_drafts"
        ).use { cursor ->
            assertTrue("the pre-existing draft row should survive the migration", cursor.moveToFirst())
            assertEquals("Carbonara", cursor.getString(0))
            assertEquals(
                "drafts follow the table's form-input convention: blank, not null, means not entered",
                "",
                cursor.getString(1)
            )
            assertEquals("", cursor.getString(2))
            assertEquals(1, cursor.count)
        }

        db.execSQL("UPDATE recipes SET caloriesPerServing = 350, proteinGramsPerServing = 12 WHERE uuid = ?", arrayOf(recipeId.toBlob()))
        db.query(
            "SELECT caloriesPerServing, proteinGramsPerServing FROM recipes WHERE uuid = ?",
            arrayOf(recipeId.toBlob())
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(350, cursor.getInt(0))
            assertEquals(12, cursor.getInt(1))
        }
    }

    @Test
    fun migrateAll1To8_succeeds() {
        helper.createDatabase(TEST_DB, 1).close()

        helper.runMigrationsAndValidate(
            TEST_DB, 8, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8,
        )
    }
}
