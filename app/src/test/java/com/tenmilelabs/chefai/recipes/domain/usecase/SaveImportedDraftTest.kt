package com.tenmilelabs.chefai.recipes.domain.usecase

import com.tenmilelabs.chefai.core.data.local.room.RecipeDraftEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDraftDao
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.recipes.domain.model.RecipeDraft
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

class SaveImportedDraftTest {

    private val recipeId = UUID.randomUUID()

    private fun draft(imageUrl: String = "https://example.com/hero.jpg") = RecipeDraft(
        recipeId = recipeId,
        isNewRecipe = true,
        title = "Scraped Recipe",
        imageUrl = imageUrl,
        updatedAt = 0L,
    )

    @Test
    fun `caches the hero image before writing the draft, and stores the returned local path`() = runTest {
        val draftDao: RecipeDraftDao = mockk(relaxed = true)
        val cacheRecipeImage: CacheRecipeImage = mockk()
        coEvery { cacheRecipeImage(recipeId, "https://example.com/hero.jpg") } returns "/files/recipe_images/$recipeId"
        val useCase = SaveImportedDraft(draftDao, cacheRecipeImage, Dispatchers.Unconfined)

        useCase(draft())

        val saved = slot<RecipeDraftEntity>()
        coVerify(exactly = 1) { draftDao.saveDraft(capture(saved)) }
        coVerify(exactly = 1) { cacheRecipeImage(recipeId, "https://example.com/hero.jpg") }
        org.junit.Assert.assertEquals("/files/recipe_images/$recipeId", saved.captured.localImagePath)
    }

    @Test
    fun `a draft is still saved when the image fails to cache, with a null local path`() = runTest {
        val draftDao: RecipeDraftDao = mockk(relaxed = true)
        val cacheRecipeImage: CacheRecipeImage = mockk()
        coEvery { cacheRecipeImage(recipeId, any()) } returns null
        val useCase = SaveImportedDraft(draftDao, cacheRecipeImage, Dispatchers.Unconfined)

        useCase(draft())

        val saved = slot<RecipeDraftEntity>()
        coVerify(exactly = 1) { draftDao.saveDraft(capture(saved)) }
        org.junit.Assert.assertNull(saved.captured.localImagePath)
    }

    @Test
    fun `a blank image url still asks CacheRecipeImage, which is expected to no-op`() = runTest {
        val draftDao: RecipeDraftDao = mockk(relaxed = true)
        val cacheRecipeImage: CacheRecipeImage = mockk()
        coEvery { cacheRecipeImage(recipeId, "") } returns null
        val useCase = SaveImportedDraft(draftDao, cacheRecipeImage, Dispatchers.Unconfined)

        useCase(draft(imageUrl = ""))

        coVerify(exactly = 1) { cacheRecipeImage(recipeId, "") }
        coVerify(exactly = 1) { draftDao.saveDraft(any()) }
    }

    @Test
    fun `a scraped NaN quantity does not crash the save — issue 336`() = runTest {
        // Given a draft whose only ingredient has a NaN quantity
        val draftDao: RecipeDraftDao = mockk(relaxed = true)
        val cacheRecipeImage: CacheRecipeImage = mockk()
        coEvery { cacheRecipeImage(recipeId, any()) } returns null
        val useCase = SaveImportedDraft(draftDao, cacheRecipeImage, Dispatchers.Unconfined)
        val poisoned = draft().copy(
            ingredients = listOf(
                RecipeIngredient(
                    ingredientId = UUID.randomUUID(),
                    ingredientDisplayName = "Flour",
                    quantity = Double.NaN,
                    unit = "g",
                    allergenName = null,
                    srcCategory = null,
                    srcSubcategory = null,
                )
            )
        )

        // When saved
        useCase(poisoned)

        // Then the draft is written, with the quantity healed to no amount
        val saved = slot<RecipeDraftEntity>()
        coVerify(exactly = 1) { draftDao.saveDraft(capture(saved)) }
        org.junit.Assert.assertTrue(saved.captured.ingredientsJson.contains("\"quantity\":0.0"))
    }
}
