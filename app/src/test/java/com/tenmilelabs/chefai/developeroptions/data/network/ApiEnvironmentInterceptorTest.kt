package com.tenmilelabs.chefai.developeroptions.data.network

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.developeroptions.data.repository.FakeApiEnvironmentRepository
import com.tenmilelabs.chefai.developeroptions.domain.model.ApiBaseUrls
import com.tenmilelabs.chefai.developeroptions.domain.model.ApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ApiEnvironmentInterceptorTest {

    private val baseUrls = ApiBaseUrls(local = "http://10.0.2.2:8080", remote = "https://api.example.com")

    @Test
    fun `local leaves ChefAI URLs alone`() {
        val result = rewriteForEnvironment("http://10.0.2.2:8080/sync/pull?since=0", baseUrls, ApiEnvironment.LOCAL)

        assertThat(result).isNull()
    }

    @Test
    fun `remote swaps the base and keeps path and query`() {
        val result = rewriteForEnvironment("http://10.0.2.2:8080/sync/pull?since=0", baseUrls, ApiEnvironment.REMOTE)

        assertThat(result).isEqualTo("https://api.example.com/sync/pull?since=0")
    }

    @Test
    fun `remote falls back to local when the build has no remote host`() {
        val unconfigured = baseUrls.copy(remote = "https://api.invalid", remoteConfigured = false)

        val result = rewriteForEnvironment("http://10.0.2.2:8080/sync/pull", unconfigured, ApiEnvironment.REMOTE)

        assertThat(result).isNull()
    }

    @Test
    fun `remote never touches a host that is not the ChefAI backend`() {
        val result = rewriteForEnvironment(
            "https://jmuci.github.io/ChATestAPI/api/v1/recipes.json",
            baseUrls,
            ApiEnvironment.REMOTE,
        )

        assertThat(result).isNull()
    }

    @Test
    fun `remote requires a path boundary after the base`() {
        val result = rewriteForEnvironment("http://10.0.2.2:80801/sync/pull", baseUrls, ApiEnvironment.REMOTE)

        assertThat(result).isNull()
    }

    @Test
    fun `the plugin sends requests to whichever environment is chosen at request time`() = runTest {
        val repository = FakeApiEnvironmentRepository()
        val seen = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            seen += request.url.toString()
            respondOk()
        }) {
            install(ApiEnvironmentInterceptor) {
                this.repository = repository
                this.baseUrls = this@ApiEnvironmentInterceptorTest.baseUrls
            }
        }

        client.get("http://10.0.2.2:8080/auth/login")
        repository.setEnvironment(ApiEnvironment.REMOTE)
        client.get("http://10.0.2.2:8080/auth/login")

        assertThat(seen).containsExactly(
            "http://10.0.2.2:8080/auth/login",
            "https://api.example.com/auth/login",
        ).inOrder()
    }
}
