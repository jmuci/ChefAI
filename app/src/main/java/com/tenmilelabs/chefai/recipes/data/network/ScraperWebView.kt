package com.tenmilelabs.chefai.recipes.data.network

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.serialization.json.Json
import timber.log.Timber

/** Skips absurd snapshots rather than marshalling them across the JNI bridge and parsing them. */
private const val MAX_SNAPSHOT_CHARS = 4 * 1024 * 1024

/** JS whose result is the whole rendered document, serialised as a JSON string literal. */
private const val OUTER_HTML_SCRIPT = "document.documentElement.outerHTML"

/**
 * Applies the settings shared by both recipe-scraping WebViews — the off-screen one in
 * [WebViewHtmlFetcher] and the visible one on the browser-import screen.
 *
 * Scraping means running a third party's JavaScript in our process, so the point of this is to give
 * that script as little to reach for as possible: no local file or content-provider access, no
 * cross-origin escape from a `file://` document, no geolocation, no autoplay. There is deliberately
 * **no `addJavascriptInterface`** anywhere in this feature — it would hand the page a bridge into
 * app code, and nothing here needs one.
 *
 * JavaScript itself stays on: it is the entire reason this path exists.
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.applyScraperHardening() {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        setGeolocationEnabled(false)
        mediaPlaybackRequiresUserGesture = true
        javaScriptCanOpenWindowsAutomatically = false
        // The stock user agent, minus the tokens that only an *embedded* WebView sends — never the
        // desktop string the Ktor client uses. Pairing a Windows Chrome UA with Android's TLS and
        // JS fingerprint would be exactly the mismatch bot detection looks for; this keeps every
        // real token (device, Android version, actual Chrome build) and removes only "; wv" and
        // "Version/X.X ", which exist for no reason other than to tell a server "this request came
        // from inside someone's app, not their browser tab". Interactive challenges like
        // Cloudflare's are documented to treat an embedded context as one they "do not handle
        // well" and can re-issue the check in a loop rather than reject it outright — which reads
        // to the user as the page endlessly reloading.
        userAgentString = userAgentString
            .replace("; wv", "")
            .replace(Regex("Version/[\\d.]+ "), "")
    }

    // Third-party cookie acceptance is opt-in per WebView instance and defaults to off; without it,
    // a cookie set by an iframe'd challenge widget on a different subdomain than the top-level page
    // is silently dropped, so the check clears in the UI but the clearance never sticks.
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@applyScraperHardening, true)
    }
}

/**
 * A [WebViewClient] that keeps the load inside the web: `http(s)` navigations (including the
 * redirects a bot check bounces through) proceed, anything else — `intent://`, `market://`,
 * `tel:` — is refused rather than handed to another app.
 */
internal open class ScraperWebViewClient : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val scheme = request?.url?.scheme?.lowercase()
        val isWeb = scheme == "http" || scheme == "https"
        if (!isWeb) Timber.d("Blocked non-web navigation during scrape: %s", request?.url)
        return !isWeb
    }
}

/**
 * Reads the rendered document out of the WebView, or `null` if it isn't available yet.
 *
 * `evaluateJavascript` hands back a JSON-encoded value, so the result is a quoted string literal
 * with the document's own quotes and newlines escaped; it is decoded rather than unescaped by hand.
 */
internal fun WebView.readRenderedHtml(onResult: (String?) -> Unit) {
    evaluateJavascript(OUTER_HTML_SCRIPT) { encoded ->
        val html = encoded
            ?.takeIf { it.isNotBlank() && it != "null" && it.length <= MAX_SNAPSHOT_CHARS }
            ?.let { runCatching { Json.decodeFromString<String>(it) }.getOrNull() }
        onResult(html)
    }
}
