package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import java.util.UUID

class FakeRecipeTagCrossRefDao : RecipeTagCrossRefDao {

    private val recipeTags = mutableListOf<RecipeTagCrossRef>()

    override suspend fun upsertCrossRef(crossRef: RecipeTagCrossRef) {
        val existingIndex = recipeTags.indexOfFirst {
            it.recipeId == crossRef.recipeId && it.tagId == crossRef.tagId
        }
        if (existingIndex != -1) {
            recipeTags[existingIndex] = crossRef
        } else {
            recipeTags.add(crossRef)
        }
    }

    override suspend fun getTagsForRecipe(recipeId: UUID): List<RecipeTagCrossRef> {
        return recipeTags.filter { it.recipeId == recipeId }
    }

    override suspend fun upsertAll(crossRefs: List<RecipeTagCrossRef>) {
        crossRefs.forEach { upsertCrossRef(it) }
    }

    override suspend fun deleteAllForRecipe(recipeId: UUID) {
        recipeTags.removeAll { it.recipeId == recipeId }
    }

    override suspend fun updateSyncStateForRecipe(
        recipeId: UUID,
        syncState: SyncState,
        updatedAt: Long
    ) {
        val updated = recipeTags.map { tag ->
            if (tag.recipeId == recipeId) tag.copy(
                syncState = syncState,
                updatedAt = updatedAt
            ) else tag
        }
        recipeTags.clear()
        recipeTags.addAll(updated)
    }

    override suspend fun markPendingForRecipes(
        recipeIds: List<UUID>,
        updatedAt: Long
    ) {
        val updated = recipeTags.map { tagCrossRef ->
            if (tagCrossRef.recipeId in recipeIds) {
                tagCrossRef.copy(syncState = SyncState.PENDING, updatedAt = updatedAt)
            } else {
                tagCrossRef
            }
        }
        recipeTags.clear()
        recipeTags.addAll(updated)
    }
}