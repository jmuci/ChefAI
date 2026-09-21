package com.tenmilelabs.chefai.mealplans.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tenmilelabs.chefai.core.data.local.prefs.userPreferencesDataStore
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanServingBasis
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreMealPlanPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : MealPlanPreferencesRepository {

    override val servingBasis: Flow<MealPlanServingBasis> =
        context.userPreferencesDataStore.data
            .catch { throwable ->
                // An unreadable preferences file must not take the week view down with it — the
                // week reads perfectly well on the default basis.
                if (throwable !is IOException) throw throwable
                Timber.w(throwable, "Could not read meal plan preferences; using the default basis")
                emit(emptyPreferences())
            }
            .map { MealPlanServingBasis.fromName(it[KEY_SERVING_BASIS]) }

    override suspend fun setServingBasis(basis: MealPlanServingBasis) {
        context.userPreferencesDataStore.edit { it[KEY_SERVING_BASIS] = basis.name }
    }

    private companion object {
        /** Stored by name, so reordering the enum can never re-map a choice. */
        val KEY_SERVING_BASIS = stringPreferencesKey("meal_plan_serving_basis")
    }
}
