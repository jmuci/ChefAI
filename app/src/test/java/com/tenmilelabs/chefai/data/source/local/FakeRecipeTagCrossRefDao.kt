package com.tenmilelabs.chefai.data.source.local

import com.tenmilelabs.chefai.data.source.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.data.source.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.data.source.local.room.dao.RecipeTagCrossRefDao
import java.util.UUID

class FakeRecipeTagCrossRefDao : RecipeTagCrossRefDao {

    private val recipeTags = mutableListOf<RecipeTagCrossRef>()


    override suspend fun upsertCrossRef(crossRef: RecipeTagCrossRef) {
        recipeTags.add(crossRef)
    }
}
