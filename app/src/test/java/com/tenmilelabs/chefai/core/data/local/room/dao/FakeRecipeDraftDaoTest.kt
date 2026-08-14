package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.RecipeDraftEntity
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FakeRecipeDraftDaoTest {

    private lateinit var dao: FakeRecipeDraftDao

    private val draftId = UUID.randomUUID()
    private val sampleDraft = RecipeDraftEntity(
        recipeId = draftId,
        isNewRecipe = true,
        title = "Test Recipe",
        description = "Test description",
        imageUrl = "",
        localImagePath = null,
        prepTimeMinutes = "10",
        cookTimeMinutes = "20",
        servings = "4",
        externalUrl = "",
        privacy = RecipePrivacy.PRIVATE,
        ingredientsJson = "[]",
        stepsJson = "[]",
        tagsJson = "[]",
        labelsJson = "[]",
        version = 1,
        updatedAt = 1000L,
    )

    @Before
    fun setup() {
        dao = FakeRecipeDraftDao()
    }

    @Test
    fun `saveDraft and getDraft round-trip`() = runTest {
        dao.saveDraft(sampleDraft)

        val retrieved = dao.getDraft(draftId)
        assertNotNull(retrieved)
        assertEquals(sampleDraft, retrieved)
    }

    @Test
    fun `getDraft returns null for unknown id`() = runTest {
        assertNull(dao.getDraft(UUID.randomUUID()))
    }

    @Test
    fun `saveDraft upserts existing draft`() = runTest {
        dao.saveDraft(sampleDraft)

        val updated = sampleDraft.copy(title = "Updated Title", updatedAt = 2000L)
        dao.saveDraft(updated)

        val retrieved = dao.getDraft(draftId)
        assertEquals("Updated Title", retrieved?.title)
        assertEquals(2000L, retrieved?.updatedAt)
        assertEquals(1, dao.count())
    }

    @Test
    fun `deleteDraft removes specific draft`() = runTest {
        val otherId = UUID.randomUUID()
        dao.saveDraft(sampleDraft)
        dao.saveDraft(sampleDraft.copy(recipeId = otherId))

        dao.deleteDraft(draftId)

        assertNull(dao.getDraft(draftId))
        assertNotNull(dao.getDraft(otherId))
        assertEquals(1, dao.count())
    }

    @Test
    fun `deleteAllDrafts clears everything`() = runTest {
        dao.saveDraft(sampleDraft)
        dao.saveDraft(sampleDraft.copy(recipeId = UUID.randomUUID()))

        dao.deleteAllDrafts()

        assertEquals(0, dao.count())
    }
}
