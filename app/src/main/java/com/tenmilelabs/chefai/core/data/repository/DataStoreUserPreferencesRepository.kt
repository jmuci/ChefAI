package com.tenmilelabs.chefai.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tenmilelabs.chefai.core.domain.repository.UserPreferencesRepository
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A plain, unencrypted DataStore — a sibling of
 * [com.tenmilelabs.chefai.auth.data.local.SecurePreferences] rather than a change to it. That store
 * holds tokens and is Keystore-encrypted for it; nothing in here is worth a cipher.
 */
private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "chefai_user_prefs"
)

@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : UserPreferencesRepository {

    override val measurementSystem: Flow<MeasurementSystem> =
        context.userPreferencesDataStore.data
            .catch { throwable ->
                // A corrupt or unreadable preferences file must not take a recipe screen down with
                // it — the recipe reads perfectly well in the units it was written in.
                if (throwable !is IOException) throw throwable
                Timber.w(throwable, "Could not read user preferences; falling back to defaults")
                emit(androidx.datastore.preferences.core.emptyPreferences())
            }
            .map { MeasurementSystem.fromName(it[KEY_MEASUREMENT_SYSTEM]) }

    override suspend fun setMeasurementSystem(system: MeasurementSystem) {
        context.userPreferencesDataStore.edit { it[KEY_MEASUREMENT_SYSTEM] = system.name }
    }

    private companion object {
        /** Stored by [MeasurementSystem.name], so reordering the enum can never re-map a choice. */
        val KEY_MEASUREMENT_SYSTEM = stringPreferencesKey("measurement_system")
    }
}
