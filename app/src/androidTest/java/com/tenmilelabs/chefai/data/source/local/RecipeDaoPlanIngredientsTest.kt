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
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Instrumented test for [com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao.observeIngredientsForRecipes]
 * against a real (in-memory) Room database.
 *
 * The query joins `recipe_ingredients`, `recipes`, and `ingredients` and filters `deletedAt IS
 * NULL` on all three — [FakeRecipeDao][com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao]
 * reimplements that filtering in Kotlin for unit tests, so only a test against the generated SQL
 * itself actually proves the join and the three-way soft-delete filter behave as intended.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class RecipeDaoPlanIngredientsTest {

    private lateinit var database: ChefAIDataBase

    private val user = UserEntity(
        uuid = UuidV7Generator.newId(),
        displayName = "Chef",
        email = "chef@test.com",
        avatarUrl = "",
        updatedAt = 0L,
        deletedAt = null,
    )

    private fun recipe(
        title: String,
        servings: Int = 4,
        deletedAt: Long? = null,
    ) = RecipeEntity(
        uuid = UuidV7Generator.newId(),
        title = title,
        description = "",
        imageUrl = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 10,
        cookTimeMinutes = 20,
        servings = servings,
        creatorId = user.uuid,
        recipeExternalUrl = null,
        updatedAt = 0L,
        deletedAt = deletedAt,
    )

    private fun ingredient(name: String, deletedAt: Long? = null) = IngredientEntity(
        uuid = UuidV7Generator.newId(),
        displayName = name,
        allergenId = null,
        sourcePrimaryId = null,
        updatedAt = 0L,
        deletedAt = deletedAt,
    )

    private fun recipeIngredient(
        recipeId: UUID,
        ingredientId: UUID,
        quantity: Double = 1.0,
        unit: String = "cup",
        deletedAt: Long? = null,
    ) = RecipeIngredientEntity(
        recipeId = recipeId,
        ingredientId = ingredientId,
        quantity = quantity,
        unit = unit,
        updatedAt = 0L,
        deletedAt = deletedAt,
    )

    @Before
    fun createDb() = runTest {
        database = Room.inMemoryDatabaseBuilder(getApplicationContext(), ChefAIDataBase::class.java)
            .allowMainThreadQueries()
            .build()
        database.userDao().upsertUser(user)
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun returnsIngredientsAcrossMultipleRecipesInOneQuery() = runTest {
        val chili = recipe("Chili")
        val salad = recipe("Salad")
        val salt = ingredient("Salt")
        val lettuce = ingredient("Lettuce")
        database.recipeDao().upsertRecipe(chili)
        database.recipeDao().upsertRecipe(salad)
        database.ingredientDao().upsertAll(listOf(salt, lettuce))
        database.recipeIngredientDao().upsertAll(
            listOf(
                recipeIngredient(chili.uuid, salt.uuid),
                recipeIngredient(salad.uuid, lettuce.uuid),
            )
        )

        val rows = database.recipeDao()
            .observeIngredientsForRecipes(listOf(chili.uuid, salad.uuid))
            .first()

        assertEquals(setOf(salt.uuid, lettuce.uuid), rows.map { it.ingredientId }.toSet())
    }

    @Test
    fun carriesEachRowsOwnRecipeServings() = runTest {
        val chili = recipe("Chili", servings = 6)
        val salt = ingredient("Salt")
        database.recipeDao().upsertRecipe(chili)
        database.ingredientDao().upsertAll(listOf(salt))
        database.recipeIngredientDao().upsertAll(listOf(recipeIngredient(chili.uuid, salt.uuid)))

        val row = database.recipeDao().observeIngredientsForRecipes(listOf(chili.uuid)).first().single()

        assertEquals(6, row.recipeServings)
        assertEquals(1.0, row.quantity)
        assertEquals("cup", row.unit)
    }

    @Test
    fun excludesRowsWhoseRecipeIsSoftDeleted() = runTest {
        val deleted = recipe("Old Recipe", deletedAt = 999L)
        val salt = ingredient("Salt")
        database.recipeDao().upsertRecipe(deleted)
        database.ingredientDao().upsertAll(listOf(salt))
        database.recipeIngredientDao().upsertAll(listOf(recipeIngredient(deleted.uuid, salt.uuid)))

        val rows = database.recipeDao().observeIngredientsForRecipes(listOf(deleted.uuid)).first()

        assertTrue(rows.isEmpty())
    }

    @Test
    fun excludesRowsWhoseIngredientIsSoftDeleted() = runTest {
        val chili = recipe("Chili")
        val removedIngredient = ingredient("Removed", deletedAt = 999L)
        database.recipeDao().upsertRecipe(chili)
        database.ingredientDao().upsertAll(listOf(removedIngredient))
        database.recipeIngredientDao().upsertAll(
            listOf(recipeIngredient(chili.uuid, removedIngredient.uuid))
        )

        val rows = database.recipeDao().observeIngredientsForRecipes(listOf(chili.uuid)).first()

        assertTrue(rows.isEmpty())
    }

    @Test
    fun excludesRowsWhoseCrossRefItselfIsSoftDeleted() = runTest {
        val chili = recipe("Chili")
        val salt = ingredient("Salt")
        database.recipeDao().upsertRecipe(chili)
        database.ingredientDao().upsertAll(listOf(salt))
        database.recipeIngredientDao().upsertAll(
            listOf(recipeIngredient(chili.uuid, salt.uuid, deletedAt = 999L))
        )

        val rows = database.recipeDao().observeIngredientsForRecipes(listOf(chili.uuid)).first()

        assertTrue(rows.isEmpty())
    }

    @Test
    fun ignoresRecipesNotInTheRequestedIdList() = runTest {
        val requested = recipe("Chili")
        val other = recipe("Salad")
        val salt = ingredient("Salt")
        val lettuce = ingredient("Lettuce")
        database.recipeDao().upsertRecipe(requested)
        database.recipeDao().upsertRecipe(other)
        database.ingredientDao().upsertAll(listOf(salt, lettuce))
        database.recipeIngredientDao().upsertAll(
            listOf(
                recipeIngredient(requested.uuid, salt.uuid),
                recipeIngredient(other.uuid, lettuce.uuid),
            )
        )

        val rows = database.recipeDao().observeIngredientsForRecipes(listOf(requested.uuid)).first()

        assertEquals(listOf(salt.uuid), rows.map { it.ingredientId })
    }

    @Test
    fun aRecipeWithNoIngredientsYieldsNoRows() = runTest {
        val bare = recipe("Water")
        database.recipeDao().upsertRecipe(bare)

        val rows = database.recipeDao().observeIngredientsForRecipes(listOf(bare.uuid)).first()

        assertTrue(rows.isEmpty())
    }
}
