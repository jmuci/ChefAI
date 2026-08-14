package com.tenmilelabs.chefai.recipes.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import java.util.UUID

private val IMAGE_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)

class FetchRecipeImageFromBackendTest {

    private val recipeId: UUID = UUID.randomUUID()
    private val store: RecipeImageStore = mockk(relaxed = true)

    /** Shares [runTest]'s scheduler — two test dispatchers with separate schedulers throw. */
    private fun TestScope.subject(engine: MockEngine) = FetchRecipeImageFromBackend(
        client = HttpClient(engine),
        recipeImageStore = store,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private fun imageEngine(contentType: String = "image/jpeg") = MockEngine {
        respond(
            content = IMAGE_BYTES.decodeToString(),
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", contentType),
        )
    }

    @Test
    fun `a served image is stored and its path returned`() = runTest {
        coEvery { store.write(recipeId, any()) } returns "/files/recipe_images/$recipeId"

        val path = subject(imageEngine())(recipeId)

        assertThat(path).isEqualTo("/files/recipe_images/$recipeId")
    }

    @Test
    fun `the request goes to our own host, never to a scraped url`() = runTest {
        // The only defence against handing a JWT to Food Network is that this URL is built from
        // BuildConfig and a recipe id, and can't be influenced by recipe data.
        val engine = imageEngine()

        subject(engine)(recipeId)

        val url = engine.requestHistory.single().url
        assertThat(url.encodedPath).isEqualTo("/recipes/$recipeId/image")
        assertThat(url.host).isNotEqualTo("food.fnr.sndimg.com")
    }

    @Test
    fun `a 404 yields null rather than throwing`() = runTest {
        // Normal: the recipe's image simply hasn't been uploaded by any device yet.
        val path = subject(MockEngine { respondError(HttpStatusCode.NotFound) })(recipeId)

        assertThat(path).isNull()
    }

    @Test
    fun `a 401 yields null rather than throwing`() = runTest {
        val path = subject(MockEngine { respondError(HttpStatusCode.Unauthorized) })(recipeId)

        assertThat(path).isNull()
    }

    @Test
    fun `a non-image response is refused and never written`() = runTest {
        // An HTML error page or a login redirect must not land on disk as a "recipe image".
        val path = subject(imageEngine(contentType = ContentType.Text.Html.toString()))(recipeId)

        assertThat(path).isNull()
        coVerify(exactly = 0) { store.write(any(), any()) }
    }

    @Test
    fun `a network failure yields null rather than throwing`() = runTest {
        val path = subject(MockEngine { throw IOException("connection reset") })(recipeId)

        assertThat(path).isNull()
    }

    @Test
    fun `a failed store write yields null`() = runTest {
        coEvery { store.write(recipeId, any()) } returns null

        val path = subject(imageEngine())(recipeId)

        assertThat(path).isNull()
    }
}
