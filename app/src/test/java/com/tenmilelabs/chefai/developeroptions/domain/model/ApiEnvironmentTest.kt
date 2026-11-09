package com.tenmilelabs.chefai.developeroptions.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ApiEnvironmentTest {

    @Test
    fun `a debug build with nothing stored talks to the local backend — issue 341`() {
        // Given no stored choice (fresh install, pm clear) or one this build can't read
        val unset = ApiEnvironment.fromName(null)
        val unknown = ApiEnvironment.fromName("STAGING")

        // Then the default is LOCAL, never the deployed backend
        assertThat(ApiEnvironment.DEFAULT).isEqualTo(ApiEnvironment.LOCAL)
        assertThat(unset).isEqualTo(ApiEnvironment.LOCAL)
        assertThat(unknown).isEqualTo(ApiEnvironment.LOCAL)
    }

    @Test
    fun `an explicit remote choice is still honoured`() {
        assertThat(ApiEnvironment.fromName("REMOTE")).isEqualTo(ApiEnvironment.REMOTE)
        assertThat(ApiEnvironment.fromName("LOCAL")).isEqualTo(ApiEnvironment.LOCAL)
    }

    @Test
    fun `the default resolves to the local base URL`() {
        val baseUrls = ApiBaseUrls(local = "http://10.0.2.2:8080", remote = "https://api.example.com")

        assertThat(baseUrls.urlFor(ApiEnvironment.DEFAULT)).isEqualTo("http://10.0.2.2:8080")
    }
}
