package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.RecipeDraftEntity
import java.util.UUID

class FakeRecipeDraftDao : RecipeDraftDao {

    private val drafts = mutableMapOf<UUID, RecipeDraftEntity>()

    override suspend fun saveDraft(draft: RecipeDraftEntity) {
        drafts[draft.recipeId] = draft
    }

    override suspend fun getDraft(recipeId: UUID): RecipeDraftEntity? = drafts[recipeId]

    override suspend fun deleteDraft(recipeId: UUID) {
        drafts.remove(recipeId)
    }

    override suspend fun deleteAllDrafts() {
        drafts.clear()
    }

    /** Test helper: returns the number of stored drafts. */
    fun count(): Int = drafts.size
}
