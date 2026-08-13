package com.tenmilelabs.chefai.recipes.data.network

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/** A minimal (1x1, transparent) valid PNG — small enough to inline, real enough to decode. */
private const val ONE_PIXEL_PNG_BASE64 =
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

/**
 * Exercises the off-screen WebView's image fetch against `data:` URLs and unroutable addresses, so
 * the round-trip and give-up paths are covered without depending on any real CDN.
 */
@RunWith(AndroidJUnit4::class)
class WebViewImageFetcherTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fetcher = WebViewImageFetcher(context)

    @Test
    fun returnsTheDecodedBytesForADataUrl() = runTest {
        val dataUrl = "data:image/png;base64,$ONE_PIXEL_PNG_BASE64"

        val result = fetcher.fetchImageBytes(dataUrl, budget = 10.seconds)

        assertArrayEquals(Base64.decode(ONE_PIXEL_PNG_BASE64, Base64.DEFAULT), result)
    }

    @Test
    fun returnsNullWhenTheConnectionIsRefused() = runTest {
        // Loopback, nothing listening — refused near-instantly, no real network needed.
        val result = fetcher.fetchImageBytes("http://127.0.0.1:1/nope.jpg", budget = 10.seconds)

        assertNull(result)
    }

    @Test
    fun returnsNullWhenTheBudgetRunsOutWithoutAResponse() = runTest {
        // RFC 5737 TEST-NET-1 — reserved for documentation/testing, never routable.
        val result = fetcher.fetchImageBytes("http://192.0.2.1/nope.jpg", budget = 3.seconds)

        assertNull(result)
    }
}
