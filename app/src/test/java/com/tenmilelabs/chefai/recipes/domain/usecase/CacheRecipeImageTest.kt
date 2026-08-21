package com.tenmilelabs.chefai.recipes.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore
import com.tenmilelabs.chefai.recipes.data.network.MAX_IMAGE_BYTES
import com.tenmilelabs.chefai.recipes.data.repository.FakeHostResolver
import com.tenmilelabs.chefai.recipes.data.repository.FakeRenderedImageFetcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

private val IMAGE_BYTES = byteArrayOf(1, 2, 3, 4)
private const val STORED_PATH = "/files/recipe_images/stored"

class CacheRecipeImageTest {

    private fun useCase(
        engine: MockEngine,
        renderedImageFetcher: FakeRenderedImageFetcher = FakeRenderedImageFetcher(),
        recipeImageStore: RecipeImageStore = mockk { coEvery { write(any(), any()) } returns STORED_PATH },
        hostResolver: FakeHostResolver = FakeHostResolver(),
    ) = CacheRecipeImage(
        httpClient = HttpClient(engine) { expectSuccess = true; followRedirects = false },
        renderedImageFetcher = renderedImageFetcher,
        recipeImageStore = recipeImageStore,
        hostResolver = hostResolver,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun imageEngine(bytes: ByteArray, contentType: String = "image/jpeg") = MockEngine { _ ->
        respond(
            content = ByteReadChannel(bytes),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, contentType),
        )
    }

    @Test
    fun `caches bytes from a plain HTTP fetch without escalating`() = runTest {
        val fetcher = FakeRenderedImageFetcher()
        val store = mockk<RecipeImageStore> { coEvery { write(any(), any()) } returns STORED_PATH }
        val recipeId = UUID.randomUUID()

        val result = useCase(imageEngine(IMAGE_BYTES), fetcher, store)
            .invoke(recipeId, "https://example.com/img.jpg")

        assertThat(result).isEqualTo(STORED_PATH)
        assertThat(fetcher.callCount).isEqualTo(0)
        coVerify(exactly = 1) { store.write(recipeId, IMAGE_BYTES) }
    }

    @Test
    fun `escalates to the rendered fetcher on a 403 and caches its bytes`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.Forbidden) }
        val fetcher = FakeRenderedImageFetcher(IMAGE_BYTES)
        val store = mockk<RecipeImageStore> { coEvery { write(any(), any()) } returns STORED_PATH }
        val recipeId = UUID.randomUUID()

        val result = useCase(engine, fetcher, store)
            .invoke(recipeId, "https://blocked.example.com/img.jpg")

        assertThat(result).isEqualTo(STORED_PATH)
        assertThat(fetcher.callCount).isEqualTo(1)
        assertThat(fetcher.lastUrl).isEqualTo("https://blocked.example.com/img.jpg")
        coVerify(exactly = 1) { store.write(recipeId, IMAGE_BYTES) }
    }

    @Test
    fun `returns null when the rendered fetcher also fails to produce bytes`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.Forbidden) }
        val fetcher = FakeRenderedImageFetcher(bytes = null)
        val store = mockk<RecipeImageStore>(relaxed = true)

        val result = useCase(engine, fetcher, store)
            .invoke(UUID.randomUUID(), "https://blocked.example.com/img.jpg")

        assertThat(result).isNull()
        coVerify(exactly = 0) { store.write(any(), any()) }
    }

    @Test
    fun `does not escalate on a 404 — a browser would not find the image either`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
        val fetcher = FakeRenderedImageFetcher(IMAGE_BYTES)

        val result = useCase(engine, fetcher).invoke(UUID.randomUUID(), "https://example.com/missing.jpg")

        assertThat(result).isNull()
        assertThat(fetcher.callCount).isEqualTo(0)
    }

    @Test
    fun `rejects a non-image content type without escalating`() = runTest {
        val engine = imageEngine(IMAGE_BYTES, contentType = "text/html")
        val fetcher = FakeRenderedImageFetcher(IMAGE_BYTES)

        val result = useCase(engine, fetcher).invoke(UUID.randomUUID(), "https://example.com/img.jpg")

        assertThat(result).isNull()
        assertThat(fetcher.callCount).isEqualTo(0)
    }

    @Test
    fun `a blank image url is a no-op`() = runTest {
        val fetcher = FakeRenderedImageFetcher(IMAGE_BYTES)
        val store = mockk<RecipeImageStore>(relaxed = true)

        val result = useCase(imageEngine(IMAGE_BYTES), fetcher, store).invoke(UUID.randomUUID(), "")

        assertThat(result).isNull()
        assertThat(fetcher.callCount).isEqualTo(0)
        coVerify(exactly = 0) { store.write(any(), any()) }
    }

    @Test
    fun `a private-network image url is rejected without any network call`() = runTest {
        val fetcher = FakeRenderedImageFetcher(IMAGE_BYTES)
        val store = mockk<RecipeImageStore>(relaxed = true)
        // The engine would throw if reached — its handler is never installed.
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val hostResolver = FakeHostResolver(mapOf("169.254.169.254" to FakeHostResolver.literal("169.254.169.254")))

        val result = useCase(engine, fetcher, store, hostResolver)
            .invoke(UUID.randomUUID(), "http://169.254.169.254/latest/meta-data/")

        assertThat(result).isNull()
        assertThat(fetcher.callCount).isEqualTo(0)
        coVerify(exactly = 0) { store.write(any(), any()) }
    }

    @Test
    fun `an image url whose DNS record points at a blocked address is rejected`() = runTest {
        val fetcher = FakeRenderedImageFetcher(IMAGE_BYTES)
        val store = mockk<RecipeImageStore>(relaxed = true)
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val hostResolver = FakeHostResolver(mapOf("evil.example.com" to FakeHostResolver.literal("169.254.169.254")))

        val result = useCase(engine, fetcher, store, hostResolver)
            .invoke(UUID.randomUUID(), "https://evil.example.com/img.jpg")

        assertThat(result).isNull()
        assertThat(fetcher.callCount).isEqualTo(0)
        coVerify(exactly = 0) { store.write(any(), any()) }
    }

    @Test
    fun `follows a redirect to a safe address and caches the bytes`() = runTest {
        val store = mockk<RecipeImageStore> { coEvery { write(any(), any()) } returns STORED_PATH }
        val recipeId = UUID.randomUUID()
        val engine = MockEngine { request ->
            if (request.url.toString() == "https://example.com/original.jpg") {
                respond(
                    content = ByteReadChannel(ByteArray(0)),
                    status = HttpStatusCode.MovedPermanently,
                    headers = headersOf(HttpHeaders.Location, "https://example.com/final.jpg"),
                )
            } else {
                respond(
                    content = ByteReadChannel(IMAGE_BYTES),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "image/jpeg"),
                )
            }
        }

        val result = useCase(engine, recipeImageStore = store).invoke(recipeId, "https://example.com/original.jpg")

        assertThat(result).isEqualTo(STORED_PATH)
        coVerify(exactly = 1) { store.write(recipeId, IMAGE_BYTES) }
    }

    @Test
    fun `does not follow a redirect to a blocked address`() = runTest {
        val store = mockk<RecipeImageStore>(relaxed = true)
        val hostResolver = FakeHostResolver(mapOf("internal.attacker.example" to FakeHostResolver.literal("169.254.169.254")))
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(ByteArray(0)),
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, "https://internal.attacker.example/steal.jpg"),
            )
        }

        val result = useCase(engine, recipeImageStore = store, hostResolver = hostResolver)
            .invoke(UUID.randomUUID(), "https://example.com/original.jpg")

        assertThat(result).isNull()
        coVerify(exactly = 0) { store.write(any(), any()) }
    }

    @Test
    fun `a body larger than the cap is rejected rather than cached truncated`() = runTest {
        val oversized = ByteArray(MAX_IMAGE_BYTES + 1024)
        val fetcher = FakeRenderedImageFetcher(IMAGE_BYTES)
        val store = mockk<RecipeImageStore>(relaxed = true)

        val result = useCase(imageEngine(oversized), fetcher, store)
            .invoke(UUID.randomUUID(), "https://example.com/huge.jpg")

        assertThat(result).isNull()
        assertThat(fetcher.callCount).isEqualTo(0)
        coVerify(exactly = 0) { store.write(any(), any()) }
    }
}
