package com.tenmilelabs.chefai.developeroptions.domain.usecase

import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.developeroptions.domain.model.ApiEnvironment
import com.tenmilelabs.chefai.developeroptions.domain.repository.ApiEnvironmentRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Points the app at [ApiEnvironment] `target`, signing out first if an account is signed in.
 *
 * An account's tokens are issued by one backend and mean nothing to the other, so keeping them
 * across a switch would only produce 401s and a failed refresh. Signing out happens *before* the
 * switch so no request ever carries one environment's token to the other. Local data is kept, as
 * on any logout — anonymous-first means the device's recipes stay with the device.
 */
class SwitchApiEnvironment @Inject constructor(
    private val apiEnvironmentRepository: ApiEnvironmentRepository,
    private val sessionManager: SessionManager,
) {
    suspend operator fun invoke(target: ApiEnvironment) {
        if (apiEnvironmentRepository.environment.first() == target) return
        if (sessionManager.userSession.value is UserSession.Authenticated) {
            sessionManager.logout()
        }
        apiEnvironmentRepository.setEnvironment(target)
    }
}
