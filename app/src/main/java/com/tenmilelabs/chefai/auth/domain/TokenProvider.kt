package com.tenmilelabs.chefai.auth.domain

/**
 * Minimal interface for providing access tokens.
 *
 * This interface exists to break circular dependencies between the HTTP client
 * and session management. By depending only on this interface, components that
 * need to add auth headers to HTTP requests don't need a full session manager.
 *
 * @see SessionManager implements this interface
 */
interface TokenProvider {
    /**
     * Returns the current access token if the user is authenticated.
     * Returns null if no valid token is available.
     */
    fun getAccessToken(): String?
}