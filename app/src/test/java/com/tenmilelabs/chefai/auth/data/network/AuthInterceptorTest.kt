package com.tenmilelabs.chefai.auth.data.network

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.domain.TokenProvider
import com.tenmilelabs.chefai.developeroptions.data.network.ApiEnvironmentInterceptor
import com.tenmilelabs.chefai.developeroptions.data.repository.FakeApiEnvironmentRepository
import com.tenmilelabs.chefai.developeroptions.domain.model.ApiBaseUrls
import com.tenmilelabs.chefai.developeroptions.domain.model.ApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AuthInterceptorTest {

    private val localBase = "http://10.0.2.2:8080"
    private val remoteBase = "https://api.pocket-chef.app"

    /** Authorization header seen by the engine for each request, keyed by the URL it went to. */
    private val sentAuth = linkedMapOf<String, String?>()

    private fun client(
        token: String? = "jwt-123",
        allowedBaseUrls: Collection<String> = listOf(localBase, remoteBase),
        configure: HttpClientConfig<*>.() -> Unit = {},
    ) = HttpClient(MockEngine { request ->
        sentAuth[request.url.toString()] = request.headers[HttpHeaders.Authorization]
        respondOk()
    }) {
        configure()
        install(AuthInterceptor) {
            tokenProvider = object : TokenProvider {
                override fun getAccessToken(): String? = token
            }
            this.allowedBaseUrls = allowedBaseUrls
        }
    }

    @Test
    fun `attaches the token to the local API host`() = runTest {
        // Given / When
        client().get("$localBase/api/v1/me")

        // Then
        assertThat(sentAuth.values.single()).isEqualTo("Bearer jwt-123")
    }

    @Test
    fun `attaches the token to the deployed API host`() = runTest {
        client().get("$remoteBase/sync/pull?since=0")

        assertThat(sentAuth.values.single()).isEqualTo("Bearer jwt-123")
    }

    @Test
    fun `never attaches the token to a third-party host`() = runTest {
        val client = client()

        client.get("https://jmuci.github.io/ChATestAPI/api/v2/recipes.json")
        client.get("https://cdn.example.com/image.jpg")

        assertThat(sentAuth.values).containsExactly(null, null)
    }

    @Test
    fun `a lookalike host, port or scheme does not count as the API host`() = runTest {
        val client = client()

        client.get("https://api.pocket-chef.app.evil.example/sync/pull")
        client.get("https://evil.example/?next=api.pocket-chef.app")
        client.get("http://10.0.2.2:8081/api/v1/me")
        client.get("http://api.pocket-chef.app/sync/pull")

        assertThat(sentAuth.values).containsExactly(null, null, null, null)
    }

    @Test
    fun `no header when there is no token, even for the API host`() = runTest {
        client(token = null).get("$remoteBase/api/v1/me")

        assertThat(sentAuth.values.single()).isNull()
    }

    @Test
    fun `no allowed base URLs means no host gets the token`() = runTest {
        client(allowedBaseUrls = emptyList()).get("$remoteBase/api/v1/me")

        assertThat(sentAuth.values.single()).isNull()
    }

    @Test
    fun `token follows a request the environment interceptor rewrote onto the deployed host`() = runTest {
        // Given — a debug build switched to REMOTE in Developer options, wired as NetworkModule does
        val repository = FakeApiEnvironmentRepository(ApiEnvironment.REMOTE)
        val client = client {
            install(ApiEnvironmentInterceptor) {
                this.repository = repository
                baseUrls = ApiBaseUrls(local = localBase, remote = remoteBase)
            }
        }

        // When
        client.get("$localBase/api/v1/me")

        // Then
        assertThat(sentAuth).containsExactly("$remoteBase/api/v1/me", "Bearer jwt-123")
    }

    @Test
    fun `isAuthenticatedOrigin rejects unparseable and relative URLs`() {
        val allowed = setOfNotNull(remoteBase.toOrigin())

        assertThat(isAuthenticatedOrigin("not a url", allowed)).isFalse()
        assertThat(isAuthenticatedOrigin("/api/v1/me", allowed)).isFalse()
        assertThat(isAuthenticatedOrigin("$remoteBase/api/v1/me", allowed)).isTrue()
    }
}
