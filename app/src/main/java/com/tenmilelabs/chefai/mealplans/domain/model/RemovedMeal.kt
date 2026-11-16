package com.tenmilelabs.chefai.mealplans.domain.model

import java.util.UUID

/**
 * A meal just taken out of its slot, kept so an Undo can put it back exactly — cooked mark
 * included. Removing a meal leaves the durable cook history (ADR-017) alone: the cooking happened.
 */
data class RemovedMeal(
    val planId: UUID,
    val dayId: UUID,
    val slot: MealSlot,
    val recipeId: UUID,
    val cookedAt: Long?,
)
