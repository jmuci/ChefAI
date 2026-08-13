package com.tenmilelabs.chefai.data.source.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeImageStateEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.recipes.data.worker.MAX_IMAGE_BACKFILL_ATTEMPTS
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

private const val SCAN_LIMIT = 200

/**
 * Covers the SQL-side half of the image backfill: which rows the sweep is even willing to look at.
 * The file-existence half lives in `RecipeImageBackfillTest` (JVM).
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class RecipeImageBackfillDaoTest {

    private lateinit var database: ChefAIDataBase

    private val testUser = UserEntity(
        uuid = UuidV7Generator.newId(),
        displayName = "Test User",
        email = "test@test.com",
        avatarUrl = "",
        updatedAt = System.currentTimeMillis(),
        deletedAt = null
    )

    private fun recipe(
        imageUrl: String = "https://food.fnr.sndimg.com/hero.webp",
        localImagePath: String? = null,
        deletedAt: Long? = null,
        updatedAt: Long = System.currentTimeMillis(),
    ) = RecipeEntity(
        uuid = UuidV7Generator.newId(),
        title = "Recipe",
        description = "",
        imageUrl = imageUrl,
        imageUrlThumbnail = imageUrl,
        localImagePath = localImagePath,
        prepTimeMinutes = 5,
        cookTimeMinutes = 5,
        servings = 1,
        creatorId = testUser.uuid,
        recipeExternalUrl = null,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private suspend fun candidates(): List<UUID> = database.recipeDao()
        .getImageBackfillCandidates(MAX_IMAGE_BACKFILL_ATTEMPTS, SCAN_LIMIT)
        .map { it.recipeId }

    @Before
    fun setup() = runTest {
        database = Room.inMemoryDatabaseBuilder(getApplicationContext(), ChefAIDataBase::class.java)
            .allowMainThreadQueries()
            .build()
        database.userDao().upsertUser(testUser)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun selectsARecipeThatHasNeverCachedItsImage() = runTest {
        val recipe = recipe()
        database.recipeDao().upsertRecipe(recipe)

        assertEquals(listOf(recipe.uuid), candidates())
    }

    @Test
    fun stillSelectsARecipeThatAlreadyHasAPath_soAMissingFileCanBeRepaired() = runTest {
        // The query can't stat the filesystem, so it deliberately over-selects and the worker filters.
        val recipe = recipe(localImagePath = "/files/recipe_images/whatever")
        database.recipeDao().upsertRecipe(recipe)

        assertEquals(listOf(recipe.uuid), candidates())
    }

    @Test
    fun skipsAUserPickedPhoto() = runTest {
        // No imageUrl means the image is the user's own: there is nothing to re-derive it from, and
        // re-deriving would be wrong even if there were.
        database.recipeDao().upsertRecipe(recipe(imageUrl = ""))

        assertTrue(candidates().isEmpty())
    }

    @Test
    fun skipsASoftDeletedRecipe() = runTest {
        database.recipeDao().upsertRecipe(recipe(deletedAt = System.currentTimeMillis()))

        assertTrue(candidates().isEmpty())
    }

    @Test
    fun skipsARecipeThatHasExhaustedItsAttempts() = runTest {
        val recipe = recipe()
        database.recipeDao().upsertRecipe(recipe)
        database.recipeImageStateDao().upsert(
            RecipeImageStateEntity(recipe.uuid, MAX_IMAGE_BACKFILL_ATTEMPTS, 1L)
        )

        assertTrue(candidates().isEmpty())
    }

    @Test
    fun stillSelectsARecipeWithAttemptsRemaining() = runTest {
        val recipe = recipe()
        database.recipeDao().upsertRecipe(recipe)
        database.recipeImageStateDao().upsert(
            RecipeImageStateEntity(recipe.uuid, MAX_IMAGE_BACKFILL_ATTEMPTS - 1, 1L)
        )

        assertEquals(listOf(recipe.uuid), candidates())
    }

    @Test
    fun returnsTheMostRecentlyUpdatedRecipesFirst() = runTest {
        val older = recipe(updatedAt = 1_000L)
        val newer = recipe(updatedAt = 2_000L)
        database.recipeDao().upsertRecipe(older)
        database.recipeDao().upsertRecipe(newer)

        assertEquals(listOf(newer.uuid, older.uuid), candidates())
    }

    @Test
    fun scanLimitBoundsTheNumberOfRowsExamined() = runTest {
        repeat(5) { database.recipeDao().upsertRecipe(recipe()) }

        val limited = database.recipeDao().getImageBackfillCandidates(
            maxAttempts = MAX_IMAGE_BACKFILL_ATTEMPTS,
            scanLimit = 2,
        )

        assertEquals(2, limited.size)
    }

    @Test
    fun deletingARecipeCascadesToItsImageState() = runTest {
        val recipe = recipe()
        database.recipeDao().upsertRecipe(recipe)
        database.recipeImageStateDao().upsert(RecipeImageStateEntity(recipe.uuid, 1, 1L))

        database.recipeDao().deleteRecipe(recipe.uuid)

        assertEquals(null, database.recipeImageStateDao().getByRecipeId(recipe.uuid))
    }
}
