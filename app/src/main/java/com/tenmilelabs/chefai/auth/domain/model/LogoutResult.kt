package com.tenmilelabs.chefai.auth.domain.model

/**
 * What [com.tenmilelabs.chefai.auth.domain.usecase.LogoutUseCase] managed to upload before the
 * session ended. Logout itself always happens; this only says whether anything was left behind.
 *
 * Changes left behind stay on the device as `PENDING`/`DELETED` rows. Signing back in with the
 * same account uploads them on its next sync; a *different* account signing in on this device
 * deletes them (ADR-008, the account-switch preserve branch).
 */
sealed interface LogoutResult {

    /** Nothing was waiting to upload, or the pre-logout push sent all of it. */
    data object AllChangesUploaded : LogoutResult

    /** [count] rows were still waiting to upload when the session ended, because of [reason]. */
    data class ChangesNotUploaded(val count: Int, val reason: Reason) : LogoutResult

    /** Couldn't even tell whether anything was waiting to upload (the local query failed). */
    data object UploadCheckFailed : LogoutResult

    enum class Reason {
        /** No network, so no push was attempted. */
        OFFLINE,

        /** The push didn't finish within the logout budget. */
        TIMED_OUT,

        /** The push failed (HTTP or network error), or the server rejected some rows. */
        FAILED,
    }
}
