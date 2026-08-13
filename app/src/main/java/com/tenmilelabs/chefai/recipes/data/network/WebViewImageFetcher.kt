package com.tenmilelabs.chefai.recipes.data.network

import android.content.Context
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebView
import com.tenmilelabs.chefai.recipes.domain.repository.RenderedImageFetcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration

/**
 * Downloads an image in an off-screen [WebView] — see [RenderedImageFetcher]'s KDoc for why.
 *
 * Structurally a sibling of [WebViewHtmlFetcher]: same off-screen, never-attached WebView, same
 * main-thread requirement, same shared process-wide [CookieManager] (a clearance cookie earned
 * loading the page carries over to its images), same hardening. The one difference is what's polled
 * for — [readImageDataUrl] reports a definitive `"error"` state, so this can give up as soon as the
 * fetch actually fails rather than waiting out the full [Duration] budget.
 */
@Singleton
class WebViewImageFetcher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RenderedImageFetcher {

    override suspend fun fetchImageBytes(imageUrl: String, budget: Duration): ByteArray? =
        withContext(Dispatchers.Main) {
            val webView = WebView(context).apply {
                applyScraperHardening()
                webViewClient = ScraperWebViewClient()
            }

            try {
                withTimeoutOrNull(budget) {
                    webView.loadUrl(imageUrl)
                    var result: ByteArray? = null
                    var failed = false
                    while (result == null && !failed) {
                        delay(SNAPSHOT_INTERVAL)
                        when (val state = webView.awaitImageDataUrl()) {
                            null, "pending" -> Unit
                            "error" -> failed = true
                            else -> result = state.toImageBytesOrNull()
                        }
                    }
                    result
                }
            } catch (e: Exception) {
                Timber.w(e, "Rendered image fetch failed for %s", imageUrl)
                null
            } finally {
                webView.stopLoading()
                webView.destroy()
                CookieManager.getInstance().flush()
            }
        }
}

/** Suspending wrapper around [readImageDataUrl]'s callback. */
private suspend fun WebView.awaitImageDataUrl(): String? = suspendCancellableCoroutine { continuation ->
    readImageDataUrl { result ->
        if (continuation.isActive) continuation.resume(result)
    }
}

/** Decodes the base64 payload of a `data:<mime>;base64,<payload>` URL, or `null` if malformed. */
private fun String.toImageBytesOrNull(): ByteArray? {
    val base64 = substringAfter(delimiter = ",", missingDelimiterValue = "")
    if (base64.isEmpty()) return null
    return runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
}
