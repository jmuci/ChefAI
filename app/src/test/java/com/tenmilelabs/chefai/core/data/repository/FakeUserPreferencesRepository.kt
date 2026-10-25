package com.tenmilelabs.chefai.core.data.repository

import com.tenmilelabs.chefai.core.domain.model.RecipeLanguage
import com.tenmilelabs.chefai.core.domain.model.WeekStart
import com.tenmilelabs.chefai.core.domain.repository.UserPreferencesRepository
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [UserPreferencesRepository], so a test can flip a setting without a DataStore. */
class FakeUserPreferencesRepository(
    initial: MeasurementSystem = MeasurementSystem.DEFAULT,
    initialFallbackLanguage: RecipeLanguage = RecipeLanguage.DEFAULT,
    initialWeekStart: WeekStart = WeekStart.forLocale(),
) : UserPreferencesRepository {

    private val _measurementSystem = MutableStateFlow(initial)

    override val measurementSystem: Flow<MeasurementSystem> = _measurementSystem

    override suspend fun setMeasurementSystem(system: MeasurementSystem) {
        _measurementSystem.value = system
    }

    private val _weekStart = MutableStateFlow(initialWeekStart)

    override val weekStart: Flow<WeekStart> = _weekStart

    override suspend fun setWeekStart(weekStart: WeekStart) {
        _weekStart.value = weekStart
    }

    val currentWeekStart: WeekStart get() = _weekStart.value

    /** Reads the stored value directly, for asserting on what a ViewModel wrote. */
    val current: MeasurementSystem get() = _measurementSystem.value

    private val _fallbackRecipeLanguage = MutableStateFlow(initialFallbackLanguage)

    override val fallbackRecipeLanguage: Flow<RecipeLanguage> = _fallbackRecipeLanguage

    override suspend fun setFallbackRecipeLanguage(language: RecipeLanguage) {
        _fallbackRecipeLanguage.value = language
    }

    /** Reads the stored fallback language directly, for asserting on what a ViewModel wrote. */
    val currentFallbackLanguage: RecipeLanguage get() = _fallbackRecipeLanguage.value
}
