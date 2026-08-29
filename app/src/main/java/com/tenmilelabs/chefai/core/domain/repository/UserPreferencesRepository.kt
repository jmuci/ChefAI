package com.tenmilelabs.chefai.core.domain.repository

import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import kotlinx.coroutines.flow.Flow

/**
 * Device-local settings the user chooses for themselves.
 *
 * Deliberately not part of the sync payload, and so deliberately not keyed by user: like the
 * meal-plan cooked toggle and `shopping_list_checks`, this describes how *this device* presents
 * things rather than what the account owns. Keeping it out of the payload means no schema
 * migration and no backend agreement.
 */
interface UserPreferencesRepository {

    /** How ingredient amounts should be read back. Emits on every change. */
    val measurementSystem: Flow<MeasurementSystem>

    suspend fun setMeasurementSystem(system: MeasurementSystem)
}
