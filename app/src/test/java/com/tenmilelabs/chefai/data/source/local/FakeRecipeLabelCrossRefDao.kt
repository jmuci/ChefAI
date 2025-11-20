package com.tenmilelabs.chefai.data.source.local

import com.tenmilelabs.chefai.data.source.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.data.source.local.room.dao.RecipeLabelCrossRefDao

class FakeRecipeLabelCrossRefDao : RecipeLabelCrossRefDao {

    private val recipeLabels = mutableListOf<RecipeLabelCrossRef>()

    override suspend fun upsertCrossRef(crossRef: RecipeLabelCrossRef) {
        recipeLabels.add(crossRef)
    }
}
