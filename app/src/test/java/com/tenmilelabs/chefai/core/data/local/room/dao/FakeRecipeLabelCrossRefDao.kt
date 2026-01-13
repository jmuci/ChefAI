package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef

class FakeRecipeLabelCrossRefDao : RecipeLabelCrossRefDao {

    private val recipeLabels = mutableListOf<RecipeLabelCrossRef>()

    override suspend fun upsertCrossRef(crossRef: RecipeLabelCrossRef) {
        recipeLabels.add(crossRef)
    }
}
