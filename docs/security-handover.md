# Security handover — recipe-URL scraping path

Facts only; nothing here is a judgement. Branch `claude/pocket-chef-scraping-review-1b3d8b`, worktree of `main` @ `ccd765aa`.

## 0. Scope mismatch (read first)

**This repository contains no Ktor backend and no server-side scraping route.** `settings.gradle.kts` includes only `:app` and `:recipe-scraper`. The backend lives in a separate repo (`jmuci/Chef.ai_Backend`, referenced in `.claude/session-context.md`), which is **not checked out locally and was not read**. [ADR-010](adrs/adr-010-client-side-recipe-scraping.md) Decision 1 states the fetch+parse is deliberately client-side (on-device, Android), and Decision 5 states a backend fetch was rejected.

So the only recipe-URL scraping path in this repo is: **Android app → third-party site** (no ChefAI server in the fetch path). Per the brief ("ignore the Android app unless it builds or validates the URL"), the Android code *does* build, validate and fetch the URL, so it is documented below. The backend is touched only by what is persisted/synced afterwards (§1 steps 13-15); the backend's handling of those fields is an Unknown.

## a. Flow map

Entry points
1. Paste a URL in `ImportRecipeScreen` → `ImportRecipeViewModel.import()` (`ui/urlimport/ImportRecipeViewModel.kt:57-92`).
2. Android share sheet (`ACTION_SEND text/plain`, manifest `AndroidManifest.xml:32-37`) → `MainActivity.consumeShareIntent` (`MainActivity.kt:71-85`) → `extractSharedRecipeUrl` (`core/util/ShareTextUrlExtractor.kt:22-27`) → `normalizeUrlForDisplay` (`RecipeUrlValidation.kt:52-69`, literal-host check only) → nav arg `prefillUrl` (`core/ui/navigation/AppDestinations.kt:100,199-208`) → same ViewModel, auto-imports once (`ImportRecipeViewModel.kt:103-107`).

Fetch + parse (`DefaultRecipeImporter.import`, `recipes/data/repository/DefaultRecipeImporter.kt:62-93`)
3. `normalizeAndValidateUrl(url, SystemHostResolver)` (`RecipeUrlValidation.kt:24-36,110-142`): trim, add `https://` if no scheme, `java.net.URI`, scheme ∈ {http,https}, host resolved via `InetAddress.getAllByName`, every address checked with `isBlockedAddress`.
4. `fetchHtml` (`DefaultRecipeImporter.kt:146-162`): manual redirect loop, ≤5 hops, each `Location` re-validated by `validateRedirectTarget` (`RecipeUrlValidation.kt:96-108`).
5. `fetchOnce` (`:166-211`): `@ScraperHttpClient` Ktor/OkHttp `prepareGet(url).execute{}`; engine DNS is `SsrfSafeDns` (`recipes/data/network/SsrfSafeDns.kt`, re-checks resolved addresses at connect time). Accepts only `text/html` / `application/xhtml+xml`; body read capped at 3 MiB (`readBodyCapped :217-227`), decoded by `decodeHtml` (`HtmlCharset.kt`).
6. Fallback tier on 401/403/429/503 or "page had no recipe": off-screen `WebView` (`WebViewHtmlFetcher.kt`, `ScraperWebView.kt`, `ScraperWebViewClient.shouldInterceptRequest`) loads the URL, polls `document.documentElement.outerHTML` every 500 ms for 8 s (`DefaultRecipeImporter.kt:80-84`).
7. Last resort: `RecipeImportResult.NeedsBrowser` → visible in-app WebView (`ui/urlimport/browser/BrowserImportScreen.kt:196-250`, snapshots → `BrowserImportViewModel.handleSnapshot :68-89` → `importFromHtml`).
8. `RecipeHtmlParser.parse(html, sourceUrl, contentLanguage)` (`:recipe-scraper …/RecipeHtmlParser.kt:41-57`): Ksoup parse → JSON-LD extractor (`jsonld/JsonLdExtractor.kt`, `JsonLdRecipeMapper.kt`) → microdata fallback (`microdata/MicrodataExtractor.kt`) → language detection + ingredient text parse. Open Graph tags are **not** parsed for recipe data (only `og:locale` is read by the language detector, per `RecipeHtmlParserTest`).
9. `ScrapedRecipe.toRecipeDraft` (`recipes/data/mapper/ScrapedRecipeMapper.kt:43-117`): `imageUrl` resolved against `sourceUrl` with `URI.resolve`; `sourceUrl` → `externalUrl`; privacy forced `PRIVATE`; max 8 tags ≤30 chars.
10. `SaveImportedDraft` (`domain/usecase/SaveImportedDraft.kt:20-29`) first calls `CacheRecipeImage`, then writes `RecipeDraftEntity` to Room.

Image fetch (second outbound request, URL taken from scraped HTML)
11. `CacheRecipeImage.invoke` (`domain/usecase/CacheRecipeImage.kt`): `normalizeAndValidateUrl(imageUrl)`, same manual-redirect loop, same client, `Content-Type` must start `image/`, body ≤5 MiB (`CappedImageBody.kt`); on bot-wall status escalates to `WebViewImageFetcher` (off-screen WebView navigated to image URL, in-page `fetch()` → base64 `data:image/` URL, `ScraperWebView.kt:47-69`).
12. `RecipeImageStore.write` (`recipes/data/local/RecipeImageStore.kt:40-42`) writes the raw bytes to `filesDir/recipe_images/<recipeId>` — no decode/re-encode on this path (the `writeFromUri` path used for user-picked photos does re-encode).

After the user saves in the editor (not read in detail)
13. Recipe row (incl. `recipeExternalUrl`, `imageUrl`) is written to Room, then pushed by sync (`core/data/sync/network/dto/SyncDtos.kt` `SyncRecipeDto`; `recipeExternalUrl` appears at `SyncDtos.kt:52`). Auth'd `@HttpClient` (not the scraper client).
14. Image bytes are uploaded by `RecipeImageUploader.upload` → `PUT ${BuildConfig.API_BASE_URL}/recipes/{recipeId}/image` (`recipes/data/network/RecipeImageUploader.kt:76-77`); `RecipeImageBackfillWorker` also calls `CacheRecipeImage` for recipes lacking a local file.
15. Backend response/stored form: **not in this repo**.

## b. Verbatim code

### b.1 HTTP client setup and fetcher

#### `app/src/main/java/com/tenmilelabs/chefai/core/di/NetworkModule.kt` L36-38 — scraper user agent

```kotlin
private const val SCRAPER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/128.0.0.0 Safari/537.36"
```

#### `app/src/main/java/com/tenmilelabs/chefai/core/di/NetworkModule.kt` L138-195 — KDoc + provideScraperHttpClient + parser provider

```kotlin
     * Unauthenticated client for fetching third-party recipe pages during URL import.
     * Must never carry ChefAI auth headers or run scraped HTML through content negotiation.
     *
     * Redirects are followed manually by callers (see [com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter]
     * and [com.tenmilelabs.chefai.recipes.domain.usecase.CacheRecipeImage]) rather than by the
     * client, so every hop can be re-validated against the SSRF guard before it's followed — a
     * page can 3xx to an internal address just as easily as it can host one directly.
     *
     * Engine is OkHttp, not CIO: CIO's TLS handshake on Android calls the legacy 2-arg
     * `checkServerTrusted`, which `TrustManagerImpl` rejects with a `CertificateException` for any
     * server needing hostname-aware validation — no CIO-side fix exists (see
     * https://github.com/ktorio/ktor/issues/1029). Third-party recipe sites are exactly the
     * population this hits. OkHttp is already on the classpath transitively via
     * `coil-network-okhttp`.
     */
    @Provides
    @Singleton
    @ScraperHttpClient
    fun provideScraperHttpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = true
        followRedirects = false

        engine {
            config { dns(SsrfSafeDns()) }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            // A hostile page's `Retry-After: 3600` would otherwise stall the import (and the
            // backfill worker) for as long as it asks — maxDelayMs doesn't cap that part.
            exponentialDelay(respectRetryAfterHeader = false)
        }

        // Every imported URL would otherwise go to logcat in release builds.
        if (BuildConfig.DEBUG) {
            install(Logging) {
                logger = Logger.SIMPLE
                level = LogLevel.INFO
            }
        }

        defaultRequest {
            header(HttpHeaders.UserAgent, SCRAPER_USER_AGENT)
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
        }
    }

    /** Stateless — [RecipeHtmlParser] lives in the dependency-free `:recipe-scraper` module. */
    @Provides
    @Singleton
    fun provideRecipeHtmlParser(): RecipeHtmlParser = RecipeHtmlParser()
}```

#### `app/src/main/java/com/tenmilelabs/chefai/recipes/data/repository/DefaultRecipeImporter.kt` L38-228 — orchestrator, redirect loop, body cap

```kotlin
/** Caps how much of a fetched page is read into memory — well past any real recipe page. */
private const val MAX_BODY_BYTES = 3 * 1024 * 1024

/** Bounds manual redirect-following — the same guard rail browsers apply against redirect loops. */
private const val MAX_REDIRECT_HOPS = 5

/**
 * How long the off-screen browser gets to produce a recipe before the import gives up on it.
 *
 * Long enough for a bot check that resolves itself plus a heavy page load, short enough that a URL
 * which simply isn't a recipe doesn't leave the user watching a spinner. Tune from real use.
 */
private val RENDER_BUDGET = 8.seconds

@Singleton
class DefaultRecipeImporter @Inject constructor(
    @ScraperHttpClient private val httpClient: HttpClient,
    private val recipeHtmlParser: RecipeHtmlParser,
    private val renderedHtmlFetcher: RenderedHtmlFetcher,
    private val metadataRepository: MetadataRepository,
    private val hostResolver: HostResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RecipeImporter {

    override suspend fun import(url: String): RecipeImportResult = withContext(ioDispatcher) {
        val normalizedUrl = normalizeAndValidateUrl(url, hostResolver) ?: return@withContext RecipeImportResult.InvalidUrl

        val fetched = fetchHtml(normalizedUrl)
        val httpResult = when (fetched) {
            is FetchOutcome.Failure -> fetched.error
            is FetchOutcome.Html -> importParsed(fetched.body, normalizedUrl, fetched.contentLanguage)
        }

        // A plain GET is enough for the large majority of recipe sites. Exactly two shapes of
        // failure are worth a second attempt in a real browser engine:
        //  - the site refused a non-browser client outright (a bot manager's 403);
        //  - it served a shell whose recipe markup only exists once its own scripts have run.
        // Anything else — a 404, a DNS failure, a timeout — would fail the same way in a browser,
        // several seconds later, so it is returned as-is.
        val refused = fetched is FetchOutcome.Failure && fetched.looksLikeBotWall
        if (!refused && httpResult !is RecipeImportResult.NoRecipeFound) return@withContext httpResult

        Timber.i("HTTP import fell short for %s, retrying with a rendered fetch", normalizedUrl)
        val rendered = renderedHtmlFetcher.fetchRenderedHtml(normalizedUrl, RENDER_BUDGET) { html ->
            recipeHtmlParser.parse(html, normalizedUrl) is ScrapeResult.Success
        }

        when {
            rendered != null -> importFromHtml(rendered, normalizedUrl)
            // Out of automated options. Only a browser the user can interact with clears an
            // interactive check — but a page that simply has no recipe on it stays that way, so
            // there is nothing to send them to.
            refused -> RecipeImportResult.NeedsBrowser(normalizedUrl)
            else -> httpResult
        }
    }

    override suspend fun importFromHtml(html: String, url: String): RecipeImportResult =
        importParsed(html, url, contentLanguage = null)

    /**
     * [contentLanguage] is the HTTP `Content-Language` header, when this importer fetched the page
     * itself — the weakest of the parser's language signals. The rendered and visible-browser paths
     * have no response headers, and don't need them: `<html lang>` survives in the DOM snapshot.
     */
    private suspend fun importParsed(html: String, url: String, contentLanguage: String?): RecipeImportResult =
        withContext(ioDispatcher) {
            when (val scrapeResult = recipeHtmlParser.parse(html, url, contentLanguage)) {
                is ScrapeResult.Success -> {
                    val knownIngredients = metadataRepository.observeAllIngredients().first()
                    val knownTags = metadataRepository.observeAllTags().first()
                    val draft = scrapeResult.recipe.toRecipeDraft(
                        recipeId = UuidV7Generator.newId(),
                        knownIngredients = knownIngredients,
                        knownTags = knownTags,
                    )
                    RecipeImportResult.Success(draft)
                }

                ScrapeResult.NoRecipeFound -> RecipeImportResult.NoRecipeFound
                is ScrapeResult.ParseError -> RecipeImportResult.ParseError(scrapeResult.message)
            }
        }

    private sealed interface FetchOutcome {
        data class Html(val body: String, val contentLanguage: String? = null) : FetchOutcome

        /**
         * @property looksLikeBotWall the server answered, and answered with a refusal rather than
         *   a "no such page" — the signature of bot protection turning away a non-browser client.
         */
        data class Failure(
            val error: RecipeImportResult.NetworkError,
            val looksLikeBotWall: Boolean = false,
        ) : FetchOutcome
    }

    /** One step of [fetchHtml]'s redirect loop: either a terminal [outcome], or one more [location] to follow. */
    private sealed interface FetchStep {
        data class Terminal(val outcome: FetchOutcome) : FetchStep
        data class Redirect(val location: String) : FetchStep
    }

    /**
     * Follows redirects itself (the client has `followRedirects = false` — see
     * [com.tenmilelabs.chefai.core.di.NetworkModule.provideScraperHttpClient]) so every hop can be
     * re-validated by [validateRedirectTarget] before it's followed, not just the first URL.
     */
    private suspend fun fetchHtml(url: String): FetchOutcome {
        var currentUrl = url
        repeat(MAX_REDIRECT_HOPS + 1) {
            when (val step = fetchOnce(currentUrl)) {
                is FetchStep.Terminal -> return step.outcome
                is FetchStep.Redirect -> {
                    val next = validateRedirectTarget(currentUrl, step.location, hostResolver)
                    if (next == null) {
                        Timber.w("Recipe import redirect failed the SSRF guard: %s", step.location)
                        return FetchOutcome.Failure(RecipeImportResult.NetworkError("Redirected to a disallowed address"))
                    }
                    currentUrl = next
                }
            }
        }
        return FetchOutcome.Failure(RecipeImportResult.NetworkError("Too many redirects"))
    }

    // prepareGet().execute { } streams the body: a plain get() reads the whole response into memory
    // first, so a huge (or gzip-bomb) page would OOM before readBodyCapped ever ran.
    private suspend fun fetchOnce(url: String): FetchStep = try {
        httpClient.prepareGet(url).execute { response ->
            // A malformed Content-Type header makes contentType() throw; treat it as "not HTML".
            val contentType = runCatching { response.contentType() }.getOrNull()
            val mimeType = contentType?.withoutParameters()?.toString().orEmpty()
            val outcome = if (!mimeType.equals("text/html", ignoreCase = true) &&
                !mimeType.equals("application/xhtml+xml", ignoreCase = true)
            ) {
                FetchOutcome.Failure(RecipeImportResult.NetworkError("Not an HTML page"))
            } else {
                FetchOutcome.Html(
                    body = response.readBodyCapped(contentType?.charset()),
                    contentLanguage = response.headers[HttpHeaders.ContentLanguage],
                )
            }
            FetchStep.Terminal(outcome)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: RedirectResponseException) {
        val location = e.response.headers[HttpHeaders.Location]
        if (location != null) {
            FetchStep.Redirect(location)
        } else {
            FetchStep.Terminal(FetchOutcome.Failure(RecipeImportResult.NetworkError("Redirect missing Location")))
        }
    } catch (e: HttpRequestTimeoutException) {
        Timber.w(e, "Recipe import request timed out")
        FetchStep.Terminal(FetchOutcome.Failure(RecipeImportResult.NetworkError(e.message ?: "Request timed out")))
    } catch (e: ResponseException) {
        Timber.w(e, "Recipe import received an error response")
        FetchStep.Terminal(
            FetchOutcome.Failure(
                error = RecipeImportResult.NetworkError(e.message ?: "Request failed"),
                looksLikeBotWall = e.response.status in BOT_WALL_STATUSES,
            ),
        )
    } catch (e: IOException) {
        Timber.w(e, "Recipe import network error")
        FetchStep.Terminal(FetchOutcome.Failure(RecipeImportResult.NetworkError(e.message ?: "Network error")))
    } catch (e: Exception) {
        // Anything else (e.g. a malformed header Ktor refuses to parse) must not escape into the
        // ViewModel's launch, which has no handler and would crash the app.
        Timber.w(e, "Recipe import failed unexpectedly")
        FetchStep.Terminal(FetchOutcome.Failure(RecipeImportResult.NetworkError(e.message ?: "Request failed")))
    }

    /**
     * Reads at most [MAX_BODY_BYTES] of the response body, protecting against a hostile/huge page,
     * and decodes it using [responseCharset] when the server declared one — see [decodeHtml].
     */
    private suspend fun HttpResponse.readBodyCapped(responseCharset: Charset?): String {
        val channel = bodyAsChannel()
        val buffer = ByteArray(MAX_BODY_BYTES)
        var offset = 0
        while (offset < buffer.size) {
            val read = channel.readAvailable(buffer, offset, buffer.size - offset)
            if (read == -1) break
            offset += read
        }
        return decodeHtml(buffer, offset, responseCharset)
    }
}
```

#### `app/src/main/java/com/tenmilelabs/chefai/recipes/data/network/SsrfSafeDns.kt` L8-25

```kotlin
/**
 * DNS for the scraper client that refuses any host resolving to a non-public address.
 *
 * `isSafeHost` validates a URL before it is fetched, but OkHttp then resolves the host again to
 * connect — a DNS-rebinding server can answer with a public IP the first time and an internal one
 * the second (or on a retry). Filtering here, at the resolution OkHttp actually connects with,
 * closes that window.
 */
internal class SsrfSafeDns(private val delegate: Dns = Dns.SYSTEM) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (addresses.any { it.isBlockedAddress() }) {
            throw UnknownHostException("$hostname resolves to a disallowed address")
        }
        return addresses
    }
}
```

#### `app/src/main/java/com/tenmilelabs/chefai/recipes/data/network/SystemHostResolver.kt` L8-16

```kotlin
/** [HostResolver] backed by the platform's real DNS resolution. */
class SystemHostResolver @Inject constructor() : HostResolver {

    override fun resolve(host: String): List<InetAddress> = try {
        InetAddress.getAllByName(host).toList()
    } catch (e: UnknownHostException) {
        emptyList()
    }
}
```

#### `app/src/main/java/com/tenmilelabs/chefai/recipes/data/network/BotWall.kt` L14-19

```kotlin
internal val BOT_WALL_STATUSES = setOf(
    HttpStatusCode.Unauthorized,
    HttpStatusCode.Forbidden,
    HttpStatusCode.TooManyRequests,
    HttpStatusCode.ServiceUnavailable,
)
```

### b.2 URL handling / validation

#### `app/src/main/java/com/tenmilelabs/chefai/recipes/data/repository/RecipeUrlValidation.kt` L8-142

```kotlin
private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

/**
 * Trims and, if absent, adds an `https://` scheme, then validates the result is a plausible
 * public web URL: `http`/`https` only, a host present, and — once resolved — not a
 * loopback/private-network/link-local/multicast address. The URL may come from user paste or from
 * a scraped page's own markup, so there's no reason for the app to fetch an internal address on
 * its behalf.
 *
 * Validation resolves the host through [hostResolver] rather than pattern-matching the string, so
 * a hostname whose DNS record points at an internal address is caught the same way a literal IP
 * would be — including IPv6 and every alternate IPv4 encoding the platform resolver itself
 * accepts, since that resolver is also what the real connection uses.
 *
 * Returns `null` when the input is not a usable, safe URL.
 */
internal fun normalizeAndValidateUrl(input: String, hostResolver: HostResolver): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if (SCHEME_PREFIX.containsMatchIn(trimmed)) trimmed else "https://$trimmed"

    val uri = try {
        URI(withScheme)
    } catch (e: URISyntaxException) {
        return null
    }

    return uri.validated(hostResolver)
}

/**
 * A cheap, no-network pre-filter for call sites that can't run [normalizeAndValidateUrl] — namely
 * [com.tenmilelabs.chefai.core.util.extractSharedRecipeUrl], invoked synchronously from
 * `MainActivity.onCreate`/`onNewIntent` on the main thread, where a real DNS lookup risks a
 * `NetworkOnMainThreadException`.
 *
 * This recognizes only a host that is *itself* a literal loopback/private-network address or
 * `localhost` — nowhere near as strong a guard as [normalizeAndValidateUrl], since it can't catch
 * a hostname whose DNS record points somewhere unsafe. It exists purely so a share-sheet paste of
 * an obviously-internal address is rejected immediately rather than momentarily populating the UI;
 * every URL from this path still goes through the full, resolver-backed check once it reaches
 * [com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter], which is the actual
 * security boundary.
 */
internal fun normalizeUrlForDisplay(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if (SCHEME_PREFIX.containsMatchIn(trimmed)) trimmed else "https://$trimmed"

    val uri = try {
        URI(withScheme)
    } catch (e: URISyntaxException) {
        return null
    }

    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host ?: return null
    if (host.isObviouslyUnsafeLiteralHost()) return null

    return uri.toString()
}

/** IPv4-octet/`localhost` string check only — see [normalizeUrlForDisplay]'s KDoc for why. */
private fun String.isObviouslyUnsafeLiteralHost(): Boolean {
    if (lowercase() == "localhost") return true
    val octets = split(".")
    if (octets.size != 4) return false
    val values = octets.map { it.toIntOrNull() ?: return false }
    val (a, b) = values[0] to values[1]
    return when {
        a == 127 -> true // 127.0.0.0/8
        a == 10 -> true // 10.0.0.0/8
        a == 192 && b == 168 -> true // 192.168.0.0/16
        a == 172 && b in 16..31 -> true // 172.16.0.0/12
        a == 169 && b == 254 -> true // 169.254.0.0/16
        else -> false
    }
}

/**
 * Resolves [location] against [baseUrl] (it may be absolute or relative, as a redirect's
 * `Location` header can be either) and validates the result exactly as [normalizeAndValidateUrl]
 * would.
 *
 * A redirect is a second, independent trip through the SSRF guard: nothing about the original URL
 * passing validation says anything about where it 3xx's to next.
 */
internal fun validateRedirectTarget(baseUrl: String, location: String, hostResolver: HostResolver): String? {
    val base = try {
        URI(baseUrl)
    } catch (e: URISyntaxException) {
        return null
    }
    val resolved = try {
        base.resolve(location)
    } catch (e: IllegalArgumentException) {
        return null
    }
    return resolved.validated(hostResolver)
}

private fun URI.validated(hostResolver: HostResolver): String? {
    val scheme = scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = host ?: return null
    if (!host.isSafeHost(hostResolver)) return null
    return toString()
}

/** Also used directly by [com.tenmilelabs.chefai.recipes.data.network.ScraperWebViewClient] to vet every request a scraped page itself makes. */
internal fun String.isSafeHost(hostResolver: HostResolver): Boolean {
    if (lowercase() == "localhost") return false
    val addresses = hostResolver.resolve(this)
    if (addresses.isEmpty()) return false
    return addresses.none { it.isBlockedAddress() }
}

internal fun InetAddress.isBlockedAddress(): Boolean {
    if (isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isAnyLocalAddress || isMulticastAddress) {
        return true
    }
    val bytes = address
    if (bytes.size == 4) {
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        return first == 0 || // 0.0.0.0/8 — "this network"; isAnyLocalAddress only covers 0.0.0.0
            (first == 100 && second in 64..127) || // 100.64.0.0/10 — CGNAT / Tailscale tailnets
            (first == 198 && second in 18..19) || // 198.18.0.0/15 — benchmarking
            (first == 255) // broadcast
    }
    // IPv6 unique local addresses, fc00::/7 — isSiteLocalAddress above only recognizes the
    // deprecated IPv6 site-local range (fec0::/10), not this one.
    return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
}
```

#### `app/src/main/java/com/tenmilelabs/chefai/core/util/ShareTextUrlExtractor.kt` L5-27

```kotlin
private val URL_REGEX = Regex("https?://\\S+")

/** Trailing punctuation a sentence wraps a shared link in, e.g. "...check it out: https://x.com/r." */
private val TRAILING_PUNCTUATION = Regex("[.,;:!?)\\]\"']+$")

/**
 * Extracts a usable recipe URL from arbitrary shared text — typically Android's share-sheet
 * `EXTRA_TEXT`, which often reads like "Check out this recipe! https://example.com/r Enjoy!"
 * rather than a bare URL.
 *
 * The text comes from another app and is exactly as untrusted as a pasted URL, so the candidate is
 * run through [normalizeUrlForDisplay] — an invalid URL, or one whose host is *itself* an obvious
 * loopback/private-network literal, yields `null` here too, rather than being handed to the
 * importer. This runs on the main thread (see `MainActivity.consumeShareIntent`), so it can only
 * do a no-network check — see [normalizeUrlForDisplay]'s KDoc. The importer re-validates the URL
 * with the full, DNS-resolving guard before ever fetching it.
 */
fun extractSharedRecipeUrl(sharedText: String?): String? {
    val match = sharedText?.let { URL_REGEX.find(it)?.value } ?: return null
    val trimmed = match.replace(TRAILING_PUNCTUATION, "")
    if (trimmed.isBlank()) return null
    return normalizeUrlForDisplay(trimmed)
}
```

#### `app/src/main/java/com/tenmilelabs/chefai/MainActivity.kt` L71-85 — share-intent entry

```kotlin
    private fun consumeShareIntent(intent: Intent) {
        // startsWith: some senders append parameters, e.g. "text/plain; charset=utf-8".
        if (intent.action != Intent.ACTION_SEND || intent.type?.startsWith("text/plain") != true) return
        // getCharSequenceExtra: some senders put a Spanned in EXTRA_TEXT, which getStringExtra
        // returns as null.
        val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: return
        intent.removeExtra(Intent.EXTRA_TEXT)
        val url = extractSharedRecipeUrl(sharedText)
        if (url == null) {
            // Otherwise the app just opens on whatever screen was last up, with no hint why.
            Toast.makeText(this, R.string.share_no_link_found, Toast.LENGTH_LONG).show()
            return
        }
        pendingSharedUrl = url
    }
```

### b.3 "Route" equivalents (UI entry points; there is no server route)

#### `app/src/main/java/com/tenmilelabs/chefai/recipes/ui/urlimport/ImportRecipeViewModel.kt` L57-107

```kotlin
    private fun import() {
        if (!_state.value.canImport) return
        val url = _state.value.url

        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, errorRes = null, showManualEntryOption = false) }

            when (val result = recipeImporter.import(url)) {
                is RecipeImportResult.Success -> {
                    saveImportedDraft(result.draft)
                    _state.update { it.copy(isImporting = false) }
                    _effects.send(ImportEffect.NavigateToEditorWithDraft(result.draft.recipeId))
                }

                // The site refused every automated fetch — hand it to a browser the user can see
                // and interact with, which is the only thing that gets past an interactive check.
                is RecipeImportResult.NeedsBrowser -> {
                    _state.update { it.copy(isImporting = false) }
                    _effects.send(ImportEffect.NavigateToBrowserImport(result.url))
                }

                RecipeImportResult.InvalidUrl -> failWith(R.string.import_recipe_invalid_url)

                RecipeImportResult.NoRecipeFound -> _state.update {
                    it.copy(
                        isImporting = false,
                        errorRes = R.string.import_recipe_no_recipe_found,
                        showManualEntryOption = true,
                    )
                }

                is RecipeImportResult.NetworkError -> failWith(R.string.import_recipe_network_error)
                is RecipeImportResult.ParseError -> failWith(R.string.import_recipe_parse_error)
            }
        }
    }

    /**
     * A URL shared into the app (share sheet → Pocket Chef) is imported straight away: sharing the
     * page *was* the user's choice, so making them tap Import again adds nothing.
     *
     * Runs at most once per destination. The import screen stays on the back stack under the
     * editor, and after process death this ViewModel is rebuilt from the same [SavedStateHandle]
     * (prefill argument included) — without the flag, coming back to the app would silently
     * import the same page a second time.
     */
    private fun autoImportSharedUrl() {
        if (prefillUrl == null || savedStateHandle.get<Boolean>(AUTO_IMPORT_STARTED_KEY) == true) return
        savedStateHandle[AUTO_IMPORT_STARTED_KEY] = true
        import()
    }
```

### b.4 WebView tier

#### `app/src/main/java/com/tenmilelabs/chefai/recipes/data/network/ScraperWebView.kt` L120-164 — ScraperWebViewClient (SSRF guard for page requests)

```kotlin
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
```

#### `app/src/main/java/com/tenmilelabs/chefai/recipes/data/network/WebViewHtmlFetcher.kt` L34-83

```kotlin
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
```

### b.5 Image fetch (URL comes from scraped HTML)

#### `app/src/main/java/com/tenmilelabs/chefai/recipes/domain/usecase/CacheRecipeImage.kt` L23-148

```kotlin
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/** Overrides the scraper client's default HTML `Accept` for this one request. */
private const val IMAGE_ACCEPT_HEADER = "image/avif,image/webp,image/*,*/*;q=0.8"

/** Bounds manual redirect-following — mirrors [com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter]. */
private const val MAX_REDIRECT_HOPS = 5

/**
 * Budget for the WebView tier — matches
 * [com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter]'s `RENDER_BUDGET` for the
 * HTML fetch.
 */
private val IMAGE_RENDER_BUDGET = 8.seconds

/**
 * Downloads a recipe's hero image and caches it on-device.
 *
 * Mirrors [com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter]'s two-tier ladder
 * one tier lower: a plain HTTP GET first, escalating to an off-screen WebView only when the response
 * looks like a bot wall refusing a non-browser client (see [BOT_WALL_STATUSES]). The same CDNs that
 * block the HTML fetch — Akamai on Food Network, Cloudflare on Serious Eats — block their own
 * images the same way; see ADR-010 Decision 6.
 *
 * Never fails the caller: any error, including a genuinely missing or unreachable image, returns
 * `null` rather than throwing. A recipe without a cached image is a normal outcome — the remote
 * `imageUrl` scraped into the draft is still there to fall back on — not a broken import, so every
 * failure path here is swallowed and logged rather than propagated.
 */
class CacheRecipeImage @Inject constructor(
    @ScraperHttpClient private val httpClient: HttpClient,
    private val renderedImageFetcher: RenderedImageFetcher,
    private val recipeImageStore: RecipeImageStore,
    private val hostResolver: HostResolver,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(recipeId: UUID, imageUrl: String): String? = withContext(ioDispatcher) {
        // The URL was scraped out of a third party's HTML, not typed by the user — the same SSRF
        // guard normalizeAndValidateUrl applies to a pasted page URL applies here too.
        val url = normalizeAndValidateUrl(imageUrl, hostResolver) ?: return@withContext null

        val bytes = fetchBytes(url) ?: return@withContext null
        recipeImageStore.write(recipeId, bytes)
    }

    private suspend fun fetchBytes(url: String): ByteArray? {
        val outcome = fetchOverHttp(url)
        return when (outcome) {
            is FetchOutcome.Bytes -> outcome.bytes
            is FetchOutcome.Failure -> {
                if (!outcome.looksLikeBotWall) return null
                Timber.i("HTTP image fetch fell short for %s, retrying with a rendered fetch", url)
                renderedImageFetcher.fetchImageBytes(url, IMAGE_RENDER_BUDGET)
            }
        }
    }

    /**
     * Follows redirects itself (the client has `followRedirects = false` — see
     * [com.tenmilelabs.chefai.core.di.NetworkModule.provideScraperHttpClient]) so every hop can be
     * re-validated by [validateRedirectTarget] before it's followed, not just the first URL.
     */
    private suspend fun fetchOverHttp(url: String): FetchOutcome {
        var currentUrl = url
        repeat(MAX_REDIRECT_HOPS + 1) {
            when (val step = fetchOnce(currentUrl)) {
                is FetchStep.Terminal -> return step.outcome
                is FetchStep.Redirect -> {
                    val next = validateRedirectTarget(currentUrl, step.location, hostResolver)
                    if (next == null) {
                        Timber.w("Recipe image redirect failed the SSRF guard: %s", step.location)
                        return FetchOutcome.Failure(looksLikeBotWall = false)
                    }
                    currentUrl = next
                }
            }
        }
        return FetchOutcome.Failure(looksLikeBotWall = false)
    }

    private suspend fun fetchOnce(url: String): FetchStep = try {
        // Streamed (prepareGet().execute { }) so readImageBodyCapped's cap actually bounds memory —
        // a plain get() buffers the whole body first.
        httpClient.prepareGet(url) {
            headers { set(HttpHeaders.Accept, IMAGE_ACCEPT_HEADER) }
        }.execute { response ->
            val mimeType = response.contentType()?.withoutParameters()?.toString().orEmpty()
            val outcome = if (!mimeType.startsWith("image/")) {
                FetchOutcome.Failure(looksLikeBotWall = false)
            } else {
                val bytes = response.readImageBodyCapped()
                if (bytes != null) FetchOutcome.Bytes(bytes) else FetchOutcome.Failure(looksLikeBotWall = false)
            }
            FetchStep.Terminal(outcome)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: RedirectResponseException) {
        val location = e.response.headers[HttpHeaders.Location]
        if (location != null) FetchStep.Redirect(location) else FetchStep.Terminal(FetchOutcome.Failure(looksLikeBotWall = false))
    } catch (e: ResponseException) {
        Timber.w(e, "Recipe image fetch received an error response")
        FetchStep.Terminal(FetchOutcome.Failure(looksLikeBotWall = e.response.status in BOT_WALL_STATUSES))
    } catch (e: Exception) {
        // Deliberately broader than DefaultRecipeImporter.fetchHtml's catch list: unlike the HTML
        // fetch (whose caller branches on a sealed RecipeImportResult), nothing upstream of this use
        // case handles a thrown exception — SaveImportedDraft has no try/catch around it — so an
        // uncaught type here would crash the import instead of just leaving it imageless.
        Timber.w(e, "Recipe image fetch failed for %s", url)
        FetchStep.Terminal(FetchOutcome.Failure(looksLikeBotWall = false))
    }

    private sealed interface FetchOutcome {
        data class Bytes(val bytes: ByteArray) : FetchOutcome
        data class Failure(val looksLikeBotWall: Boolean) : FetchOutcome
    }

    /** One step of [fetchOverHttp]'s redirect loop: either a terminal [outcome], or one more [location] to follow. */
    private sealed interface FetchStep {
        data class Terminal(val outcome: FetchOutcome) : FetchStep
        data class Redirect(val location: String) : FetchStep
    }
}
```

#### `app/src/main/java/com/tenmilelabs/chefai/recipes/data/network/CappedImageBody.kt` L17-34

```kotlin
internal suspend fun HttpResponse.readImageBodyCapped(): ByteArray? {
    val declaredLength = contentLength()
    if (declaredLength != null && declaredLength > MAX_IMAGE_BYTES) return null

    val channel = bodyAsChannel()
    val buffer = ByteArray(MAX_IMAGE_BYTES)
    var offset = 0
    while (offset < buffer.size) {
        val read = channel.readAvailable(buffer, offset, buffer.size - offset)
        if (read == -1) break
        offset += read
    }
    if (offset == buffer.size) {
        val probe = ByteArray(1)
        if (channel.readAvailable(probe, 0, 1) > 0) return null
    }
    return buffer.copyOf(offset)
}
```

### b.6 Parsers

#### `recipe-scraper/src/commonMain/kotlin/com/tenmilelabs/recipescraper/RecipeHtmlParser.kt` L32-74

```kotlin
class RecipeHtmlParser {

    /**
     * Extracts a recipe from [html]. [sourceUrl] is recorded on the result as-is; it is never read
     * from the page itself.
     *
     * [contentLanguage] is the HTTP `Content-Language` response header, if the caller fetched the
     * page itself and has one. It is the weakest language signal and is safe to omit.
     */
    fun parse(html: String, sourceUrl: String, contentLanguage: String? = null): ScrapeResult = try {
        val document = Ksoup.parse(html)

        val jsonLd = extractJsonLdRecipe(document, sourceUrl)
        if (jsonLd != null && jsonLd.isUsable()) {
            ScrapeResult.Success(jsonLd.withLanguage(document, contentLanguage), ExtractionSource.JSON_LD)
        } else {
            val microdata = extractMicrodataRecipe(document, sourceUrl)
            if (microdata != null && microdata.isUsable()) {
                ScrapeResult.Success(microdata.withLanguage(document, contentLanguage), ExtractionSource.MICRODATA)
            } else {
                ScrapeResult.NoRecipeFound
            }
        }
    } catch (e: Exception) {
        ScrapeResult.ParseError(e.message ?: "Unknown parse error")
    }
}

private fun ScrapedRecipe.isUsable(): Boolean =
    title.isNotBlank() && ingredients.isNotEmpty()

/**
 * Replaces the extractor's provisional [ScrapedRecipe.language] with the final verdict, then
 * re-parses each ingredient from its untouched [com.tenmilelabs.recipescraper.model.ScrapedIngredient.raw]
 * line with that language.
 */
private fun ScrapedRecipe.withLanguage(document: Document, contentLanguage: String?): ScrapedRecipe {
    val detected = detectLanguage(this, document, contentLanguage)
    return copy(
        language = detected,
        ingredients = ingredients.map { parseIngredient(it.raw, detected?.code) },
    )
}
```

#### `recipe-scraper/src/commonMain/kotlin/com/tenmilelabs/recipescraper/jsonld/JsonLdExtractor.kt` L22-64

```kotlin
internal fun extractJsonLdRecipe(document: Document, sourceUrl: String): ScrapedRecipe? {
    var fallback: ScrapedRecipe? = null
    for (script in document.select("script[type=application/ld+json]")) {
        val root = runCatching { LENIENT_JSON.parseToJsonElement(script.data()) }.getOrNull() ?: continue
        for (candidate in findRecipeCandidates(root)) {
            val recipe = mapJsonLdRecipe(candidate, sourceUrl) ?: continue
            if (recipe.ingredients.isNotEmpty()) return recipe
            if (fallback == null) fallback = recipe
        }
    }
    return fallback
}

private val LENIENT_JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Walks a parsed JSON-LD document looking for `Recipe`-typed objects, in document order. Handles a
 * bare object, a top-level array, an `@graph` wrapper, an `@graph` nested inside an array element,
 * and a recipe hung off a `WebPage`'s `mainEntity`. Depth-capped like the mapper's walkers.
 */
private fun findRecipeCandidates(element: JsonElement, depth: Int = 0): List<JsonObject> =
    if (depth >= MAX_NESTING_DEPTH) emptyList() else when (element) {
        is JsonObject -> buildList {
            if (element.isRecipeType()) add(element)
            (element["@graph"] as? JsonArray)?.let { addAll(findRecipeCandidates(it, depth + 1)) }
            element["mainEntity"]?.let { addAll(findRecipeCandidates(it, depth + 1)) }
        }

        is JsonArray -> element.flatMap { findRecipeCandidates(it, depth + 1) }
        else -> emptyList()
    }

private fun JsonObject.isRecipeType(): Boolean = typeNames().any { it.equals("Recipe", ignoreCase = true) }

/** `@type` as either a bare string or an array of strings. */
internal fun JsonObject.typeNames(): List<String> = when (val type = this["@type"]) {
    is JsonPrimitive -> listOfNotNull(type.contentOrNull)
    is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    else -> emptyList()
}
```

#### `recipe-scraper/src/commonMain/kotlin/com/tenmilelabs/recipescraper/jsonld/JsonLdRecipeMapper.kt` L32-57 — mapJsonLdRecipe

```kotlin
internal fun mapJsonLdRecipe(obj: JsonObject, sourceUrl: String): ScrapedRecipe? {
    val title = cleanHtmlText(obj.stringOrNull("name")) ?: return null
    val nutrition = obj["nutrition"] as? JsonObject

    return ScrapedRecipe(
        title = title,
        description = cleanHtmlText(obj.stringOrNull("description")),
        imageUrl = obj["image"]?.firstImageUrlOrNull(),
        prepTimeMinutes = parseDurationToMinutes(obj.stringOrNull("prepTime")),
        cookTimeMinutes = parseDurationToMinutes(obj.stringOrNull("cookTime")),
        totalTimeMinutes = parseDurationToMinutes(obj.stringOrNull("totalTime")),
        servings = obj["recipeYield"]?.yieldStrings()?.firstNotNullOfOrNull(::firstIntOrNull),
        caloriesPerServing = nutrition?.stringOrNull("calories")
            ?.let { firstNutritionIntOrNull(it, MAX_PLAUSIBLE_CALORIES) },
        proteinGramsPerServing = nutrition?.stringOrNull("proteinContent")
            ?.let { firstNutritionIntOrNull(it, MAX_PLAUSIBLE_PROTEIN_GRAMS) },
        ingredients = (obj["recipeIngredient"] ?: obj["ingredients"])?.toIngredients().orEmpty(),
        instructions = obj["recipeInstructions"]?.toInstructionSteps().orEmpty(),
        keywords = obj.keywords(),
        author = obj["author"]?.firstAuthorNameOrNull(),
        sourceUrl = sourceUrl,
        // Provisional: RecipeHtmlParser weighs this against the page's other signals.
        language = normalizeLanguageTag(obj["inLanguage"]?.languageTagOrNull())
            ?.let { DetectedLanguage(it, LanguageConfidence.HIGH, LanguageSource.JSON_LD) },
    )
}
```

#### `recipe-scraper/src/commonMain/kotlin/com/tenmilelabs/recipescraper/jsonld/JsonLdRecipeMapper.kt` L89-118 — ingredient/instruction walkers + MAX_NESTING_DEPTH

```kotlin
private fun JsonElement.toIngredients(depth: Int = 0): List<ScrapedIngredient> =
    when (this) {
        is JsonArray -> if (depth >= MAX_NESTING_DEPTH) emptyList() else flatMap { it.toIngredients(depth + 1) }
        is JsonObject -> listOfNotNull(cleanHtmlText(stringOrNull("name") ?: stringOrNull("text")))
            .map(::parseIngredient)

        is JsonPrimitive -> listOfNotNull(contentOrNull?.let(::cleanHtmlText)).map(::parseIngredient)
    }

/**
 * How deep nested arrays / `HowToSection`s are followed — by every recursive helper in this file and
 * by `findRecipeCandidates`. Real pages nest two or three levels; the cap stops a hostile page from
 * nesting thousands deep and overflowing the stack — a `StackOverflowError` is not an `Exception`,
 * so RecipeHtmlParser's catch wouldn't contain it (#339). Any new recursive walker must take it too.
 */
internal const val MAX_NESTING_DEPTH = 32

private fun JsonElement.toInstructionSteps(depth: Int = 0): List<String> = if (depth >= MAX_NESTING_DEPTH) emptyList() else when (this) {
    is JsonArray -> flatMap { it.toInstructionSteps(depth + 1) }

    is JsonObject -> {
        if (typeNames().any { it.equals("HowToSection", ignoreCase = true) }) {
            this["itemListElement"]?.toInstructionSteps(depth + 1).orEmpty()
        } else {
            listOfNotNull(cleanHtmlText(stringOrNull("text") ?: stringOrNull("name")))
        }
    }

    is JsonPrimitive -> contentOrNull?.let(::splitInstructionText).orEmpty()
}
```

#### `recipe-scraper/src/commonMain/kotlin/com/tenmilelabs/recipescraper/microdata/MicrodataExtractor.kt` L29-80

```kotlin
internal fun extractMicrodataRecipe(document: Document, sourceUrl: String): ScrapedRecipe? {
    val scope = document.select("[itemscope][itemtype~=(?i)schema\\.org/Recipe]").firstOrNull() ?: return null
    val title = cleanHtmlText(scope.propertyValue("name")) ?: return null

    return ScrapedRecipe(
        title = title,
        description = cleanHtmlText(scope.propertyValue("description")),
        imageUrl = scope.propertyValue("image"),
        prepTimeMinutes = parseDurationToMinutes(scope.propertyValue("prepTime")),
        cookTimeMinutes = parseDurationToMinutes(scope.propertyValue("cookTime")),
        totalTimeMinutes = parseDurationToMinutes(scope.propertyValue("totalTime")),
        servings = firstIntOrNull(scope.propertyValue("recipeYield")),
        caloriesPerServing = scope.propertyValue("calories")
            ?.let { firstNutritionIntOrNull(it, MAX_PLAUSIBLE_CALORIES) },
        proteinGramsPerServing = scope.propertyValue("proteinContent")
            ?.let { firstNutritionIntOrNull(it, MAX_PLAUSIBLE_PROTEIN_GRAMS) },
        ingredients = scope.propertyValues("recipeIngredient")
            .mapNotNull(::cleanHtmlText)
            .map(::parseIngredient),
        instructions = scope.propertyValues("recipeInstructions").mapNotNull(::cleanHtmlText),
        keywords = scope.keywords(),
        author = cleanHtmlText(scope.propertyValue("author")),
        sourceUrl = sourceUrl,
        // Provisional: RecipeHtmlParser weighs this against the page's other signals.
        language = normalizeLanguageTag(scope.propertyValue("inLanguage"))
            ?.let { DetectedLanguage(it, LanguageConfidence.HIGH, LanguageSource.MICRODATA) },
    )
}

private fun Element.keywords(): List<String> {
    val all = propertyValues("keywords") + propertyValues("recipeCategory") + propertyValues("recipeCuisine")
    val split = all.flatMap { it.split(",") }.map { it.trim() }
    val seen = HashSet<String>()
    return split.filter { it.isNotBlank() && seen.add(it.lowercase()) }
}

/** The first `[itemprop=<name>]` value scoped to this element. */
private fun Element.propertyValue(name: String): String? = propertyValues(name).firstOrNull()

/** Every `[itemprop=<name>]` value scoped to this element, in document order. */
private fun Element.propertyValues(name: String): List<String> =
    select("[itemprop=$name]").mapNotNull { it.microdataValue() }

/**
 * `content` (for `<meta>`) → `datetime` (for `<time>`, where an ISO duration lives) → `src` (for
 * `<img>`) → `href` (for `<a>`/`<link>`) → else the element's own text.
 */
private fun Element.microdataValue(): String? =
    attrOrNull("content") ?: attrOrNull("datetime") ?: attrOrNull("src") ?: attrOrNull("href")
        ?: text().takeIf { it.isNotBlank() }

private fun Element.attrOrNull(key: String): String? = attr(key).takeIf { it.isNotBlank() }
```

### b.7 Mapping + persistence

#### `app/src/main/java/com/tenmilelabs/chefai/recipes/domain/usecase/SaveImportedDraft.kt` L20-29

```kotlin
class SaveImportedDraft @Inject constructor(
    private val recipeDraftDao: RecipeDraftDao,
    private val cacheRecipeImage: CacheRecipeImage,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(draft: RecipeDraft) = withContext(ioDispatcher) {
        val localImagePath = cacheRecipeImage(draft.recipeId, draft.imageUrl)
        recipeDraftDao.saveDraft(draft.copy(localImagePath = localImagePath).toRecipeDraftEntity())
    }
}
```

### Not included (size budget)
- `ScraperWebView.kt:83-118` (`applyScraperHardening`, summarised in §c), `:166-179` (`readRenderedHtml`: `evaluateJavascript("document.documentElement.outerHTML")`, JSON-decoded, dropped if >4 Mi chars).
- `recipes/data/network/WebViewImageFetcher.kt` (94 lines; sibling of `WebViewHtmlFetcher`, same hardening/client; `toImageBytesOrNull` only checks the `data:image/` prefix), `ScraperWebView.kt:16-69` (JS snapshot/image-fetch script + size constants `MAX_SNAPSHOT_CHARS=4 MiB`, `MAX_IMAGE_BYTES=5 MiB`).
- `recipes/data/network/HtmlCharset.kt` (48 lines: charset = Content-Type param, else regex-sniffed `<meta>` in first 4096 bytes, else UTF-8; unknown names fall through), `BrowserImportViewModel.handleSnapshot` (`:68-89`, passes each WebView snapshot to `importFromHtml(html, url)` then `SaveImportedDraft`), `ScrapedRecipeMapper.kt:43-83` (draft construction: `externalUrl = sourceUrl`, `imageUrl` resolved with `URI(sourceUrl).resolve`, privacy `PRIVATE`), `RecipeImageUploader.kt:56-147` (PUT of cached bytes to `API_BASE_URL/recipes/{id}/image`; contains a magic-byte content-type sniff).
- `recipe-scraper/.../jsonld/JsonLdRecipeMapper.kt:58-88,119-151` (keyword/author/image/yield/language helpers, all depth-capped by `MAX_NESTING_DEPTH`), `util/HtmlText.kt` (`cleanHtmlText`: `Ksoup.parse(raw).text()` when text contains `<` or `&`), `util/IsoDuration.kt`, `ingredient/IngredientTextParser.kt` (300 lines, regex-based), `language/*`.
- `recipes/data/mapper/ScrapedRecipeMapper.kt:85-117` (ingredient/step/tag mapping), `BrowserImportScreen.kt:196-250` (WebView host + polling loop; `ScraperWebViewClient` + `applyScraperHardening`, loads `state.url`), nav wiring in `AppDestinations.kt`/`ChefAINavGraph.kt:389-405`.
- `RecipeImageUploader.kt:101-147`, `RecipeImageStore.kt` remainder, sync DTOs/mappers (`SyncDtos.kt`, `SyncMapper.kt`), Room entities/DAOs, `RecipeImageBackfillWorker.kt`.
- Any backend code (not in repo).

## c. Config facts

| Item | Value (source) |
|---|---|
| HTTP engine | Ktor client `3.3.3` (`ktorVersion`, `libs.versions.toml:21`) with **OkHttp** engine (`ktor-client-okhttp`); OkHttp version is transitive, exact version not resolved here |
| Client | `@ScraperHttpClient`, singleton, `NetworkModule.kt:156`; no auth interceptor, no ContentNegotiation, no `HttpCookies` plugin |
| `expectSuccess` | `true` (non-2xx throws `ResponseException`; 3xx throws `RedirectResponseException`) |
| Redirects | `followRedirects = false`; followed manually, max 5 hops, each hop re-validated (`DefaultRecipeImporter.kt:42,146-162`) |
| Timeouts | request 15 s, connect 10 s, socket 10 s (`NetworkModule.kt:164-168`); WebView tier budget 8 s (`RENDER_BUDGET`) |
| Retry | `HttpRequestRetry`: `retryOnServerErrors(maxRetries = 2)`, exponential delay, `respectRetryAfterHeader = false` |
| Max response size | HTML: 3 MiB (`MAX_BODY_BYTES`, `ByteArray` of that size allocated per fetch; stream read via `bodyAsChannel`). Image: 5 MiB (`MAX_IMAGE_BYTES`; declared `Content-Length` pre-check + probe byte). WebView snapshot: 4 Mi chars |
| Compression | No Ktor `ContentEncoding` plugin installed. Decoding behaviour is whatever the OkHttp engine does by default (not verified in this repo) |
| User agent | Ktor tier: desktop Chrome 128 string (`NetworkModule.kt:36-38`), `Accept: text/html,application/xhtml+xml` (image fetch overrides to `image/avif,image/webp,image/*,*/*;q=0.8`). WebView tier: stock WebView UA minus `; wv` and `Version/x.y ` |
| Schemes | `http`, `https` only (`RecipeUrlValidation.kt:111-113`); WebView client refuses other schemes in `shouldOverrideUrlLoading` |
| Ports | No port check anywhere; URL `port` is never inspected |
| DNS | Pre-check: `InetAddress.getAllByName` (platform resolver). Connect-time (Ktor tier only): `SsrfSafeDns` wraps `Dns.SYSTEM`. WebView tier: `shouldInterceptRequest` resolves and checks, WebView then resolves again itself (ADR-010 Decision 7 states there is no pinning in that tier) |
| Blocked address classes | loopback, link-local, site-local, any-local, multicast, `0.0.0.0/8`, `100.64.0.0/10`, `198.18.0.0/15`, `255.x.x.x`, IPv6 `fc00::/7`; hostname `localhost` (`RecipeUrlValidation.kt:126-142`) |
| Proxy | No proxy configuration in the scraper client |
| Logging | Ktor `Logging` at INFO only when `BuildConfig.DEBUG`; Timber warnings log URLs |
| HTML parser | Ksoup `0.2.6` (`com.fleeksoft.ksoup:ksoup`, KMP port of Jsoup), `libs.versions.toml:19` |
| JSON | `kotlinx-serialization-json 1.9.0`, untyped `JsonElement` only, `Json { ignoreUnknownKeys = true; isLenient = true }` |
| Parser limits | Recursion depth `MAX_NESTING_DEPTH = 32` in JSON-LD walkers; no explicit limit on number of `ld+json` blocks, array lengths, or string lengths beyond the 3 MiB body cap. `RecipeHtmlParser.parse` catches `Exception` only (not `Error`) |
| Android | `minSdk 28`, `targetSdk 36`; release uses platform-default cleartext policy (no `usesCleartextTraffic` attribute); debug-only `network_security_config.xml` permits cleartext to `10.0.2.2`, `localhost`, `127.0.0.1` |
| WebView settings | JS on, DOM storage on, file/content access off, geolocation off, no popups, no `addJavascriptInterface` anywhere in the feature, process-wide `CookieManager` with third-party cookies accepted (`ScraperWebView.kt:83-118`) |
| Ktor plugins on scraper client | `HttpTimeout`, `HttpRequestRetry`, `Logging` (debug only), `defaultRequest` (headers). **Not installed:** Authentication/AuthInterceptor, RateLimit, CORS, ContentNegotiation, HttpCookies, ContentEncoding |
| Backend API base URL | Gradle prop `chefaiApiBaseUrl`, default `https://api.pocket-chef.app` (`app/build.gradle.kts:16-20`); only used by the authenticated client and `RecipeImageUploader`, never for the scraper |

## d. Identity

- Scraping calls no ChefAI endpoint, so the import step needs **no identity and no auth**; it runs for anonymous and signed-in users identically (client enforces nothing based on auth state).
- The scraper client structurally has no `AuthInterceptor` (ADR-010 Decision 4); `docs/claude/gotchas.md` also records that rule.
- Per-user/rate limits on import: none found in the Android code (only `ImportRecipeState.canImport`, a UI guard). No server-side limits exist for this step because there is no server step.
- Share-target entry (`ACTION_SEND text/plain`, activity `exported="true"`) accepts any text from any app; first `https?://\S+` match is used.
- Downstream (unread backend): per CLAUDE.md the backend uses anonymous-first auth with JWT; the image PUT and recipe sync go through the authenticated client. How the backend authorizes `PUT /recipes/{id}/image` and sync pushes was not read.

## e. Deployment

- No `Dockerfile`, compose file, or backend source is in this repo (`git ls-files` matches only archived prompt docs and Android files). Container networking, reachable internal services, secrets/env var names, and cloud-metadata exposure of the backend: **not determinable here**.
- The code path analysed executes on end-user devices; "internal network" there means the device's own network (Wi-Fi LAN, loopback, VPN/Tailscale ranges), which the blocklist in §c targets.
- Repo secrets/config names seen: Gradle property `chefaiApiBaseUrl`; `local.properties` exists (not read). No secrets were encountered or included.

## f. Existing tests touching this path

JVM unit tests (`app/src/test/...`):
- `recipes/data/repository/RecipeUrlValidationTest` — scheme add/keep/reject, blank, non-URL, `localhost` any case, IPv4 loopback, IPv6 loopback, 10/8, 192.168/16, `169.254.169.254`, IPv6 link-local, IPv6 ULA, decimal-integer loopback, IPv4-mapped IPv6 loopback, hostname resolving to blocked / partially blocked / unresolvable / public, relative redirect, redirect to blocked host, unparseable `Location`.
- `recipes/data/repository/DefaultRecipeImporterTest` (Ktor MockEngine) — JSON-LD import + catalog mapping, Content-Language, no recipe, 404, non-HTML content type, invalid/localhost/private/DNS-blocked URL, render-fallback decisions (403 → render, render fails → `NeedsBrowser`, no-recipe page, 404 not rendered), redirect to safe host followed, redirect to blocked host rejected, redirect-loop cap.
- `recipes/domain/usecase/CacheRecipeImageTest` — plain fetch, escalate on 403, rendered failure, no escalate on 404, non-image content type, blank URL, private-network URL, DNS-blocked URL, safe/blocked redirect, oversize body rejected.
- `recipes/data/network/HtmlCharsetTest` — header vs meta vs UTF-8 default, unknown charset, length bound.
- `core/util/ShareTextUrlExtractorTest` (exists; cases not read).
- `recipes/ui/urlimport/ImportRecipeViewModelTest`, `.../browser/BrowserImportViewModelTest`, `recipes/data/mapper/ScrapedRecipeMapperTest`, `domain/usecase/SaveImportedDraftTest` (exist; cases not read).
- `:recipe-scraper` (`commonTest`/`jvmTest`): `RecipeHtmlParserTest` (tier order, fall-through, `sourceUrl` taken from caller not page, language signals), `JsonLdExtractorTest` (shapes, malformed block skipped, nutrition bounds, depth cap at `MAX_NESTING_DEPTH`), `JsonLdDeepNestingTest` (6000-deep nesting, issue #339), `MicrodataExtractorTest`, plus ingredient/duration/language tests.
- Instrumented (`androidTest`): `WebViewHtmlFetcherTest`, `ImportRecipeE2ETest`, `RecipeImageStoreTest`.
- **No tests found for:** `SsrfSafeDns`, `ScraperWebViewClient.shouldInterceptRequest`, `applyScraperHardening`, the share-intent path in `MainActivity`, `WebViewImageFetcher`, `RecipeImageUploader`.

## g. Unknowns

- Backend repo (`jmuci/Chef.ai_Backend`) not available: no route, auth, rate-limit, validation or storage code for sync/image upload was read.
- Whether the backend ever fetches `externalUrl` or `imageUrl` server-side (e.g. thumbnails, link previews, the "cache-by-normalized-URL" idea in ADR-010) — unknown.
- How the backend validates the bytes of `PUT /recipes/{id}/image` and what it does with `recipeExternalUrl` / `imageUrl` strings — unknown.
- Backend Dockerfile/compose, network reach, env vars/secrets, cloud metadata exposure — unknown.
- Exact resolved OkHttp version and its default compression/redirect/TLS behaviour — not resolved.
- Whether the Android System WebView version in the field matters for the WebView tier (no minimum enforced in code that I found).
- `ShareTextUrlExtractorTest`, `ImportRecipeViewModelTest`, `BrowserImportViewModelTest` case lists not read.
- Whether the brief intended a different repo/branch containing a backend scraping route — unconfirmed.
