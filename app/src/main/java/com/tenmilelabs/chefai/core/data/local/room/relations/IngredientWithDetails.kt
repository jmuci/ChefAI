package com.tenmilelabs.chefai.core.data.local.room.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.tenmilelabs.chefai.core.data.local.room.AllergenEntity
import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.SourceClassificationEntity

data class IngredientWithDetails(
    @Embedded val ingredient: IngredientEntity,

    @Relation(parentColumn = "allergenId", entityColumn = "uuid")
    val allergen: AllergenEntity?,

    @Relation(parentColumn = "sourcePrimaryId", entityColumn = "uuid")
    val sourcePrimary: SourceClassificationEntity?
)
