package com.tenmilelabs.chefai.data.source.local.room.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.tenmilelabs.chefai.data.source.local.room.AllergenEntity
import com.tenmilelabs.chefai.data.source.local.room.IngredientEntity
import com.tenmilelabs.chefai.data.source.local.room.SourceClassificationEntity

data class IngredientWithDetails(
    @Embedded val ingredient: IngredientEntity,

    @Relation(parentColumn = "allergenId", entityColumn = "uuid")
    val allergen: AllergenEntity?,

    @Relation(parentColumn = "sourcePrimaryId", entityColumn = "uuid")
    val sourcePrimary: SourceClassificationEntity?
)
