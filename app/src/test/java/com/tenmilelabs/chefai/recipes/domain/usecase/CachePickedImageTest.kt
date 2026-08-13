package com.tenmilelabs.chefai.recipes.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

private const val PICKED_URI = "content://media/external/images/media/42"
private const val STORED_PATH = "/files/recipe_images/stored"

class CachePickedImageTest {

    private val recipeId: UUID = UUID.randomUUID()

    private fun useCase(store: RecipeImageStore) = CachePickedImage(
        recipeImageStore = store,
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `returns the path the store wrote the picked photo to`() = runTest {
        val store = mockk<RecipeImageStore> {
            coEvery { writeFromUri(any(), any()) } returns STORED_PATH
        }

        val result = useCase(store).invoke(recipeId, PICKED_URI)

        assertThat(result).isEqualTo(STORED_PATH)
        coVerify(exactly = 1) { store.writeFromUri(recipeId, PICKED_URI) }
    }

    @Test
    fun `returns null without touching the store when the uri is blank`() = runTest {
        val store = mockk<RecipeImageStore>()

        val result = useCase(store).invoke(recipeId, "   ")

        assertThat(result).isNull()
        coVerify(exactly = 0) { store.writeFromUri(any(), any()) }
    }

    @Test
    fun `returns null when the store cannot read the image`() = runTest {
        val store = mockk<RecipeImageStore> {
            coEvery { writeFromUri(any(), any()) } returns null
        }

        val result = useCase(store).invoke(recipeId, PICKED_URI)

        assertThat(result).isNull()
    }
}
