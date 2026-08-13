package com.tenmilelabs.chefai.recipes.data.network

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import com.tenmilelabs.chefai.recipes.domain.repository.RenderedHtmlFetcher
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
import kotlin.time.Duration.Companion.milliseconds

/** How often the rendered DOM is re-read while waiting for a recipe to appear. */
internal val SNAPSHOT_INTERVAL: Duration = 500.milliseconds

/**
 * Loads pages in an off-screen [WebView] — a real browser engine, on the user's own connection.
 *
 * The WebView is never attached to a window; it still fetches, runs scripts and builds a DOM, which
 * is all this needs. Cookies are left to the process-wide `CookieManager` on purpose: a clearance
 * cookie earned on the visible browser screen carries over, so the next import of the same site
 * usually resolves here without troubling the user again.
 *
 * A [WebView] must be created, driven and destroyed on the main thread, so everything below hops
 * there regardless of the caller's dispatcher.
 */
@Singleton
class WebViewHtmlFetcher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RenderedHtmlFetcher {

    override suspend fun fetchRenderedHtml(
        url: String,
        budget: Duration,
        accept: (String) -> Boolean,
    ): String? = withContext(Dispatchers.Main) {
        val webView = WebView(context).apply {
            applyScraperHardening()
            webViewClient = ScraperWebViewClient()
        }

        try {
            withTimeoutOrNull(budget) {
                webView.loadUrl(url)
                // Polling from the moment the load starts, rather than waiting for onPageFinished,
                // means a page whose recipe markup is in the initial HTML resolves in one tick
                // instead of after every last tracker and image has settled.
                var accepted: String? = null
                while (accepted == null) {
                    delay(SNAPSHOT_INTERVAL)
                    val html = webView.awaitRenderedHtml()
                    if (html != null && runCatching { accept(html) }.getOrDefault(false)) {
                        accepted = html
                    }
                }
                accepted
            }
        } catch (e: Exception) {
            Timber.w(e, "Rendered fetch failed for %s", url)
            null
        } finally {
            webView.stopLoading()
            webView.destroy()
            // Commits any clearance cookie earned this call to disk immediately, rather than
            // waiting for whatever batches CookieManager would otherwise use.
            CookieManager.getInstance().flush()
        }
    }
}

/** Suspending wrapper around [readRenderedHtml]'s callback. */
private suspend fun WebView.awaitRenderedHtml(): String? = suspendCancellableCoroutine { continuation ->
    readRenderedHtml { html ->
        if (continuation.isActive) continuation.resume(html)
    }
}
