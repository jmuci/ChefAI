package com.tenmilelabs.chefai.developeroptions.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import com.tenmilelabs.chefai.developeroptions.domain.model.ApiBaseUrls
import com.tenmilelabs.chefai.developeroptions.domain.model.ApiEnvironment
import com.tenmilelabs.chefai.developeroptions.domain.repository.ApiEnvironmentRepository
import com.tenmilelabs.chefai.developeroptions.domain.usecase.SwitchApiEnvironment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the Developer options screen renders. */
data class DeveloperOptionsUiState(
    val environment: ApiEnvironment = ApiEnvironment.DEFAULT,
    val localBaseUrl: String = "",
    val remoteBaseUrl: String = "",
) {
    val isRemote: Boolean get() = environment == ApiEnvironment.REMOTE
    val activeBaseUrl: String get() = if (isRemote) remoteBaseUrl else localBaseUrl
}

/** Everything the user can do on the Developer options screen. */
sealed interface DeveloperOptionsAction {
    data class UseRemoteEnvironmentChanged(val useRemote: Boolean) : DeveloperOptionsAction
}

@HiltViewModel
class DeveloperOptionsViewModel @Inject constructor(
    apiEnvironmentRepository: ApiEnvironmentRepository,
    private val switchApiEnvironment: SwitchApiEnvironment,
    private val apiBaseUrls: ApiBaseUrls,
) : ViewModel() {

    val uiState: StateFlow<DeveloperOptionsUiState> = apiEnvironmentRepository.environment
        .map { stateFor(it) }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = stateFor(ApiEnvironment.DEFAULT),
        )

    fun onAction(action: DeveloperOptionsAction) {
        when (action) {
            is DeveloperOptionsAction.UseRemoteEnvironmentChanged -> viewModelScope.launch {
                switchApiEnvironment(
                    if (action.useRemote) ApiEnvironment.REMOTE else ApiEnvironment.LOCAL,
                )
            }
        }
    }

    private fun stateFor(environment: ApiEnvironment) = DeveloperOptionsUiState(
        environment = environment,
        localBaseUrl = apiBaseUrls.local,
        remoteBaseUrl = apiBaseUrls.remote,
    )
}
