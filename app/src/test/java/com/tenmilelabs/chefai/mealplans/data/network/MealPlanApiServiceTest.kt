package com.tenmilelabs.chefai.mealplans.data.network

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.IOException

class MealPlanApiServiceTest {

    private lateinit var engine: MockEngine

    private fun service(engine: MockEngine): MealPlanApiService {
        this.engine = engine
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return MealPlanApiService(client)
    }

    private val lastRequest: HttpRequestData get() = engine.requestHistory.last()

    private fun okEngine(body: String) = MockEngine {
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
        )
    }

    private val successBody =
        """{"days":[{"uuid":"01957a7f-0000-7000-8000-000000000000","dayIndex":0,"dinnerRecipeId":null,"lunchRecipeId":null}],"recipes":[],"referenceData":{},"creators":[]}"""

    @Test
    fun `preferencesJson is sent as the request body`() = runTest {
        val subject = service(okEngine(successBody))

        subject.generateStateless("""{"planLengthDays":5}""")

        val body = String((lastRequest.body as OutgoingContent.ByteArrayContent).bytes())
        assertThat(body).contains("planLengthDays")
    }

    @Test
    fun `a 200 response maps to Success with the parsed body`() = runTest {
        val subject = service(okEngine(successBody))

        val result = subject.generateStateless("{}")

        assertThat(result).isInstanceOf(GenerateStatelessResult.Success::class.java)
        val response = (result as GenerateStatelessResult.Success).response
        assertThat(response.days).hasSize(1)
        assertThat(response.days.single().dinnerRecipeId).isNull()
    }

    @Test
    fun `a 400 maps to Error`() = runTest {
        val subject = service(MockEngine { respondError(HttpStatusCode.BadRequest) })

        val result = subject.generateStateless("not valid json")

        assertThat(result).isInstanceOf(GenerateStatelessResult.Error::class.java)
    }

    @Test
    fun `a 500 maps to Error`() = runTest {
        val subject = service(MockEngine { respondError(HttpStatusCode.InternalServerError) })

        val result = subject.generateStateless("{}")

        assertThat(result).isInstanceOf(GenerateStatelessResult.Error::class.java)
    }

    @Test
    fun `malformed JSON on a 200 maps to Error rather than throwing`() = runTest {
        val subject = service(okEngine("not json"))

        val result = subject.generateStateless("{}")

        assertThat(result).isInstanceOf(GenerateStatelessResult.Error::class.java)
    }

    @Test
    fun `a network failure — the same path a real request timeout takes — maps to Error, not a thrown exception`() =
        runTest {
            val subject = service(MockEngine { throw IOException("connection reset") })

            val result = subject.generateStateless("{}")

            assertThat(result).isInstanceOf(GenerateStatelessResult.Error::class.java)
        }
}
