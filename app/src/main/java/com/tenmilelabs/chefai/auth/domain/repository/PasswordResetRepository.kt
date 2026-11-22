package com.tenmilelabs.chefai.auth.domain.repository

import com.tenmilelabs.chefai.auth.domain.model.PasswordResetResult

/**
 * Password reset by emailed link. Direct REST, no dirty queue, and unauthenticated: it must work
 * for a user who can't sign in.
 *
 * Neither the token nor the password is ever logged by an implementation.
 */
interface PasswordResetRepository {

    /**
     * Asks for a reset email. [PasswordResetResult.Success] for any well-formed [email]: the
     * server answers the same whether or not the account exists, so callers must not imply it does.
     */
    suspend fun requestReset(email: String): PasswordResetResult

    /** Checks that [token] is still usable, without spending it. */
    suspend fun validateToken(token: String): PasswordResetResult

    /** Sets [newPassword] and spends [token]. Doesn't sign anyone in. */
    suspend fun confirmReset(token: String, newPassword: String): PasswordResetResult
}
