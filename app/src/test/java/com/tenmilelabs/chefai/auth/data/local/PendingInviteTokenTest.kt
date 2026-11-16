package com.tenmilelabs.chefai.auth.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PendingInviteTokenTest {

    private val savedAt = 1_000_000L

    @Test
    fun `a token is returned while it is fresh`() {
        assertThat(freshPendingInviteToken("abc", savedAt, savedAt + PENDING_INVITE_TTL_MS)).isEqualTo("abc")
    }

    @Test
    fun `a token older than the lifetime is dropped`() {
        assertThat(freshPendingInviteToken("abc", savedAt, savedAt + PENDING_INVITE_TTL_MS + 1)).isNull()
    }

    @Test
    fun `a token with no timestamp, from a build that didn't record one, is dropped`() {
        assertThat(freshPendingInviteToken("abc", savedAt = null, now = savedAt)).isNull()
    }

    @Test
    fun `a timestamp in the future, from a clock change, is dropped`() {
        assertThat(freshPendingInviteToken("abc", savedAt, now = savedAt - 1)).isNull()
    }
}
