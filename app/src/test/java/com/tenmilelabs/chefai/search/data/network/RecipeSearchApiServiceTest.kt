package com.tenmilelabs.chefai.search.data.network

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.IOException

class RecipeSearchApiServiceTest {

    private lateinit var engine: MockEngine

    private fun service(engine: MockEngine): RecipeSearchApiService {
        this.engine = engine
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return RecipeSearchApiService(client)
    }

    private val lastRequest: HttpRequestData get() = engine.requestHistory.last()

    private fun okEngine(body: String) = MockEngine {
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
        )
    }

    private val successBody = """{"query":"chic","results":[],"hasMore":false}"""

    @Test
    fun `query limit and offset are sent as request parameters`() = runTest {
        val subject = service(okEngine(successBody))

        subject.search("chic ken", limit = 10, offset = 5)

        assertThat(lastRequest.url.parameters["q"]).isEqualTo("chic ken")
        assertThat(lastRequest.url.parameters["limit"]).isEqualTo("10")
        assertThat(lastRequest.url.parameters["offset"]).isEqualTo("5")
    }

    @Test
    fun `a 200 response maps to Success with the parsed body`() = runTest {
        val subject = service(okEngine(successBody))

        val result = subject.search("chic", 20, 0)

        assertThat(result).isInstanceOf(RecipeSearchNetworkResult.Success::class.java)
        val response = (result as RecipeSearchNetworkResult.Success).response
        assertThat(response.query).isEqualTo("chic")
        assertThat(response.results).isEmpty()
        assertThat(response.hasMore).isFalse()
    }

    @Test
    fun `a 400 maps to Error, not Unauthorized`() = runTest {
        val subject = service(MockEngine { respondError(HttpStatusCode.BadRequest) })

        val result = subject.search("", 20, 0)

        assertThat(result).isInstanceOf(RecipeSearchNetworkResult.Error::class.java)
    }

    @Test
    fun `a 401 maps to Unauthorized specifically, not a generic Error`() = runTest {
        val subject = service(MockEngine { respondError(HttpStatusCode.Unauthorized) })

        val result = subject.search("chic", 20, 0)

        assertThat(result).isEqualTo(RecipeSearchNetworkResult.Unauthorized)
    }

    @Test
    fun `a 500 maps to Error`() = runTest {
        val subject = service(MockEngine { respondError(HttpStatusCode.InternalServerError) })

        val result = subject.search("chic", 20, 0)

        assertThat(result).isInstanceOf(RecipeSearchNetworkResult.Error::class.java)
    }

    @Test
    fun `malformed JSON on a 200 maps to Error rather than throwing`() = runTest {
        val subject = service(okEngine("not json"))

        val result = subject.search("chic", 20, 0)

        assertThat(result).isInstanceOf(RecipeSearchNetworkResult.Error::class.java)
    }

    @Test
    fun `a network failure — the same path a real request timeout takes — maps to Error, not a thrown exception`() =
        runTest {
            val subject = service(MockEngine { throw IOException("connection reset") })

            val result = subject.search("chic", 20, 0)

            assertThat(result).isInstanceOf(RecipeSearchNetworkResult.Error::class.java)
        }
}
