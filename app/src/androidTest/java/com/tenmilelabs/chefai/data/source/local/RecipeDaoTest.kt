package com.tenmilelabs.chefai.data.source.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class RecipeDaoTest {
    private lateinit var database: ChefAIDataBase

    @Before
    fun initDB() {
        database = Room.inMemoryDatabaseBuilder(
            getApplicationContext(),
            ChefAIDataBase::class.java
        ).allowMainThreadQueries().build()
    }

    @Test
    fun insertRecipeAndGetById() = runTest {
        // GIVEN - Insert a recipe
        val recipe = RecipeEntity(
            "1",
            "Title1",
            "Label1",
            "Description1",
            10,
            "RecipeUrl1",
            "ImageUrl1",
            "ImageUrlThumbnail1"
        )

        database.recipeDao().upsertRecipe(recipe)

        // WHEN - Get the recipe by id from the database
        val loaded = database.recipeDao().getRecipeById("1")

        // THEN - The loaded data contains the expected values
        assertNotNull(loaded as RecipeEntity)
        assertEquals(loaded.uuid, recipe.uuid)
        assertEquals(loaded.title, recipe.title)
    }

    @Test
    fun insertRecipeReplacesOnConflict() = runTest {
        // Given that a recipe is inserted
        val recipe = RecipeEntity(
            "1",
            "Title1",
            "Label1",
            "Description1",
            10,
            "RecipeUrl1",
            "ImageUrl1",
            "ImageUrlThumbnail1"
        )
        database.recipeDao().upsertRecipe(recipe)
        // When a recipe with the same id is inserted
        val newRecipe = RecipeEntity(
            "1",
            "Title2",
            "Label2",
            "Description2",
            10,
            "RecipeUrl2",
            "ImageUrl2",
            "ImageUrlThumbnail2"
        )
        database.recipeDao().upsertRecipe(newRecipe)
        // THEN - The loaded data contains the expected values
        val loaded = database.recipeDao().getRecipeById("1")
        assertNotNull(loaded as RecipeEntity)
        assertEquals(loaded.uuid, newRecipe.uuid)
        assertEquals(loaded.title, newRecipe.title)
    }
}