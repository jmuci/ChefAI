package com.tenmilelabs.chefai.mealplans.data.repository

import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanServingBasis
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [MealPlanPreferencesRepository], so a test can flip the basis without a DataStore. */
class FakeMealPlanPreferencesRepository(
    initial: MealPlanServingBasis = MealPlanServingBasis.DEFAULT,
) : MealPlanPreferencesRepository {

    private val _servingBasis = MutableStateFlow(initial)

    override val servingBasis: Flow<MealPlanServingBasis> = _servingBasis

    override suspend fun setServingBasis(basis: MealPlanServingBasis) {
        _servingBasis.value = basis
    }

    /** Reads the stored value directly, for asserting on what a ViewModel wrote. */
    val current: MealPlanServingBasis get() = _servingBasis.value
}
