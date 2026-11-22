package com.tenmilelabs.chefai.auth.ui.google

import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.credentials.CustomCredential
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class GoogleIdTokenResultMappingTest {

    @Test
    fun `given a google id token credential, when mapped, then Success carries the token`() {
        val google = GoogleIdTokenCredential(
            id = "user@example.com",
            idToken = "e30.eyJzdWIiOiIxMjM0NTY3ODkwIiwiaXNzIjoiaHR0cHM6Ly9hY2NvdW50cy5nb29nbGUuY29tIiwiYXVkIjoieCIsImV4cCI6OTk5OTk5OTk5OSwiaWF0IjoxfQ.c2ln",
            displayName = null,
            familyName = null,
            givenName = null,
            profilePictureUri = null,
            phoneNumber = null,
        )
        val credential = CustomCredential(google.type, google.data)

        assertThat(credential.toGoogleIdTokenResult())
            .isEqualTo(GoogleIdTokenResult.Success("e30.eyJzdWIiOiIxMjM0NTY3ODkwIiwiaXNzIjoiaHR0cHM6Ly9hY2NvdW50cy5nb29nbGUuY29tIiwiYXVkIjoieCIsImV4cCI6OTk5OTk5OTk5OSwiaWF0IjoxfQ.c2ln"))
    }

    @Test
    fun `given a custom credential of another type, when mapped, then Failure`() {
        val credential = CustomCredential("something.else", Bundle())

        assertThat(credential.toGoogleIdTokenResult()).isInstanceOf(GoogleIdTokenResult.Failure::class.java)
    }

    @Test
    fun `given a google credential with a malformed bundle, when mapped, then Failure`() {
        val credential = CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, Bundle())

        assertThat(credential.toGoogleIdTokenResult()).isInstanceOf(GoogleIdTokenResult.Failure::class.java)
    }

    @Test
    fun `given a non-custom credential, when mapped, then Failure`() {
        assertThat(PasswordCredential("id", "pw").toGoogleIdTokenResult())
            .isInstanceOf(GoogleIdTokenResult.Failure::class.java)
    }

    @Test
    fun `given a cancellation exception, when mapped, then Cancelled`() {
        assertThat(GetCredentialCancellationException().toGoogleIdTokenResult())
            .isEqualTo(GoogleIdTokenResult.Cancelled)
    }

    @Test
    fun `given no credential exception, when mapped, then NoGoogleAccount`() {
        assertThat(NoCredentialException().toGoogleIdTokenResult())
            .isEqualTo(GoogleIdTokenResult.NoGoogleAccount)
    }

    @Test
    fun `given any other exception, when mapped, then Failure keeps the cause`() {
        val cause = GetCredentialUnknownException("boom")

        assertThat(cause.toGoogleIdTokenResult()).isEqualTo(GoogleIdTokenResult.Failure(cause))
    }
}
