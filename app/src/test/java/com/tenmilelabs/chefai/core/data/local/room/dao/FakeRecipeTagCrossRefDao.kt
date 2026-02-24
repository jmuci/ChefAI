package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import java.util.UUID

class FakeRecipeTagCrossRefDao : RecipeTagCrossRefDao {

    val recipeTags = mutableListOf<RecipeTagCrossRef>()

    override suspend fun upsertCrossRef(crossRef: RecipeTagCrossRef) {
        recipeTags.add(crossRef)
    }

    override suspend fun markPendingForRecipes(recipeIds: List<UUID>, updatedAt: Long) {
        val updated = recipeTags.map { tag ->
            if (tag.recipeId in recipeIds) {
                tag.copy(syncState = SyncState.PENDING, updatedAt = updatedAt)
            } else {
                tag
            }
        }
        recipeTags.clear()
        recipeTags.addAll(updated)
    }
}
