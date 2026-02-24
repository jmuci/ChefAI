package com.tenmilelabs.chefai.auth.domain.model

import com.tenmilelabs.chefai.core.domain.model.User
import java.util.UUID

/**
 * Represents the current user's session state.
 * The app always has a user context — there is no "unauthenticated" state.
 * Users start as Anonymous and can upgrade to Authenticated.
 */
sealed class UserSession {
    /**
     * Session state is being loaded/verified.
     */
    data object Loading : UserSession()

    /**
     * Local-only anonymous session with a client-generated UUID.
     * No backend account exists. Data lives exclusively in Room.
     */
    data class Anonymous(val localUserId: UUID) : UserSession()

    /**
     * User is authenticated with valid tokens.
     */
    data class Authenticated(
        val user: User,
        val authToken: AuthToken
    ) : UserSession()
}
