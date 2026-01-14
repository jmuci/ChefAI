package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef

class FakeRecipeTagCrossRefDao : RecipeTagCrossRefDao {

    private val recipeTags = mutableListOf<RecipeTagCrossRef>()


    override suspend fun upsertCrossRef(crossRef: RecipeTagCrossRef) {
        recipeTags.add(crossRef)
    }
}
