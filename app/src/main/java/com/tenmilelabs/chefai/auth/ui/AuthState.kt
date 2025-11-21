package com.tenmilelabs.chefai.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel that exposes the current user session state to Composables.
 * This allows any screen to observe the authentication state.
 */
@HiltViewModel
class AuthStateViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {
    val userSession = sessionManager.userSession
}

/**
 * Composable helper to easily access the current user session state.
 * Usage:
 * ```
 * val userSession by rememberUserSession()
 * when (userSession) {
 *     is UserSession.Authenticated -> { /* Show authenticated content */ }
 *     is UserSession.Unauthenticated -> { /* Show login screen */ }
 *     is UserSession.Loading -> { /* Show loading indicator */ }
 * }
 * ```
 */
@Composable
fun rememberUserSession(
    viewModel: AuthStateViewModel = hiltViewModel()
): State<UserSession> {
    return viewModel.userSession.collectAsState()
}
