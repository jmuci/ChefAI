package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import java.util.UUID

class FakeRecipeLabelCrossRefDao : RecipeLabelCrossRefDao {

    val recipeLabels = mutableListOf<RecipeLabelCrossRef>()

    override suspend fun upsertCrossRef(crossRef: RecipeLabelCrossRef) {
        val existingIndex = recipeLabels.indexOfFirst {
            it.recipeId == crossRef.recipeId && it.labelId == crossRef.labelId
        }
        if (existingIndex != -1) {
            recipeLabels[existingIndex] = crossRef
        } else {
            recipeLabels.add(crossRef)
        }
    }

    override suspend fun getLabelsForRecipe(recipeId: UUID): List<RecipeLabelCrossRef> {
        return recipeLabels.filter { it.recipeId == recipeId }
    }

    override suspend fun upsertAll(crossRefs: List<RecipeLabelCrossRef>) {
        crossRefs.forEach { upsertCrossRef(it) }
    }

    override suspend fun deleteAllForRecipe(recipeId: UUID) {
        recipeLabels.removeAll { it.recipeId == recipeId }
    }

    override suspend fun updateSyncStateForRecipe(recipeId: UUID, syncState: SyncState, updatedAt: Long) {
        val updated = recipeLabels.map { label ->
            if (label.recipeId == recipeId) label.copy(syncState = syncState, updatedAt = updatedAt) else label
        }
        recipeLabels.clear()
        recipeLabels.addAll(updated)
    }

    override suspend fun markPendingForRecipes(recipeIds: List<UUID>, updatedAt: Long) {
        val updated = recipeLabels.map { label ->
            if (label.recipeId in recipeIds) {
                label.copy(syncState = SyncState.PENDING, updatedAt = updatedAt)
            } else {
                label
            }
        }
        recipeLabels.clear()
        recipeLabels.addAll(updated)
    }
}
