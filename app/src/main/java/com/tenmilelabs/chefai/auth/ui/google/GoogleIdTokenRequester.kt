package com.tenmilelabs.chefai.auth.ui.google

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.tenmilelabs.chefai.BuildConfig
import kotlinx.coroutines.CancellationException

/** Outcome of asking the user for a Google ID token. Never contains anything loggable. */
sealed interface GoogleIdTokenResult {
    /** [idToken] is a Google-signed JWT; send it once to the backend and discard it. */
    data class Success(val idToken: String) : GoogleIdTokenResult

    /** The user dismissed the account chooser. Not an error. */
    data object Cancelled : GoogleIdTokenResult

    /** The device has no Google account to choose from. */
    data object NoGoogleAccount : GoogleIdTokenResult

    data class Failure(val cause: Throwable) : GoogleIdTokenResult
}

/**
 * Runs Credential Manager's explicit "Sign in with Google" flow, which always shows the account
 * chooser. Needs an Activity context at call time, so it lives in the UI layer and is not
 * injected; ViewModels only ever see the [GoogleIdTokenResult].
 */
class GoogleIdTokenRequester(
    private val serverClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID,
) {
    suspend fun request(activityContext: Context): GoogleIdTokenResult {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        return try {
            val response = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
            response.credential.toGoogleIdTokenResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toGoogleIdTokenResult()
        }
    }
}

@Composable
fun rememberGoogleIdTokenRequester(): GoogleIdTokenRequester = remember { GoogleIdTokenRequester() }

internal fun Credential.toGoogleIdTokenResult(): GoogleIdTokenResult {
    if (this !is CustomCredential || type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleIdTokenResult.Failure(IllegalStateException("Unexpected credential type: $type"))
    }
    return try {
        GoogleIdTokenResult.Success(GoogleIdTokenCredential.createFrom(data).idToken)
    } catch (e: GoogleIdTokenParsingException) {
        GoogleIdTokenResult.Failure(e)
    }
}

internal fun Throwable.toGoogleIdTokenResult(): GoogleIdTokenResult = when (this) {
    is GetCredentialCancellationException -> GoogleIdTokenResult.Cancelled
    is NoCredentialException -> GoogleIdTokenResult.NoGoogleAccount
    else -> GoogleIdTokenResult.Failure(this)
}
