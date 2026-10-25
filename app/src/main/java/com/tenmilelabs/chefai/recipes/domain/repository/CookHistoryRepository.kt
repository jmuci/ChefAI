package com.tenmilelabs.chefai.recipes.domain.repository

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Read side of the on-device record of which recipes the user has cooked. */
interface CookHistoryRepository {

    /**
     * Ids of the recipes cooked at least once, re-emitted whenever a meal is marked or unmarked.
     * Survives the deletion of the meal plan a recipe was cooked in.
     */
    fun observeCookedRecipeIds(): Flow<Set<UUID>>
}
