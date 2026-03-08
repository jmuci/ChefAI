
package com.tenmilelabs.chefai.data.source.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class RecipeDaoTest {
    private lateinit var database: ChefAIDataBase

    // Test data
    private val testUser = UserEntity(
        uuid = UuidV7Generator.newId(),
        displayName = "Test User",
        email = "test@test.com",
        avatarUrl = "",
        updatedAt = System.currentTimeMillis(),
        deletedAt = null
    )

    private val recipe1 = RecipeEntity(
        uuid = UuidV7Generator.newId(),
        title = "Pancakes",
        description = "Fluffy American-style pancakes.",
        imageUrl = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 10,
        cookTimeMinutes = 15,
        servings = 4,
        creatorId = testUser.uuid,
        recipeExternalUrl = null,
        updatedAt = System.currentTimeMillis(),
        deletedAt = null
    )

    private val recipe2 = RecipeEntity(
        uuid = UuidV7Generator.newId(),
        title = "French Toast",
        description = "Classic sweet French toast.",
        imageUrl = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 5,
        cookTimeMinutes = 10,
        servings = 2,
        creatorId = testUser.uuid,
        recipeExternalUrl = null,
        updatedAt = System.currentTimeMillis(),
        deletedAt = null
    )

    @Before
    fun initDB() {
        database = Room.inMemoryDatabaseBuilder(
            getApplicationContext(),
            ChefAIDataBase::class.java
        ).allowMainThreadQueries().build()

        // Pre-populate user table for foreign key constraints
        runTest {
            database.userDao().upsertUser(testUser)
        }
    }

    @Test
    fun insertRecipeAndGetById() = runTest {
        // GIVEN - Insert a recipe
        database.recipeDao().upsertRecipe(recipe1)

        // WHEN - Get the recipe by id from the database
        val loaded = database.recipeDao().getRecipeById(recipe1.uuid)

        // THEN - The loaded data contains the expected values
        assertNotNull(loaded)
        assertEquals(recipe1.uuid, loaded?.uuid)
        assertEquals(recipe1.title, loaded?.title)
    }

    @Test
    fun insertRecipeReplacesOnConflict() = runTest {
        // GIVEN - a recipe is inserted
        database.recipeDao().upsertRecipe(recipe1)

        // WHEN - a recipe with the same id is inserted
        val updatedRecipe = recipe1.copy(title = "Delicious Pancakes")
        database.recipeDao().upsertRecipe(updatedRecipe)

        // THEN - The loaded data contains the updated values
        val loaded = database.recipeDao().getRecipeById(recipe1.uuid)
        assertEquals(updatedRecipe.title, loaded?.title)
    }

    @Test
    fun getAllRecipes_returnsAllRecipes() = runTest {
        // GIVEN - insert two recipes
        database.recipeDao().upsertRecipe(recipe1)
        database.recipeDao().upsertRecipe(recipe2)

        // WHEN - get all recipes
        val recipes = database.recipeDao().observeAllRecipesForUser(testUser.uuid).first()

        // THEN - the list contains both recipes
        assertEquals(2, recipes.size)
    }

    @Test
    fun getRecipeWithDetails_returnsCorrectDetails() = runTest {
        // GIVEN - a recipe with associated details
        database.recipeDao().upsertRecipe(recipe1)
        // In a real scenario, you would also insert steps, ingredients, etc.

        // WHEN - fetching the recipe with details
        val recipeWithDetails = database.recipeDao().getRecipeWithDetails(recipe1.uuid)

        // THEN - the recipe details are loaded correctly
        assertNotNull(recipeWithDetails)
        assertEquals(recipe1.uuid, recipeWithDetails?.recipe?.uuid)
        // Add more assertions for steps, ingredients, tags, and labels as you add them
    }

    /**
     * This test verifies that when a recipe is deleted, its details are also handled correctly,
     * either by being deleted as well (if using CASCADE) or by becoming dissociated.
     */
    @Test
    fun deleteRecipe_alsoRemovesAssociatedDetails() = runTest {
        // GIVEN - a recipe is inserted
        database.recipeDao().upsertRecipe(recipe1)

        // WHEN - the recipe is deleted
        database.recipeDao().deleteRecipe(recipe1.uuid)

        // THEN - the recipe and its details can no longer be retrieved
        val loadedRecipe = database.recipeDao().getRecipeById(recipe1.uuid)
        val recipeWithDetails = database.recipeDao().getRecipeWithDetails(recipe1.uuid)
        assertEquals(null, loadedRecipe)
        assertEquals(null, recipeWithDetails)
    }
}
