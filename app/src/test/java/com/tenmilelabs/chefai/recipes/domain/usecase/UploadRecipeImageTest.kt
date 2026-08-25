package com.tenmilelabs.chefai.recipes.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeImageStateDao
import com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore
import com.tenmilelabs.chefai.recipes.data.network.ImageUploadOutcome
import com.tenmilelabs.chefai.recipes.data.network.RecipeImageUploader
import com.tenmilelabs.chefai.recipes.data.worker.MAX_IMAGE_UPLOAD_ATTEMPTS
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

private val IMAGE_BYTES = byteArrayOf(9, 8, 7)

class UploadRecipeImageTest {

    private val recipeId = UUID.randomUUID()

    private fun useCase(
        recipeImageStore: RecipeImageStore = mockk {
            coEvery { read(recipeId) } returns IMAGE_BYTES
            coEvery { lastModified(recipeId) } returns 111L
        },
        recipeImageUploader: RecipeImageUploader = mockk(),
        recipeDao: RecipeDao = mockk(relaxed = true),
        recipeImageStateDao: RecipeImageStateDao = mockk(relaxed = true),
    ) = UploadRecipeImage(
        recipeImageStore = recipeImageStore,
        recipeImageUploader = recipeImageUploader,
        recipeDao = recipeDao,
        recipeImageStateDao = recipeImageStateDao,
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `no local file is a no-op that returns false`() = runTest {
        val store: RecipeImageStore = mockk { coEvery { read(recipeId) } returns null }
        val uploader: RecipeImageUploader = mockk()
        val recipeDao: RecipeDao = mockk(relaxed = true)
        val stateDao: RecipeImageStateDao = mockk(relaxed = true)

        val result = useCase(store, uploader, recipeDao, stateDao).invoke(recipeId)

        assertThat(result).isFalse()
        coVerify(exactly = 0) { uploader.upload(any(), any()) }
        coVerify(exactly = 0) { recipeDao.updateImageBlobId(any(), any()) }
    }

    @Test
    fun `success writes the returned blob id and records success, keyed to the pre-upload mtime`() = runTest {
        val uploader: RecipeImageUploader = mockk {
            coEvery { upload(recipeId, IMAGE_BYTES) } returns ImageUploadOutcome.Success("blob-123")
        }
        val recipeDao: RecipeDao = mockk(relaxed = true)
        val stateDao: RecipeImageStateDao = mockk(relaxed = true)

        val result = useCase(recipeImageUploader = uploader, recipeDao = recipeDao, recipeImageStateDao = stateDao)
            .invoke(recipeId)

        assertThat(result).isTrue()
        coVerify(exactly = 1) { recipeDao.updateImageBlobId(recipeId, "blob-123") }
        coVerify(exactly = 1) { stateDao.recordUploadSuccess(recipeId, 111L) }
        coVerify(exactly = 0) { stateDao.burnUploadAttempts(any(), any(), any()) }
    }

    @Test
    fun `a permanent rejection burns the attempt budget and does not touch the recipe row`() = runTest {
        val uploader: RecipeImageUploader = mockk {
            coEvery { upload(recipeId, IMAGE_BYTES) } returns ImageUploadOutcome.Permanent("hash mismatch")
        }
        val recipeDao: RecipeDao = mockk(relaxed = true)
        val stateDao: RecipeImageStateDao = mockk(relaxed = true)

        val result = useCase(recipeImageUploader = uploader, recipeDao = recipeDao, recipeImageStateDao = stateDao)
            .invoke(recipeId)

        assertThat(result).isFalse()
        coVerify(exactly = 0) { recipeDao.updateImageBlobId(any(), any()) }
        coVerify(exactly = 0) { stateDao.recordUploadSuccess(any(), any()) }
        coVerify(exactly = 1) { stateDao.burnUploadAttempts(recipeId, any(), MAX_IMAGE_UPLOAD_ATTEMPTS) }
    }

    @Test
    fun `a transient failure leaves bookkeeping untouched so the sweep retries later`() = runTest {
        val uploader: RecipeImageUploader = mockk {
            coEvery { upload(recipeId, IMAGE_BYTES) } returns ImageUploadOutcome.Transient("HTTP 503")
        }
        val recipeDao: RecipeDao = mockk(relaxed = true)
        val stateDao: RecipeImageStateDao = mockk(relaxed = true)

        val result = useCase(recipeImageUploader = uploader, recipeDao = recipeDao, recipeImageStateDao = stateDao)
            .invoke(recipeId)

        assertThat(result).isFalse()
        coVerify(exactly = 0) { recipeDao.updateImageBlobId(any(), any()) }
        coVerify(exactly = 0) { stateDao.recordUploadSuccess(any(), any()) }
        coVerify(exactly = 0) { stateDao.burnUploadAttempts(any(), any(), any()) }
    }
}
