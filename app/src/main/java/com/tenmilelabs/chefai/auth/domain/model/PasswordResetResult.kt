package com.tenmilelabs.chefai.auth.domain.model

/**
 * Outcome of one password-reset call. No exception crosses into the domain: transport and parse
 * failures are folded into [Network] and [Unknown] by the repository.
 */
sealed interface PasswordResetResult {
    /** `202` (request) or `204` (validate / confirm). */
    data object Success : PasswordResetResult

    /** `410 RESET_TOKEN_INVALID` — unknown, expired or already used; the server doesn't say which. */
    data object InvalidToken : PasswordResetResult

    /** `400` — [message] is user-presentable (e.g. the password rules). Blank if the body had none. */
    data class Validation(val message: String) : PasswordResetResult

    /** `429` from the `/auth` IP bucket. */
    data object RateLimited : PasswordResetResult

    /** Offline, timed out, or the backend is unreachable — worth retrying. */
    data object Network : PasswordResetResult

    /** Any other status, or a response that could not be read. */
    data object Unknown : PasswordResetResult
}
