package com.tenmilelabs.chefai.recipes.data.worker

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeImageCandidate
import org.junit.Test
import java.util.UUID

private const val REMOTE_URL = "https://food.fnr.sndimg.com/hero.webp"

/**
 * Covers the half of the backfill the DAO query can't express — whether the file a row points at is
 * actually on disk. The SQL-side predicates (dead URLs, user photos, soft-deleted rows) are covered
 * by the DAO test in androidTest.
 */
class RecipeImageBackfillTest {

    private fun candidate(
        localImagePath: String? = null,
        imageUrl: String = REMOTE_URL,
        imageBlobId: String? = null,
        creatorId: UUID = me,
    ) = RecipeImageCandidate(
        recipeId = UUID.randomUUID(),
        imageUrl = imageUrl,
        localImagePath = localImagePath,
        imageBlobId = imageBlobId,
        creatorId = creatorId,
    )

    private val me = UUID.randomUUID()
    private val someoneElse = UUID.randomUUID()

    /** Stands in for the filesystem: every listed path exists, nothing else does. */
    private fun existing(vararg paths: String): (String) -> Boolean = { it in paths }

    @Test
    fun `a recipe that has never been cached needs backfill`() {
        val candidates = listOf(candidate(localImagePath = null))

        val result = candidates.needingBackfill(existing())

        assertThat(result).isEqualTo(candidates)
    }

    @Test
    fun `a recipe whose image is on disk is left alone`() {
        val candidates = listOf(candidate(localImagePath = "/files/recipe_images/a"))

        val result = candidates.needingBackfill(existing("/files/recipe_images/a"))

        assertThat(result).isEmpty()
    }

    @Test
    fun `a recipe whose file was deleted underneath it is repaired`() {
        // The row still points somewhere, but the bytes are gone — an account switch or a manual
        // clear. Trusting the column alone would leave this recipe permanently pictureless.
        val candidates = listOf(candidate(localImagePath = "/files/recipe_images/vanished"))

        val result = candidates.needingBackfill(existing())

        assertThat(result).isEqualTo(candidates)
    }

    @Test
    fun `only the rows actually missing an image are returned`() {
        val cached = candidate(localImagePath = "/files/recipe_images/present")
        val neverCached = candidate(localImagePath = null)
        val stale = candidate(localImagePath = "/files/recipe_images/gone")

        val result = listOf(cached, neverCached, stale)
            .needingBackfill(existing("/files/recipe_images/present"))

        assertThat(result).containsExactly(neverCached, stale).inOrder()
    }

    @Test
    fun `an empty candidate list yields no work`() {
        assertThat(emptyList<RecipeImageCandidate>().needingBackfill(existing())).isEmpty()
    }

    @Test
    fun `a user photo with an uploaded blob is now a candidate — it was not in Stage 1`() {
        // Blank imageUrl means the image can't be re-derived from anywhere, so before Stage 2 this
        // row was deliberately excluded from the sweep entirely. Once the bytes are on the backend
        // there *is* somewhere to fetch them from, which is the whole point of the upload.
        val candidates = listOf(candidate(imageUrl = "", imageBlobId = "abc"))

        val result = candidates.needingBackfill(existing())

        assertThat(result).isEqualTo(candidates)
    }

    @Test
    fun `a user photo already on disk is still left alone`() {
        val candidates = listOf(
            candidate(imageUrl = "", imageBlobId = "abc", localImagePath = "/files/recipe_images/a")
        )

        val result = candidates.needingBackfill(existing("/files/recipe_images/a"))

        assertThat(result).isEmpty()
    }

    @Test
    fun `the batch cap bounds how many images one run downloads`() {
        val candidates = List(IMAGE_BACKFILL_BATCH * 3) { candidate() }

        val batch = candidates.needingBackfill(existing()).take(IMAGE_BACKFILL_BATCH)

        assertThat(batch).hasSize(IMAGE_BACKFILL_BATCH)
    }

    // --- Whose image URLs may be fetched (security review A3) ---

    @Test
    fun `the url ladder is used only for recipes the current user owns`() {
        assertThat(mayFetchImageFromUrl("https://attacker.example/x.png", someoneElse, me)).isFalse()
        assertThat(mayFetchImageFromUrl("https://attacker.example/x.png", someoneElse, null)).isFalse()
        assertThat(mayFetchImageFromUrl(REMOTE_URL, me, null)).isFalse()
        assertThat(mayFetchImageFromUrl(REMOTE_URL, me, me)).isTrue()
        assertThat(mayFetchImageFromUrl("", me, me)).isFalse()
    }

    @Test
    fun `another user's recipe is fetchable only through its backend blob`() {
        val ownUrl = candidate()
        val othersUrl = candidate(creatorId = someoneElse)
        val othersBlob = candidate(creatorId = someoneElse, imageBlobId = "blob-1")

        val result = listOf(ownUrl, othersUrl, othersBlob).fetchableBy(me)

        assertThat(result).containsExactly(ownUrl, othersBlob)
    }
}
