package com.tenmilelabs.chefai.data.source.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_1_2
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_2_3
import com.tenmilelabs.chefai.core.data.local.room.dao.MIGRATION_3_4
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
    fun migrateAll1To4_succeeds() {
        helper.createDatabase(TEST_DB, 1).close()

        helper.runMigrationsAndValidate(
            TEST_DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4
        )
    }
}
