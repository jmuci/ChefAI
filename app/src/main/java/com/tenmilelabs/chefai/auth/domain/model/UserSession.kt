package com.tenmilelabs.chefai.auth.domain.model

import com.tenmilelabs.chefai.core.domain.model.User
import com.tenmilelabs.chefai.household.domain.model.HouseholdRole
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
        val authToken: AuthToken,
        /**
         * Present iff the user belongs to a household — see ADR-014. Folded in here rather than a
         * parallel `StateFlow` so every existing `when (session)` call site keeps compiling
         * untouched, and "no household yet" is representable as plain `null`. A user belongs to
         * **at most one** household, so there is no list/switcher to model.
         *
         * Sourced from the local household cache only — see `SessionManager.attachHouseholdState`
         * — so this can go briefly stale relative to the server (e.g. right after another device
         * removes the user from a household) until the next background refresh lands. That
         * staleness is only ever a display concern: every server-side mutation is re-authorized
         * against the server's own membership table regardless of what this field currently says.
         */
        val household: AuthenticatedHousehold? = null,
    ) : UserSession()
}

/** See [UserSession.Authenticated.household]. */
data class AuthenticatedHousehold(val householdId: UUID, val role: HouseholdRole)
