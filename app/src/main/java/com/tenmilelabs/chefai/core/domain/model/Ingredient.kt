package com.tenmilelabs.chefai.core.domain.model

import java.util.UUID

/**
 * Domain model representing a single ingredient.
 *
 * This class is decoupled from the database and represents the ingredient in the UI and business logic.
 */
data class Ingredient(
    val uuid: UUID,
    val displayName: String,
    val allergen: Allergen?,
    val sourcePrimary: SourceClassification?
)
