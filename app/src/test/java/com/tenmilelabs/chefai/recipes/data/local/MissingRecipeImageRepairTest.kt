package com.tenmilelabs.chefai.recipes.data.local

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeImageStateDao
import com.tenmilelabs.chefai.core.data.sync.FakeSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

class MissingRecipeImageRepairTest {

    private val recipeDao = FakeRecipeDao()
    private val imageStateDao = FakeRecipeImageStateDao()
    private val syncScheduler = FakeSyncManager()

    private fun repair(vararg existingPaths: String) = MissingRecipeImageRepair(
        recipeDao = recipeDao,
        recipeImageStateDao = imageStateDao,
        syncScheduler = syncScheduler,
        ioDispatcher = Dispatchers.Unconfined,
    ).apply { fileExists = { it in existingPaths } }

    @Test
    fun `a row pointing at a missing file falls back to its remote image`() = runTest {
        // Given a recipe restored from a backup whose cached file never came back
        val recipe = recipe(localImagePath = "/data/recipe_images/gone")
        recipeDao.upsertRecipe(recipe)
        imageStateDao.recordDownloadAttempt(recipe.uuid, at = 1L)

        // When
        val repaired = repair().invoke()

        // Then the path is cleared, the attempts reset and a backfill scheduled
        assertThat(repaired).isEqualTo(1)
        assertThat(recipeDao.getRecipeById(recipe.uuid)?.localImagePath).isNull()
        assertThat(imageStateDao.getByRecipeId(recipe.uuid)).isNull()
        assertThat(syncScheduler.imageBackfillCount).isEqualTo(1)
    }

    @Test
    fun `a row whose file exists is left alone`() = runTest {
        // Given
        val path = "/data/recipe_images/present"
        val recipe = recipe(localImagePath = path)
        recipeDao.upsertRecipe(recipe)

        // When
        val repaired = repair(path).invoke()

        // Then
        assertThat(repaired).isEqualTo(0)
        assertThat(recipeDao.getRecipeById(recipe.uuid)?.localImagePath).isEqualTo(path)
        assertThat(syncScheduler.imageBackfillCount).isEqualTo(0)
    }

    @Test
    fun `a row with no cached image is ignored`() = runTest {
        // Given
        recipeDao.upsertRecipe(recipe(localImagePath = null))

        // When
        val repaired = repair().invoke()

        // Then
        assertThat(repaired).isEqualTo(0)
        assertThat(syncScheduler.imageBackfillCount).isEqualTo(0)
    }

    @Test
    fun `a path replaced since it was read is not cleared`() = runTest {
        // Given a backfill that stored a fresh copy after the row was read
        val recipe = recipe(localImagePath = "/data/recipe_images/fresh")
        recipeDao.upsertRecipe(recipe)

        // When the stale path is cleared
        recipeDao.clearLocalImagePathIfUnchanged(recipe.uuid, "/data/recipe_images/stale")

        // Then the fresh copy survives
        assertThat(recipeDao.getRecipeById(recipe.uuid)?.localImagePath)
            .isEqualTo("/data/recipe_images/fresh")
    }

    private fun recipe(localImagePath: String?) = RecipeEntity(
        uuid = UuidV7Generator.newId(),
        title = "Paella",
        description = "",
        imageUrl = "https://example.com/paella.jpeg",
        imageUrlThumbnail = "https://example.com/paella.jpeg",
        prepTimeMinutes = 1,
        cookTimeMinutes = 1,
        servings = 1,
        creatorId = UUID.randomUUID(),
        recipeExternalUrl = null,
        updatedAt = 1L,
        deletedAt = null,
        localImagePath = localImagePath,
    )
}
