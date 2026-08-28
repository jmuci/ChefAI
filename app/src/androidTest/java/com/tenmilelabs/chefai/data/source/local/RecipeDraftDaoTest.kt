package com.tenmilelabs.chefai.data.source.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.RecipeDraftEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Instrumented test for [com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDraftDao] against a
 * real (in-memory) Room database — the recipe URL import flow's final write goes through this DAO,
 * and its `@Upsert`-generated SQL is otherwise untested (the unit-test suite only exercises
 * `FakeRecipeDraftDao`, a hand-written in-memory map, not Room's generated implementation).
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class RecipeDraftDaoTest {

    private lateinit var database: ChefAIDataBase

    private fun draft(recipeId: UUID = UuidV7Generator.newId(), title: String = "Draft Title") =
        RecipeDraftEntity(
            recipeId = recipeId,
            isNewRecipe = true,
            title = title,
            description = "A description",
            imageUrl = "",
            localImagePath = null,
            prepTimeMinutes = "10",
            cookTimeMinutes = "20",
            servings = "4",
            caloriesPerServing = "",
            proteinGramsPerServing = "",
            externalUrl = "https://example.com/recipe",
            privacy = RecipePrivacy.PRIVATE,
            ingredientsJson = "[]",
            stepsJson = "[]",
            tagsJson = "[]",
            labelsJson = "[]",
            version = 1,
            updatedAt = System.currentTimeMillis(),
        )

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(getApplicationContext(), ChefAIDataBase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun saveDraft_thenGetDraft_returnsTheSameDraft() = runTest {
        val entity = draft()

        database.recipeDraftDao().saveDraft(entity)
        val loaded = database.recipeDraftDao().getDraft(entity.recipeId)

        assertEquals(entity, loaded)
    }

    @Test
    fun getDraft_forAnUnknownId_returnsNull() = runTest {
        assertNull(database.recipeDraftDao().getDraft(UuidV7Generator.newId()))
    }

    @Test
    fun saveDraft_upsertsRatherThanDuplicating() = runTest {
        val recipeId = UuidV7Generator.newId()
        database.recipeDraftDao().saveDraft(draft(recipeId, title = "First title"))
        database.recipeDraftDao().saveDraft(draft(recipeId, title = "Updated title"))

        val loaded = database.recipeDraftDao().getDraft(recipeId)

        assertEquals("Updated title", loaded?.title)
    }

    @Test
    fun deleteDraft_removesOnlyThatDraft() = runTest {
        val keep = draft(title = "Keep me")
        val remove = draft(title = "Remove me")
        database.recipeDraftDao().saveDraft(keep)
        database.recipeDraftDao().saveDraft(remove)

        database.recipeDraftDao().deleteDraft(remove.recipeId)

        assertNull(database.recipeDraftDao().getDraft(remove.recipeId))
        assertEquals(keep, database.recipeDraftDao().getDraft(keep.recipeId))
    }

    @Test
    fun localImagePath_roundTripsThroughSaveAndGet() = runTest {
        // Schema v2, MIGRATION_1_2 — the draft row must carry a cached image path too, since import
        // writes a draft before the user ever reaches Save.
        val recipeId = UuidV7Generator.newId()
        val path = "/data/data/com.tenmilelabs.chefai/files/recipe_images/$recipeId"
        database.recipeDraftDao().saveDraft(draft(recipeId).copy(localImagePath = path))

        val loaded = database.recipeDraftDao().getDraft(recipeId)

        assertEquals(path, loaded?.localImagePath)
    }

    @Test
    fun deleteAllDrafts_clearsEveryDraft() = runTest {
        val first = draft()
        val second = draft()
        database.recipeDraftDao().saveDraft(first)
        database.recipeDraftDao().saveDraft(second)

        database.recipeDraftDao().deleteAllDrafts()

        assertNull(database.recipeDraftDao().getDraft(first.recipeId))
        assertNull(database.recipeDraftDao().getDraft(second.recipeId))
    }
}
