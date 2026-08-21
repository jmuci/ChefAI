package com.tenmilelabs.chefai.recipes.data.network

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tenmilelabs.chefai.recipes.data.repository.isSafeHost
import com.tenmilelabs.chefai.recipes.domain.repository.HostResolver
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection

/** Skips absurd snapshots rather than marshalling them across the JNI bridge and parsing them. */
private const val MAX_SNAPSHOT_CHARS = 4 * 1024 * 1024

/** JS whose result is the whole rendered document, serialised as a JSON string literal. */
private const val OUTER_HTML_SCRIPT = "document.documentElement.outerHTML"

/**
 * Caps how large a downloaded recipe image is allowed to be — comfortably past any real hero
 * image, while bounding how much a hostile response can inflate memory (twice over: once as raw
 * bytes in the WebView's own fetch, again as the base64 string handed back across the JNI bridge).
 */
internal const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

/** Base64 inflates by ~4/3; a little headroom is left for the `data:<mime>;base64,` prefix. */
private const val MAX_IMAGE_DATA_URL_CHARS = MAX_IMAGE_BYTES * 4 / 3 + 128

/**
 * JS that fetches the document's own URL and hands back its bytes as a `data:` URL.
 *
 * Navigating a WebView directly to an image URL loads it inside a synthetic image document whose
 * origin *is* the image's origin — the only way to read the bytes back with `fetch()` without
 * hitting CORS, and the reason this loads [WebView]'s own `location.href` rather than being handed
 * a URL as an argument. `cache: 'force-cache'` reuses the bytes the navigation itself just
 * downloaded rather than fetching twice.
 *
 * `fetch` is async and `evaluateJavascript` is not, so the outcome is stashed on a `window` global
 * and each call to this script just reads whatever state is current — polled the same way
 * [readRenderedHtml] polls for parseable markup. This is a plain `fetch`, not a bridge into app
 * code: there is deliberately no `addJavascriptInterface` anywhere in this feature (see
 * [applyScraperHardening]'s KDoc).
 */
private val IMAGE_FETCH_SCRIPT = """
    (function () {
      var s = window.__chefaiImg;
      if (!s) {
        window.__chefaiImg = { state: 'pending' };
        fetch(location.href, { cache: 'force-cache' })
          .then(function (r) { if (!r.ok) throw new Error('http ' + r.status); return r.blob(); })
          .then(function (b) {
            if (b.size > $MAX_IMAGE_BYTES) throw new Error('too large: ' + b.size);
            return new Promise(function (res, rej) {
              var fr = new FileReader();
              fr.onload = function () { res(fr.result); };
              fr.onerror = function () { rej(fr.error); };
              fr.readAsDataURL(b);
            });
          })
          .then(function (d) { window.__chefaiImg = { state: 'ok', data: d }; })
          .catch(function (e) { window.__chefaiImg = { state: 'error', message: String(e) }; });
        return 'pending';
      }
      return s.state === 'ok' ? s.data : s.state;
    })()
""".trimIndent()

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
 *
 * [hostResolver] defaults to real DNS ([SystemHostResolver]) rather than being Hilt-injected: this
 * class is constructed directly at each of its three call sites (an off-screen WebView is neither
 * a singleton nor scoped to a screen, so there's no natural injection point), and the default
 * keeps those call sites unchanged while still letting tests substitute a fake.
 */
internal open class ScraperWebViewClient(
    private val hostResolver: HostResolver = SystemHostResolver(),
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val scheme = request?.url?.scheme?.lowercase()
        val isWeb = scheme == "http" || scheme == "https"
        if (!isWeb) Timber.d("Blocked non-web navigation during scrape: %s", request?.url)
        return !isWeb
    }

    /**
     * The authoritative SSRF guard for everything the page itself fetches — the document, every
     * redirect hop, and every subresource (image, XHR, fetch, iframe) — not just the top-level
     * navigation [shouldOverrideUrlLoading] sees. This callback runs off the UI thread (see the
     * platform docs on [WebViewClient.shouldInterceptRequest]), which is what makes a blocking DNS
     * resolution here safe where it would risk an ANR in [shouldOverrideUrlLoading].
     */
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url ?: return null
        val scheme = url.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = url.host
        if (host.isNullOrEmpty() || host.isSafeHost(hostResolver)) return null
        Timber.w("Blocked scrape request to disallowed host: %s", url)
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            HttpURLConnection.HTTP_FORBIDDEN,
            "Forbidden",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )
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

/**
 * Reads the current state of [IMAGE_FETCH_SCRIPT]'s in-page fetch — `"pending"`, `"error"`, a
 * `data:` URL on success, or `null` if the bridge call itself couldn't be decoded.
 */
internal fun WebView.readImageDataUrl(onResult: (String?) -> Unit) {
    evaluateJavascript(IMAGE_FETCH_SCRIPT) { encoded ->
        val result = encoded
            ?.takeIf { it.isNotBlank() && it != "null" && it.length <= MAX_IMAGE_DATA_URL_CHARS }
            ?.let { runCatching { Json.decodeFromString<String>(it) }.getOrNull() }
        onResult(result)
    }
}
