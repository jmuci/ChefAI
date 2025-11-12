package com.tenmilelabs.chefai.data.source.local.room.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.tenmilelabs.chefai.data.source.local.room.RecipeEntity
import com.tenmilelabs.chefai.data.source.local.room.UserEntity

data class UserWithRecipes(
    @Embedded val user: UserEntity,
    @Relation(
        parentColumn = "uuid",
        entityColumn = "creatorId"
    )
    val recipes: List<RecipeEntity>
)
