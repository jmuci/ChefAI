package com.tenmilelabs.chefai.recipes.data.worker

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeImageUploadCandidate
import org.junit.Test
import java.util.UUID

/**
 * Covers the half of the upload sweep SQL can't express — whether the bytes on disk are still the
 * ones the backend has. The query-side predicates (unsynced rows, soft deletes, the attempt cap,
 * user-photos-first ordering) are covered by the DAO test in androidTest.
 */
class RecipeImageUploadTest {

    private fun candidate(
        path: String = "/files/recipe_images/a",
        imageBlobId: String? = null,
        uploadedFileModifiedAt: Long? = null,
    ) = RecipeImageUploadCandidate(
        recipeId = UUID.randomUUID(),
        localImagePath = path,
        imageBlobId = imageBlobId,
        uploadedFileModifiedAt = uploadedFileModifiedAt,
    )

    /** Stands in for the filesystem: listed paths exist with the given mtime, nothing else does. */
    private fun mtimes(vararg pairs: Pair<String, Long>): (String) -> Long? =
        { path -> pairs.toMap()[path] }

    @Test
    fun `an image that has never been uploaded is a candidate`() {
        val candidates = listOf(candidate(imageBlobId = null))

        val result = candidates.needingUpload(mtimes("/files/recipe_images/a" to 100L))

        assertThat(result).isEqualTo(candidates)
    }

    @Test
    fun `an image already on the backend is left alone`() {
        val candidates = listOf(candidate(imageBlobId = "abc", uploadedFileModifiedAt = 100L))

        val result = candidates.needingUpload(mtimes("/files/recipe_images/a" to 100L))

        assertThat(result).isEmpty()
    }

    @Test
    fun `a replaced photo is re-uploaded even though the blob pointer is set`() {
        // The picker writes over the same path, so the row looks untouched: same localImagePath,
        // still a non-null imageBlobId. Only the file's mtime moved. Missing this would mean a
        // user's new photo silently never leaving the device.
        val candidates = listOf(candidate(imageBlobId = "abc", uploadedFileModifiedAt = 100L))

        val result = candidates.needingUpload(mtimes("/files/recipe_images/a" to 250L))

        assertThat(result).isEqualTo(candidates)
    }

    @Test
    fun `a row whose file has vanished is skipped, not uploaded`() {
        // Repairing this is the download sweep's job. Trying to upload nothing would just burn
        // attempts against a recipe that might still be recoverable.
        val candidates = listOf(candidate(imageBlobId = null))

        val result = candidates.needingUpload(mtimes())

        assertThat(result).isEmpty()
    }

    @Test
    fun `an uploaded image with no recorded mtime is re-uploaded`() {
        // Shouldn't happen — recordUploadSuccess always writes one — but treating "I don't know
        // what I sent" as "already done" would strand the bytes. Err toward re-sending; the backend
        // dedupes on content hash, so a redundant upload is a no-op there.
        val candidates = listOf(candidate(imageBlobId = "abc", uploadedFileModifiedAt = null))

        val result = candidates.needingUpload(mtimes("/files/recipe_images/a" to 100L))

        assertThat(result).isEqualTo(candidates)
    }

    @Test
    fun `only the rows genuinely out of date are returned`() {
        val fresh = candidate(path = "/p/fresh", imageBlobId = "x", uploadedFileModifiedAt = 10L)
        val never = candidate(path = "/p/never", imageBlobId = null)
        val replaced = candidate(path = "/p/replaced", imageBlobId = "y", uploadedFileModifiedAt = 10L)
        val gone = candidate(path = "/p/gone", imageBlobId = null)

        val result = listOf(fresh, never, replaced, gone).needingUpload(
            mtimes("/p/fresh" to 10L, "/p/never" to 20L, "/p/replaced" to 99L)
        )

        assertThat(result).containsExactly(never, replaced).inOrder()
    }

    @Test
    fun `the batch cap bounds how many images one run sends`() {
        val candidates = List(IMAGE_UPLOAD_BATCH * 3) { candidate(path = "/p/$it") }
        val existing = candidates.associate { it.localImagePath to 1L }

        val batch = candidates.needingUpload { existing[it] }.take(IMAGE_UPLOAD_BATCH)

        assertThat(batch).hasSize(IMAGE_UPLOAD_BATCH)
    }
}
