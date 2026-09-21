package com.tenmilelabs.chefai.mealplans.domain.repository

import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanServingBasis
import kotlinx.coroutines.flow.Flow

/**
 * Device-local meal-plan settings — today, just whose week is being read.
 *
 * A sibling of [com.tenmilelabs.chefai.core.domain.repository.UserPreferencesRepository] rather
 * than a member of it: that interface lives in `core/` and may only speak in `core/` types, and
 * [MealPlanServingBasis] belongs to this feature (ADR-005). Both are backed by the same DataStore
 * file, which is why the store itself was lifted out into `core/data/local/prefs/`.
 *
 * Not part of the sync payload, for the same reason the cooked toggle and `shopping_list_checks`
 * are not: it describes how *this device* reads a plan, not what the account owns.
 */
interface MealPlanPreferencesRepository {

    /** Whose week the Meal Plans screen shows. Emits on every change. */
    val servingBasis: Flow<MealPlanServingBasis>

    suspend fun setServingBasis(basis: MealPlanServingBasis)
}
