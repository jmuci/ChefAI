package com.tenmilelabs.chefai.auth.ui.google

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Tells Credential Manager the user signed out, so a later auto-select flow cannot silently sign
 * the same Google account back in (ADR-019 Decision 4). Best effort: a failure only costs that
 * convenience guarantee, never the logout, so it is logged and swallowed. Needs a UI [context], so
 * `LogoutUseCase` stays Context-free and the profile menu calls this once logout has finished.
 */
suspend fun clearGoogleCredentialState(context: Context) {
    try {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ClearCredentialException) {
        Timber.w(e, "Could not clear Credential Manager state after logout")
    } catch (e: Exception) {
        Timber.w(e, "Could not clear Credential Manager state after logout")
    }
}
