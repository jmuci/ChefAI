package com.tenmilelabs.chefai.data.source.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.recipes.data.worker.MAX_IMAGE_UPLOAD_ATTEMPTS
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

private const val SCAN_LIMIT = 200
private const val LOCAL_PATH = "/files/recipe_images/x"

/**
 * Covers the SQL-side half of the image upload sweep: which rows it is willing to consider at all.
 * The "are these still the bytes we sent" half lives in `RecipeImageUploadTest` (JVM).
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class RecipeImageUploadDaoTest {

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
        localImagePath: String? = LOCAL_PATH,
        imageBlobId: String? = null,
        deletedAt: Long? = null,
        syncState: SyncState = SyncState.SYNCED,
        updatedAt: Long = System.currentTimeMillis(),
    ) = RecipeEntity(
        uuid = UuidV7Generator.newId(),
        title = "Recipe",
        description = "",
        imageUrl = imageUrl,
        imageUrlThumbnail = imageUrl,
        localImagePath = localImagePath,
        imageBlobId = imageBlobId,
        prepTimeMinutes = 5,
        cookTimeMinutes = 5,
        servings = 1,
        creatorId = testUser.uuid,
        recipeExternalUrl = null,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        syncState = syncState,
    )

    private suspend fun candidates(): List<UUID> = database.recipeDao()
        .getImageUploadCandidates(MAX_IMAGE_UPLOAD_ATTEMPTS, SCAN_LIMIT)
        .map { it.recipeId }

    @Before
    fun setup() = runTest {
        database = Room.inMemoryDatabaseBuilder(getApplicationContext(), ChefAIDataBase::class.java)
            .allowMainThreadQueries()
            .build()
        database.userDao().upsertUser(testUser)
    }

    @After
    fun teardown() = database.close()

    @Test
    fun aSyncedRecipeWithACachedImageIsACandidate() = runTest {
        val target = recipe()
        database.recipeDao().upsertRecipe(target)

        assertEquals(listOf(target.uuid), candidates())
    }

    @Test
    fun aRecipeWithNoCachedImageIsNotACandidate() = runTest {
        database.recipeDao().upsertRecipe(recipe(localImagePath = null))

        assertTrue(candidates().isEmpty())
    }

    @Test
    fun anUnsyncedRecipeIsNotACandidate() = runTest {
        // The recipe row has to reach the server before its image can attach to it. Uploading first
        // would 404, so PENDING rows wait for the push that is about to happen anyway.
        database.recipeDao().upsertRecipe(recipe(syncState = SyncState.PENDING))

        assertTrue(candidates().isEmpty())
    }

    @Test
    fun aSoftDeletedRecipeIsNotACandidate() = runTest {
        database.recipeDao().upsertRecipe(recipe(deletedAt = System.currentTimeMillis()))

        assertTrue(candidates().isEmpty())
    }

    @Test
    fun aRecipeThatHasExhaustedItsUploadAttemptsIsNotACandidate() = runTest {
        val target = recipe()
        database.recipeDao().upsertRecipe(target)
        repeat(MAX_IMAGE_UPLOAD_ATTEMPTS) {
            database.recipeImageStateDao().recordUploadAttempt(target.uuid, 1L)
        }

        assertTrue(candidates().isEmpty())
    }

    @Test
    fun aRecipeOneAttemptShortOfTheCapIsStillACandidate() = runTest {
        val target = recipe()
        database.recipeDao().upsertRecipe(target)
        repeat(MAX_IMAGE_UPLOAD_ATTEMPTS - 1) {
            database.recipeImageStateDao().recordUploadAttempt(target.uuid, 1L)
        }

        assertEquals(listOf(target.uuid), candidates())
    }

    @Test
    fun anAlreadyUploadedRecipeStillComesBack_theFileCheckIsTheCallersJob() = runTest {
        // SQL can't tell whether the bytes on disk are still the ones that were sent, so the query
        // deliberately over-selects and the worker narrows on mtime.
        val target = recipe(imageBlobId = "abc")
        database.recipeDao().upsertRecipe(target)

        assertEquals(listOf(target.uuid), candidates())
    }

    @Test
    fun userPhotosAreOrderedAheadOfScrapedImages() = runTest {
        // A blank imageUrl means the image cannot be re-derived from anywhere (ADR-011 Decision 3).
        // If a sweep only gets partway through its batch, those are the bytes worth saving first —
        // even though this scraped recipe is the more recently updated of the two.
        val userPhoto = recipe(imageUrl = "", updatedAt = 1_000L)
        val scraped = recipe(updatedAt = 9_000L)
        database.recipeDao().upsertRecipe(scraped)
        database.recipeDao().upsertRecipe(userPhoto)

        assertEquals(listOf(userPhoto.uuid, scraped.uuid), candidates())
    }

    @Test
    fun recordUploadSuccessClearsAttemptsAndRemembersTheFile() = runTest {
        val target = recipe()
        database.recipeDao().upsertRecipe(target)
        database.recipeImageStateDao().recordUploadAttempt(target.uuid, 1L)

        database.recipeImageStateDao().recordUploadSuccess(target.uuid, modifiedAt = 4242L)

        val state = database.recipeImageStateDao().getByRecipeId(target.uuid)
        assertEquals(0, state?.uploadAttempts)
        assertEquals(4242L, state?.uploadedFileModifiedAt)
    }

    @Test
    fun burnUploadAttemptsRemovesTheRecipeFromFutureSweepsInOneGo() = runTest {
        val target = recipe()
        database.recipeDao().upsertRecipe(target)

        database.recipeImageStateDao()
            .burnUploadAttempts(target.uuid, at = 1L, cap = MAX_IMAGE_UPLOAD_ATTEMPTS)

        assertTrue(candidates().isEmpty())
    }

    @Test
    fun uploadAndDownloadAttemptCountsDoNotInterfere() = runTest {
        // The two ladders share a row but must not share a budget: an image the backend keeps
        // refusing should still be eligible for a download, and vice versa.
        val target = recipe()
        database.recipeDao().upsertRecipe(target)

        database.recipeImageStateDao()
            .burnUploadAttempts(target.uuid, at = 1L, cap = MAX_IMAGE_UPLOAD_ATTEMPTS)

        val state = database.recipeImageStateDao().getByRecipeId(target.uuid)
        assertEquals(MAX_IMAGE_UPLOAD_ATTEMPTS, state?.uploadAttempts)
        assertEquals("download attempts untouched", 0, state?.attempts)
    }

    @Test
    fun updateImageBlobIdDoesNotDisturbTheRowsSyncStateOrTimestamp() = runTest {
        // The value came from the server, so pushing it back would churn the pull delta forever.
        val target = recipe(updatedAt = 777L)
        database.recipeDao().upsertRecipe(target)

        database.recipeDao().updateImageBlobId(target.uuid, "abc")

        val stored = database.recipeDao().getRecipeById(target.uuid)
        assertEquals("abc", stored?.imageBlobId)
        assertEquals(777L, stored?.updatedAt)
        assertEquals(SyncState.SYNCED, stored?.syncState)
        assertNull(stored?.deletedAt)
    }
}
