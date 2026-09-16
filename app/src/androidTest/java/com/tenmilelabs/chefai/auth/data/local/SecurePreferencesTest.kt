package com.tenmilelabs.chefai.auth.data.local

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented — [SecurePreferences] is Keystore/DataStore-backed and needs a real Android
 * runtime. Scoped to the pending-invite-token round trip (ADR-014 §7); every other method already
 * has fake coverage via [FakeSecurePreferences] throughout the rest of the suite.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class SecurePreferencesTest {

    private lateinit var securePreferences: SecurePreferences

    @Before
    fun setUp() {
        securePreferences = SecurePreferences(getApplicationContext())
    }

    @After
    fun tearDown() = runTest {
        securePreferences.clearPendingInviteToken()
    }

    @Test
    fun getPendingInviteToken_isNullBeforeAnyTokenIsSaved() = runTest {
        assertNull(securePreferences.getPendingInviteToken().first())
    }

    @Test
    fun savePendingInviteToken_roundTripsThroughEncryption() = runTest {
        securePreferences.savePendingInviteToken("invite-token-abc")

        assertEquals("invite-token-abc", securePreferences.getPendingInviteToken().first())
    }

    @Test
    fun savePendingInviteToken_survivesReadingFromAFreshInstance() = runTest {
        securePreferences.savePendingInviteToken("invite-token-abc")

        // A new instance against the same Context/DataStore file — proves persistence rather than
        // in-memory state, the scenario a killed-and-restarted sign-up process relies on.
        val freshInstance = SecurePreferences(getApplicationContext())

        assertEquals("invite-token-abc", freshInstance.getPendingInviteToken().first())
    }

    @Test
    fun clearPendingInviteToken_removesIt() = runTest {
        securePreferences.savePendingInviteToken("invite-token-abc")

        securePreferences.clearPendingInviteToken()

        assertNull(securePreferences.getPendingInviteToken().first())
    }
}
