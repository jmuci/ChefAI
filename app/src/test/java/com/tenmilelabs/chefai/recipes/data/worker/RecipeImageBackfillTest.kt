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
    ) = RecipeImageCandidate(
        recipeId = UUID.randomUUID(),
        imageUrl = imageUrl,
        localImagePath = localImagePath,
    )

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
    fun `the batch cap bounds how many images one run downloads`() {
        val candidates = List(IMAGE_BACKFILL_BATCH * 3) { candidate() }

        val batch = candidates.needingBackfill(existing()).take(IMAGE_BACKFILL_BATCH)

        assertThat(batch).hasSize(IMAGE_BACKFILL_BATCH)
    }
}
