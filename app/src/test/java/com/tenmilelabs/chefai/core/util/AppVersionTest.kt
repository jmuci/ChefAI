package com.tenmilelabs.chefai.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppVersionTest {

    @Test
    fun `release build shows the plain version name`() {
        assertThat(formatAppVersion("1.0", isDebug = false, commitHash = "")).isEqualTo("1.0")
    }

    @Test
    fun `debug build appends the commit hash before the debug suffix`() {
        assertThat(formatAppVersion("1.0", isDebug = true, commitHash = "a1b2c3d"))
            .isEqualTo("1.0-a1b2c3d-debug")
    }
}
