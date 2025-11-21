package com.tenmilelabs.chefai.auth.domain.model

import com.tenmilelabs.chefai.domain.model.User

/**
 * Represents the current user's session state.
 */
sealed class UserSession {
    /**
     * User is authenticated with valid tokens.
     */
    data class Authenticated(
        val user: User,
        val authToken: AuthToken
    ) : UserSession()

    /**
     * No authenticated user session.
     */
    data object Unauthenticated : UserSession()

    /**
     * Session state is being loaded/verified.
     */
    data object Loading : UserSession()
}
