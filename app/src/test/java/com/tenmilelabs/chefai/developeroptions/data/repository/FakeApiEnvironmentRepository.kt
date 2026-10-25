package com.tenmilelabs.chefai.developeroptions.data.repository

import com.tenmilelabs.chefai.developeroptions.domain.model.ApiEnvironment
import com.tenmilelabs.chefai.developeroptions.domain.repository.ApiEnvironmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [ApiEnvironmentRepository], so a test can switch environment without a DataStore. */
class FakeApiEnvironmentRepository(
    initial: ApiEnvironment = ApiEnvironment.DEFAULT,
) : ApiEnvironmentRepository {

    private val _environment = MutableStateFlow(initial)

    override val environment: Flow<ApiEnvironment> = _environment

    override suspend fun setEnvironment(environment: ApiEnvironment) {
        _environment.value = environment
    }

    /** Reads the stored value directly, for asserting on what a ViewModel wrote. */
    val current: ApiEnvironment get() = _environment.value
}
