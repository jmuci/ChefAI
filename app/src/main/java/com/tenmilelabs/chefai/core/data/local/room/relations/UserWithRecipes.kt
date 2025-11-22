package com.tenmilelabs.chefai.core.data.local.room.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity

data class UserWithRecipes(
    @Embedded val user: UserEntity,
    @Relation(
        parentColumn = "uuid",
        entityColumn = "creatorId"
    )
    val recipes: List<RecipeEntity>
)
