package com.tenmilelabs.chefai.core.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket

/**
 * Exercises [NetworkModule.provideScraperHttpClient] against a real local server rather than a
 * [io.ktor.client.engine.mock.MockEngine] — [DefaultRecipeImporterTest]-style tests build their own
 * `HttpClient` around a mock engine, which never touches the real OkHttp engine this client is
 * configured with, so they can't catch a regression in engine-level behavior.
 *
 * The one behavior worth pinning here: `followRedirects = false` must actually stop the OkHttp
 * engine from following a 3xx *before* app code sees it, since
 * [com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter] relies on catching
 * [RedirectResponseException] to re-validate every redirect hop against the SSRF guard. If a future
 * Ktor/OkHttp upgrade — or an `engine { config { ... } }` block added for an unrelated reason —
 * silently re-enabled transport-level redirect-following, this would be the only place unit tests
 * would notice.
 *
 * Hand-rolled over `com.sun.net.httpserver.HttpServer`: that package isn't on the `android.jar`
 * bootclasspath local unit tests compile against.
 */
class ScraperHttpClientTest {

    private lateinit var server: ServerSocket
    private lateinit var serverThread: Thread
    private lateinit var client: HttpClient

    @Before
    fun startServer() {
        server = ServerSocket(0)
        serverThread = Thread {
            try {
                while (!server.isClosed) {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        socket.getOutputStream().write(
                            (
                                "HTTP/1.1 302 Found\r\n" +
                                    "Location: http://127.0.0.1/should-not-be-followed\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n\r\n"
                                ).toByteArray()
                        )
                    }
                }
            } catch (_: Exception) {
                // Expected once stopServer() closes the socket out from under the blocked accept().
            }
        }.apply { start() }
        client = NetworkModule.provideScraperHttpClient()
    }

    @After
    fun stopServer() {
        client.close()
        server.close()
        serverThread.join(1_000)
    }

    @Test
    fun `scraper client does not auto-follow redirects at the transport level`() {
        val port = server.localPort

        assertThrows(RedirectResponseException::class.java) {
            runBlocking { client.get("http://localhost:$port/redirect") }
        }
    }
}
