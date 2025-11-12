package com.tenmilelabs.chefai.domain.model

import java.util.UUID

data class RecipeStep(
    val uuid: UUID,
    val orderIndex: Int,
    val instruction: String
)
