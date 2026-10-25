package com.tenmilelabs.chefai.developeroptions.domain.repository

import com.tenmilelabs.chefai.developeroptions.domain.model.ApiEnvironment
import kotlinx.coroutines.flow.Flow

/** The debug-only backend choice made on the Developer options screen. Device-local, never synced. */
interface ApiEnvironmentRepository {

    /** The chosen environment. Emits on every change. */
    val environment: Flow<ApiEnvironment>

    suspend fun setEnvironment(environment: ApiEnvironment)
}
