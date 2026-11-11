package com.tenmilelabs.chefai.core.data.sync.network

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.http.Headers
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ClientVersionHeadersTest {

    private val localBase = "http://10.0.2.2:8080"
    private val remoteBase = "https://api.pocket-chef.app"

    /** Headers seen by the engine for each request, in order. */
    private val sent = mutableListOf<Headers>()

    private fun client() = HttpClient(MockEngine { request ->
        sent += request.headers
        respondOk()
    }) {
        install(ClientVersionHeaders) {
            allowedBaseUrls = listOf(localBase, remoteBase)
            clientVersionCode = 42
        }
    }

    @Test
    fun `names the sync protocol and app version on requests to the ChefAI backend`() = runTest {
        val client = client()

        client.get("$localBase/sync/pull?since=0")
        client.get("$remoteBase/api/v1/me")

        assertThat(sent.map { it[HEADER_SYNC_PROTOCOL] }).containsExactly("1", "1")
        assertThat(sent.map { it[HEADER_CLIENT_VERSION] }).containsExactly("42", "42")
    }

    @Test
    fun `never sends them to a third-party host`() = runTest {
        val client = client()

        client.get("https://cdn.example.com/image.jpg")
        client.get("https://api.pocket-chef.app.evil.example/sync/pull")

        assertThat(sent.map { it[HEADER_SYNC_PROTOCOL] }).containsExactly(null, null)
        assertThat(sent.map { it[HEADER_CLIENT_VERSION] }).containsExactly(null, null)
    }
}
