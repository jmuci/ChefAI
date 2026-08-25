package com.tenmilelabs.chefai.data.source.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Instrumented test for [com.tenmilelabs.chefai.core.data.local.room.dao.RecipeIngredientDao]
 * against a real (in-memory) Room database.
 *
 * [RecipeIngredientDao.updateSyncStateForRecipeIngredients] carries a comment warning that marking
 * the wrong ingredient refs SYNCED previously caused a subsequent pull to delete them as stale,
 * permanently losing data (#101) — that scoped-update behavior had no DAO-level regression test.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class RecipeIngredientDaoTest {

    private lateinit var database: ChefAIDataBase

    private val user = UserEntity(
        uuid = UuidV7Generator.newId(),
        displayName = "Chef",
        email = "chef@test.com",
        avatarUrl = "",
        updatedAt = 0L,
        deletedAt = null,
    )

    private lateinit var recipe: RecipeEntity

    private fun ingredient(name: String) = IngredientEntity(
        uuid = UuidV7Generator.newId(),
        displayName = name,
        allergenId = null,
        sourcePrimaryId = null,
        updatedAt = 0L,
        deletedAt = null,
    )

    private fun recipeIngredient(
        recipeId: UUID,
        ingredientId: UUID,
        syncState: SyncState = SyncState.PENDING,
    ) = RecipeIngredientEntity(
        recipeId = recipeId,
        ingredientId = ingredientId,
        quantity = 1.0,
        unit = "cup",
        updatedAt = 0L,
        syncState = syncState,
    )

    @Before
    fun createDb() = runTest {
        database = Room.inMemoryDatabaseBuilder(getApplicationContext(), ChefAIDataBase::class.java)
            .allowMainThreadQueries()
            .build()
        database.userDao().upsertUser(user)
        recipe = RecipeEntity(
            uuid = UuidV7Generator.newId(),
            title = "Chili",
            description = "",
            imageUrl = "",
            imageUrlThumbnail = "",
            prepTimeMinutes = 10,
            cookTimeMinutes = 20,
            servings = 4,
            creatorId = user.uuid,
            recipeExternalUrl = null,
            updatedAt = 0L,
            deletedAt = null,
        )
        database.recipeDao().upsertRecipe(recipe)
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun upsertAllForRecipe_replacesEveryPriorIngredientRefForThatRecipe() = runTest {
        val salt = ingredient("Salt")
        val pepper = ingredient("Pepper")
        val cumin = ingredient("Cumin")
        database.ingredientDao().upsertAll(listOf(salt, pepper, cumin))
        database.recipeIngredientDao().upsertAllForRecipe(
            recipe.uuid,
            listOf(recipeIngredient(recipe.uuid, salt.uuid), recipeIngredient(recipe.uuid, pepper.uuid)),
        )

        database.recipeIngredientDao().upsertAllForRecipe(
            recipe.uuid,
            listOf(recipeIngredient(recipe.uuid, cumin.uuid)),
        )

        val remaining = database.recipeIngredientDao().getIngredientsForRecipe(recipe.uuid)
        assertEquals(listOf(cumin.uuid), remaining.map { it.ingredientId })
    }

    @Test
    fun upsertAllForRecipe_doesNotTouchAnotherRecipesIngredients() = runTest {
        val otherRecipe = recipe.copy(uuid = UuidV7Generator.newId())
        database.recipeDao().upsertRecipe(otherRecipe)
        val flour = ingredient("Flour")
        database.ingredientDao().upsertAll(listOf(flour))
        database.recipeIngredientDao().upsertAllForRecipe(
            otherRecipe.uuid,
            listOf(recipeIngredient(otherRecipe.uuid, flour.uuid)),
        )

        database.recipeIngredientDao().upsertAllForRecipe(recipe.uuid, emptyList())

        assertEquals(1, database.recipeIngredientDao().getIngredientsForRecipe(otherRecipe.uuid).size)
    }

    @Test
    fun updateSyncStateForRecipeIngredients_marksOnlyTheGivenIngredientIds() = runTest {
        val sent = ingredient("Sent")
        val filteredOut = ingredient("FilteredOut")
        database.ingredientDao().upsertAll(listOf(sent, filteredOut))
        database.recipeIngredientDao().upsertAll(
            listOf(
                recipeIngredient(recipe.uuid, sent.uuid, SyncState.PENDING),
                recipeIngredient(recipe.uuid, filteredOut.uuid, SyncState.PENDING),
            )
        )

        // Mirrors buildSyncRecipeDto filtering out ingredients the backend catalog doesn't know yet
        // (#101) — only `sent`'s ref made it into the push payload, so only it may flip to SYNCED.
        database.recipeIngredientDao().updateSyncStateForRecipeIngredients(
            recipeId = recipe.uuid,
            ingredientIds = listOf(sent.uuid),
            syncState = SyncState.SYNCED,
            updatedAt = 100L,
        )

        val refs = database.recipeIngredientDao().getIngredientsForRecipe(recipe.uuid)
            .associateBy { it.ingredientId }
        assertEquals(SyncState.SYNCED, refs.getValue(sent.uuid).syncState)
        assertEquals(SyncState.PENDING, refs.getValue(filteredOut.uuid).syncState)
    }

    @Test
    fun updateSyncStateForRecipe_marksEveryRefForThatRecipeRegardlessOfIngredient() = runTest {
        val a = ingredient("A")
        val b = ingredient("B")
        database.ingredientDao().upsertAll(listOf(a, b))
        database.recipeIngredientDao().upsertAll(
            listOf(
                recipeIngredient(recipe.uuid, a.uuid, SyncState.PENDING),
                recipeIngredient(recipe.uuid, b.uuid, SyncState.PENDING),
            )
        )

        database.recipeIngredientDao().updateSyncStateForRecipe(recipe.uuid, SyncState.SYNCED, 200L)

        val refs = database.recipeIngredientDao().getIngredientsForRecipe(recipe.uuid)
        assertTrue(refs.all { it.syncState == SyncState.SYNCED })
    }

    @Test
    fun getDirty_returnsOnlyPendingCrossRefs() = runTest {
        val a = ingredient("A")
        val b = ingredient("B")
        database.ingredientDao().upsertAll(listOf(a, b))
        database.recipeIngredientDao().upsertAll(
            listOf(
                recipeIngredient(recipe.uuid, a.uuid, SyncState.PENDING),
                recipeIngredient(recipe.uuid, b.uuid, SyncState.SYNCED),
            )
        )

        val dirty = database.recipeIngredientDao().getDirty()

        assertEquals(listOf(a.uuid), dirty.map { it.ingredientId })
    }

    @Test
    fun markPendingForRecipes_onlyAffectsListedRecipes() = runTest {
        val otherRecipe = recipe.copy(uuid = UuidV7Generator.newId())
        database.recipeDao().upsertRecipe(otherRecipe)
        val ing = ingredient("Shared")
        database.ingredientDao().upsertAll(listOf(ing))
        database.recipeIngredientDao().upsertAll(
            listOf(
                recipeIngredient(recipe.uuid, ing.uuid, SyncState.SYNCED),
                recipeIngredient(otherRecipe.uuid, ing.uuid, SyncState.SYNCED),
            )
        )

        database.recipeIngredientDao().markPendingForRecipes(listOf(recipe.uuid), updatedAt = 50L)

        val markedRefs = database.recipeIngredientDao().getIngredientsForRecipe(recipe.uuid)
        val untouchedRefs = database.recipeIngredientDao().getIngredientsForRecipe(otherRecipe.uuid)
        assertTrue(markedRefs.all { it.syncState == SyncState.PENDING })
        assertTrue(untouchedRefs.all { it.syncState == SyncState.SYNCED })
    }

    @Test
    fun deleteAllForRecipe_removesOnlyThatRecipesRefs() = runTest {
        val otherRecipe = recipe.copy(uuid = UuidV7Generator.newId())
        database.recipeDao().upsertRecipe(otherRecipe)
        val ing = ingredient("Shared")
        database.ingredientDao().upsertAll(listOf(ing))
        database.recipeIngredientDao().upsertAll(
            listOf(
                recipeIngredient(recipe.uuid, ing.uuid),
                recipeIngredient(otherRecipe.uuid, ing.uuid),
            )
        )

        database.recipeIngredientDao().deleteAllForRecipe(recipe.uuid)

        assertTrue(database.recipeIngredientDao().getIngredientsForRecipe(recipe.uuid).isEmpty())
        assertEquals(1, database.recipeIngredientDao().getIngredientsForRecipe(otherRecipe.uuid).size)
    }
}
