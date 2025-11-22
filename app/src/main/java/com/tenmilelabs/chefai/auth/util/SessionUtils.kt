package com.tenmilelabs.chefai.auth.util

import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.domain.model.User
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import kotlinx.coroutines.flow.map

/**
 * Extension function to get the current authenticated user from session.
 * Returns null if user is not authenticated.
 */
fun SessionManager.getCurrentUserOrNull(): User? {
    return when (val session = userSession.value) {
        is UserSession.Authenticated -> session.user
        else -> null
    }
}

/**
 * Extension function to check if user is currently authenticated.
 */
fun SessionManager.isAuthenticated(): Boolean {
    return userSession.value is UserSession.Authenticated
}

/**
 * Extension to get a Flow of the current user that emits null when unauthenticated.
 */
fun SessionManager.observeCurrentUser(): kotlinx.coroutines.flow.Flow<User?> {
    return userSession.map { session ->
        when (session) {
            is UserSession.Authenticated -> session.user
            else -> null
        }
    }
}
