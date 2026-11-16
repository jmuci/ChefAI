
package com.tenmilelabs.chefai.data.source.local

import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.data.local.room.HouseholdMemberEntity
import com.tenmilelabs.chefai.core.data.local.room.HouseholdEntity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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

    @Test
    fun softDelete_removesRecipeFromObserveRecipesWithDetails() = runTest {
        // GIVEN - two recipes exist
        database.recipeDao().upsertRecipe(recipe1)
        database.recipeDao().upsertRecipe(recipe2)

        // WHEN - recipe1 is soft-deleted
        database.recipeDao().softDelete(recipe1.uuid, System.currentTimeMillis())

        // THEN - only recipe2 remains in the details stream
        val recipes = database.recipeDao().observeVisibleRecipesWithDetails(testUser.uuid).first()
        assertEquals(1, recipes.size)
        assertEquals(recipe2.uuid, recipes.first().recipe.uuid)
    }

    @Test
    fun observeVisibleRecipes_showsOwnPublicAndHouseholdRecipes_hidesOtherAccountsPrivateOnes() = runTest {
        // GIVEN - the viewer's private recipe, another account's private and public recipes, and a
        // household member's private recipe
        val otherUser = testUser.copy(uuid = UuidV7Generator.newId(), email = "other@test.com")
        val householdMember = testUser.copy(uuid = UuidV7Generator.newId(), email = "member@test.com")
        database.userDao().upsertUser(otherUser)
        database.userDao().upsertUser(householdMember)
        val own = recipe1.copy(privacy = RecipePrivacy.PRIVATE)
        val othersPrivate = recipe2.copy(uuid = UuidV7Generator.newId(), creatorId = otherUser.uuid, privacy = RecipePrivacy.PRIVATE)
        val othersPublic = recipe2.copy(uuid = UuidV7Generator.newId(), creatorId = otherUser.uuid, privacy = RecipePrivacy.PUBLIC)
        val membersPrivate = recipe2.copy(uuid = UuidV7Generator.newId(), creatorId = householdMember.uuid, privacy = RecipePrivacy.PRIVATE)
        listOf(own, othersPrivate, othersPublic, membersPrivate).forEach { database.recipeDao().upsertRecipe(it) }
        val householdId = UuidV7Generator.newId()
        database.householdDao().upsertHousehold(
            HouseholdEntity(uuid = householdId, name = "Home", ownerId = testUser.uuid, createdAt = 0L, updatedAt = 0L)
        )
        database.householdDao().upsertMembers(
            listOf(
                HouseholdMemberEntity(householdId, testUser.uuid, "Test User", "", "OWNER", 0L),
                HouseholdMemberEntity(householdId, householdMember.uuid, "Member", "", "MEMBER", 0L),
            )
        )

        // WHEN - the viewer's catalog and local search are read
        val visible = database.recipeDao().observeVisibleRecipesWithDetails(testUser.uuid).first().map { it.recipe.uuid }
        val searched = database.recipeDao().searchRecipesWithDetails("", 50, testUser.uuid).map { it.recipe.uuid }

        // THEN - the other account's private recipe is in neither
        val expected = setOf(own.uuid, othersPublic.uuid, membersPrivate.uuid)
        assertEquals(expected, visible.toSet())
        assertEquals(expected, searched.toSet())
    }

    @Test
    fun softDelete_causesObserveRecipeWithDetailsToEmitNull() = runTest {
        // GIVEN - a recipe exists
        database.recipeDao().upsertRecipe(recipe1)

        // WHEN - it is soft-deleted
        database.recipeDao().softDelete(recipe1.uuid, System.currentTimeMillis())

        // THEN - the single-recipe details stream now emits null, as if it doesn't exist
        val details = database.recipeDao().observeRecipeWithDetails(recipe1.uuid).first()
        assertNull(details)
    }

    @Test
    fun softDelete_getRecipeByIdStillReturnsTheTombstone() = runTest {
        // GIVEN - a recipe exists
        database.recipeDao().upsertRecipe(recipe1)

        // WHEN - it is soft-deleted
        val deletedAt = System.currentTimeMillis()
        database.recipeDao().softDelete(recipe1.uuid, deletedAt)

        // THEN - getRecipeById (used by sync as an existence check) still returns the row,
        // marked DELETED — sync must never treat a tombstone as "not found".
        val entity = database.recipeDao().getRecipeById(recipe1.uuid)
        assertNotNull(entity)
        assertEquals(deletedAt, entity?.deletedAt)
        assertEquals(SyncState.DELETED, entity?.syncState)
    }

    @Test
    fun softDelete_recipeStillAppearsInGetAllDirty() = runTest {
        // GIVEN - a recipe exists
        database.recipeDao().upsertRecipe(recipe1)

        // WHEN - it is soft-deleted
        database.recipeDao().softDelete(recipe1.uuid, System.currentTimeMillis())

        // THEN - it still appears in getAllDirty, which is how the delete gets pushed to the backend
        val dirty = database.recipeDao().getAllDirty()
        assertTrue(dirty.any { it.uuid == recipe1.uuid && it.syncState == SyncState.DELETED })
    }

    @Test
    fun localImagePath_roundTripsThroughUpsertAndGet() = runTest {
        // GIVEN - a recipe with an on-device cached image (schema v2, MIGRATION_1_2)
        val path = "/data/data/com.tenmilelabs.chefai/files/recipe_images/${recipe1.uuid}"
        database.recipeDao().upsertRecipe(recipe1.copy(localImagePath = path))

        // WHEN - loading it back
        val loaded = database.recipeDao().getRecipeById(recipe1.uuid)

        // THEN - the path round-trips unchanged
        assertEquals(path, loaded?.localImagePath)
    }

    @Test
    fun localImagePath_defaultsToNullWhenNotCached() = runTest {
        database.recipeDao().upsertRecipe(recipe1)

        val loaded = database.recipeDao().getRecipeById(recipe1.uuid)

        assertNull(loaded?.localImagePath)
    }

    @Test
    fun softDelete_decrementsCountRecipesForUser() = runTest {
        // GIVEN - two recipes for the same user
        database.recipeDao().upsertRecipe(recipe1)
        database.recipeDao().upsertRecipe(recipe2)
        assertEquals(2, database.recipeDao().countRecipesForUser(testUser.uuid))

        // WHEN - one is soft-deleted
        database.recipeDao().softDelete(recipe1.uuid, System.currentTimeMillis())

        // THEN - the count drops by one
        assertEquals(1, database.recipeDao().countRecipesForUser(testUser.uuid))
    }

    @Test
    fun getSoftDeletedIds_returnsOnlyLocallySoftDeletedRows() = runTest {
        // GIVEN - one live recipe, one soft-deleted, and an id with no row at all
        database.recipeDao().upsertRecipe(recipe1)
        database.recipeDao().upsertRecipe(recipe2)
        database.recipeDao().softDelete(recipe1.uuid, System.currentTimeMillis())
        val unknown = java.util.UUID.randomUUID()

        // WHEN
        val result = database.recipeDao().getSoftDeletedIds(listOf(recipe1.uuid, recipe2.uuid, unknown))

        // THEN - unknown and live ids are not reported
        assertEquals(listOf(recipe1.uuid), result)
    }
}
