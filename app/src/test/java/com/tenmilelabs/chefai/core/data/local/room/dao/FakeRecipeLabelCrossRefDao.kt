package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import java.util.UUID

class FakeRecipeLabelCrossRefDao : RecipeLabelCrossRefDao {

    val recipeLabels = mutableListOf<RecipeLabelCrossRef>()

    override suspend fun upsertCrossRef(crossRef: RecipeLabelCrossRef) {
        recipeLabels.add(crossRef)
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
