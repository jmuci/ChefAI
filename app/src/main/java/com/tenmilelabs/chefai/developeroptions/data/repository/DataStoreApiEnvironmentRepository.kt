package com.tenmilelabs.chefai.developeroptions.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tenmilelabs.chefai.core.data.local.prefs.userPreferencesDataStore
import com.tenmilelabs.chefai.developeroptions.domain.model.ApiEnvironment
import com.tenmilelabs.chefai.developeroptions.domain.repository.ApiEnvironmentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreApiEnvironmentRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ApiEnvironmentRepository {

    override val environment: Flow<ApiEnvironment> =
        context.userPreferencesDataStore.data
            .catch { throwable ->
                if (throwable !is IOException) throw throwable
                Timber.w(throwable, "Could not read the API environment; falling back to the default")
                emit(emptyPreferences())
            }
            .map { ApiEnvironment.fromName(it[KEY_API_ENVIRONMENT]) }

    override suspend fun setEnvironment(environment: ApiEnvironment) {
        context.userPreferencesDataStore.edit { it[KEY_API_ENVIRONMENT] = environment.name }
    }

    private companion object {
        val KEY_API_ENVIRONMENT = stringPreferencesKey("debug_api_environment")
    }
}
