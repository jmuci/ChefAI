# ADR 010 – Client-Side Recipe URL Scraping

**Date:** August 2026
**Status:** Accepted
**Author:** Claude (Sonnet 5), on behalf of Jose Mucientes

---

## Context

The only way into Pocket Chef's recipe library was typing a recipe by hand into the unified editor.
The FAB menu on the Recipes tab shipped an "Import recipe" item that did nothing. This ADR covers
the design behind filling that gap: paste a recipe URL, get a pre-filled editor.

Recipe sites are SEO-driven, so they almost universally embed schema.org `Recipe` structured data
(JSON-LD or microdata) in static HTML for Google's rich-snippet search results. That means no
headless browser or JS execution is needed — an HTTP GET plus an HTML parse is enough to extract a
usable recipe from the large majority of sites.

---

## Decision 1: Client-side scraping, not a backend endpoint

The scrape (fetch + parse) happens **on-device**, inside the Android app, rather than as a backend
endpoint the app calls.

**Why:**
- Zero server compute cost, and zero new backend surface to build, deploy, and operate.
- Requests to third-party sites are spread across users' own IPs rather than concentrated on one
  backend IP that's trivially rate-limited or blocked by recipe sites.
- No backend release is needed to ship the feature.

**Tradeoff accepted:** a broken parser (a recipe site changes its markup) requires an app release to
fix, not a backend deploy. There is also no cross-user cache — if 1,000 users import the same URL,
it's fetched and parsed 1,000 times. If scraping volume ever justifies it, a cache-by-normalized-URL
layer on the existing Ktor backend is a **later, additive** optimization — not a capability given up
by this decision, since the client-side extraction logic would be reused as-is server-side too (see
Decision 2).

---

## Decision 2: A separate `:recipe-scraper` Gradle module, pure Kotlin

HTML-string-in, structured-recipe-out is a genuinely self-contained problem with no need for
Android, Room, Hilt, or Ktor. It lives in its own module rather than a package inside `:app`:

- The "no Android dependencies" boundary is **compiler-enforced**, not just a convention respected
  by discipline.
- Its tests run on plain JVM — no Robolectric, no instrumented tests, no emulator needed for the
  extraction logic itself.
- "Publish this as a library" becomes a `maven-publish` block, not a refactor. There is no comparable
  Kotlin/JVM recipe scraper published today — the mature options
  ([recipe-scrapers](https://github.com/hhursev/recipe-scrapers)) are Python; the JS ecosystem's
  options lean on headless Chromium.
- It's built as Kotlin Multiplatform (`jvm()`-only target for now, all code in `commonMain`) so
  iOS/JS targets can be added later without moving files, even though only JVM is used today.

The module does **no network I/O** — `RecipeHtmlParser.parse(html, sourceUrl)` takes a string and
returns a `ScrapeResult`. `:app` owns fetching (see Decision 4), mirroring how the Python
`recipe-scrapers` library separates fetching from parsing: it keeps the library dependency-light,
testable purely from HTML fixtures, and reusable from a future backend cache layer without dragging
Ktor client internals into it.

---

## Decision 3: JSON-LD first, microdata fallback, no per-site scrapers in v1

Two generic extraction tiers, tried in order:

1. **JSON-LD** (`<script type="application/ld+json">`) — the more common format and the more
   reliably structured one (typed fields, less markup-mixed-with-data than microdata).
2. **Microdata** (`itemscope`/`itemprop` attributes) — second-tier fallback for older or
   differently-built sites.

A tier only counts as a hit when it produces a non-blank title **and** at least one ingredient or
instruction — a schema.org block with only a name is not a usable recipe, and falls through to the
next tier or to `ScrapeResult.NoRecipeFound`.

**No per-site selectors in v1.** Both tiers are schema.org-generic; nothing is hardcoded to a
specific site's HTML structure. If misses turn out to be common in real usage, the extensible next
step is a `SiteScraper` registry keyed by hostname as a third tier — the tiered design in
`RecipeHtmlParser` leaves room for this without a redesign.

**Ksoup, not Jsoup**, for HTML parsing (`com.fleeksoft.ksoup:ksoup`) — a Kotlin Multiplatform port of
Jsoup's API, chosen specifically so the module's only non-Kotlin-stdlib dependency stays
KMP-compatible, keeping the door open for non-JVM targets.

---

## Decision 4: Fetch/DI split — a second, unauthenticated Ktor `HttpClient`

The single existing `HttpClient` (`core/di/NetworkModule.kt`) installs `AuthInterceptor` and runs
responses through `ContentNegotiation`. Pointing that client at a third-party site would leak ChefAI
auth tokens to whatever host the user pasted, and run untrusted third-party HTML through JSON content
negotiation. A second client, qualified `@ScraperHttpClient`, was added instead:

- No `AuthInterceptor` — the entire point.
- No `ContentNegotiation` — the raw HTML body is wanted as text, not deserialized.
- `HttpTimeout` (15s request / 10s connect / 10s socket) and `HttpRequestRetry` (2 retries,
  exponential backoff on server errors) — a user-facing paste-a-URL action needs to fail fast and
  predictably, unlike background sync.
- A normal desktop `User-Agent` — many sites serve degraded or no HTML to unrecognized clients.
- `Logging` at `INFO`, not `HEADERS` — scraped-page response headers are noise and may carry
  third-party cookies worth not logging.

`DefaultRecipeImporter` (the fetch → parse → map orchestrator) additionally: rejects non-`http(s)`
schemes and loopback/private-network hosts before ever making a request (the URL is arbitrary user
paste — there's no reason the app should fetch `localhost` or RFC1918 addresses); caps the read body
at ~3MB via a bounded channel read, which is robust to a missing or wrong `Content-Length` header
rather than trusting the header outright; and rejects non-HTML `Content-Type` responses before
handing the body to the parser.

---

## Decision 5: A rendered-DOM fallback for sites that refuse HTTP clients

**Added August 2026, amending Decision 1.**

Decision 1 claimed "no headless browser or JS execution is needed — an HTTP GET plus an HTML parse
is enough". That holds for the large majority of recipe sites, and stays the default. It does not
hold for all of them. Measured against three reported failures:

| Site | Response to the Ktor client | Cause |
|---|---|---|
| `foodnetwork.com` | 403, a 426-byte Akamai "Access Denied" page | Akamai Bot Manager scoring TLS/IP fingerprints. Sending a full set of real Chrome headers does not help. |
| `seriouseats.com` | 403, a 680 KB JS interstitial | Cloudflare Turnstile — an **interactive** check that does not resolve on its own. |
| `directoalpaladar.com` | 200, parses correctly | Not a scraping failure at all; see the ingredient-parser note below. |

Both blocked pages carry perfectly ordinary schema.org `Recipe` JSON-LD once loaded in a real
browser. The extraction tier was never the problem — the fetch tier is.

Two tiers were added below the HTTP fetch, both reusing `RecipeHtmlParser` unchanged:

1. **Off-screen `WebView`** (`WebViewHtmlFetcher`, behind the `RenderedHtmlFetcher` port). A real
   browser engine on the user's own connection, unattached to any window, polling
   `document.documentElement.outerHTML` every 500ms for up to 8s and stopping at the first snapshot
   the parser accepts. This clears Food Network with no UI at all, and also rescues sites whose
   markup only exists after their own scripts run.
2. **Visible in-app browser** (`BrowserImportScreen`). For an interactive check, the user clears it
   themselves — the same thing they would do in Chrome. Nothing here attempts to defeat a CAPTCHA;
   the screen just keeps polling and gets out of the way the moment a recipe appears.

**Escalation is narrow on purpose.** Only a `401`/`403`/`429`/`503` — a server *refusing* us —
escalates to the visible browser. A `404`, a DNS failure or a timeout returns immediately: a
browser would fail the same way, eight seconds later. A page that loaded fine but simply has no
recipe on it gets the rendered retry (for the JS-rendered case) but never sends the user to a
browser, because there would be nothing there for them to do.

**Why not a backend endpoint.** Delegating the fetch to the Ktor backend was the obvious
alternative and is *worse* for exactly the two sites that motivated this: it concentrates requests
on one datacenter IP, which Akamai and Cloudflare score far more harshly than the residential
mobile IPs Decision 1 deliberately chose. A backend endpoint would need its own headless browser or
a paid unblocking service to beat what a `WebView` on the device does for free. The
cache-by-normalized-URL layer Decision 1 anticipated remains the right *additive* next step, and is
unaffected by this.

**Cost accepted.** This runs a third party's JavaScript in our process. Both WebViews share one
hardening config (`applyScraperHardening`): no file or content-provider access, no cross-origin
escape from `file://`, no geolocation, no autoplay, no window popups, and a `WebViewClient` that
refuses any non-`http(s)` navigation. There is deliberately **no `addJavascriptInterface`** anywhere
in this feature — it would hand the page a bridge into app code, and nothing here needs one. The
stock WebView user agent is left alone rather than reusing Decision 4's desktop string: pairing a
Windows Chrome UA with Android's TLS and JS fingerprint is the exact mismatch bot detection looks
for. Cookies are left to the process-wide `CookieManager` on purpose, so a clearance cookie earned
on the visible screen carries over and the next import of that site usually resolves off-screen.

Two parser bugs surfaced by the same investigation were fixed alongside it:

- **Fused metric amounts.** `"300g Garbanzos"` parsed to a null quantity *and* null unit, so
  `ScrapedRecipeMapper` substituted `1.0`/`"unit"` and the editor showed *"1 unit of 300g
  Garbanzos"* — 8 of the 14 ingredients on the cocido madrileño page. `IngredientTextParser` now
  splits a token that fuses the two, but only when the suffix normalises to a known unit, so pan
  sizes (`"9x13"`) and multipliers (`"1x"`) are untouched.
- **Response charset.** The body was decoded as UTF-8 unconditionally, turning every accented
  character on an ISO-8859-1 site into a replacement glyph. `decodeHtml` now honours the
  `Content-Type` charset, falls back to a sniffed `<meta>` declaration, and only then to UTF-8.

---

## Decision 6: Caching imported images on-device

**Added August 2026, amending Decision 1.**

Recipes imported from the two sites Decision 5 measured saved with a correct `imageUrl` but
rendered nothing. Coil's plain `OkHttpNetworkFetcherFactory` is refused by the image CDNs exactly
the way the plain Ktor client was refused before Decision 5 — the same bot-wall class, one layer
further down:

| Probe against `food.fnr.sndimg.com/…QK0103_Grilled-Peach-Crumble…webp` | Result |
|---|---|
| `okhttp/4.12.0` UA | 403 |
| No headers | 403 |
| Full Chrome header set — UA, Accept, Accept-Language, Referer, all four `Sec-Fetch-*`, `sec-ch-ua*` | 403 |
| Desktop Chrome UA + Referer | 403 |
| Forced HTTP/1.1 (different ALPN) | 403 |
| Base image URL, no `.rend` transform; smaller `.rend` variant | 403 |
| A control site (`budgetbytes.com`) with a plain `okhttp/4.12.0` UA | 200 |

The block page is Akamai's (`errors.edgesuite.net`, `server-timing: ak_p`) — headers cannot fix
this, it is the same TLS/IP fingerprinting Decision 5 documented for the page itself.

**The fix mirrors Decision 5's ladder one tier lower.** `CacheRecipeImage` tries a plain GET through
the existing `@ScraperHttpClient` first, escalating to an off-screen `WebView` only on the same
`BOT_WALL_STATUSES` (401/403/429/503) Decision 5 already escalates on. On success, the bytes are
written to `filesDir/recipe_images/<recipeId>` (`RecipeImageStore`) and the path is recorded on a
new `localImagePath: String?` column — added on `recipes` and `recipe_drafts` via `MIGRATION_1_2`,
the first real migration since #119 collapsed the schema to v1. `imageUrl` keeps the real remote
URL; the UI prefers `localImagePath` when present and falls back to `imageUrl` otherwise (see
`recipeImageModel`), so a recipe whose image can't be cached degrades to exactly today's hotlink
behaviour rather than losing the image reference entirely.

**Why not the in-page `fetch()` the issue first proposed.** The obvious shape — reuse the WebView
already loading the page, `fetch()` the image from inside it — doesn't hold for the site that
motivated this: Food Network serves images from `food.fnr.sndimg.com`, a different origin than
`www.foodnetwork.com`, so that fetch would be cross-origin and CORS-blocked. (Serious Eats happens
to be same-origin, which is likely why the shape looked general.) `WebViewImageFetcher` instead
navigates a **fresh** off-screen WebView directly to the image URL — Chromium loads it as a
synthetic image document whose origin *is* the image's own origin, so an in-page
`fetch(location.href, {cache: 'force-cache'})` is same-origin and reuses the bytes the navigation
itself already downloaded. The result is handed back through the same `evaluateJavascript` + poll
pattern as `readRenderedHtml`, stashed on a `window` global between polls since `fetch` is async and
`evaluateJavascript` is not — still with **no `addJavascriptInterface`**, unchanged from Decision 5.

**Why not a backend proxy.** Rejected for the same reason Decision 5 gives: a datacenter IP is
scored *harder* than a residential mobile IP by exactly these two bot managers, so a backend fetch
would fail where the on-device WebView succeeds. Backend involvement is not rejected forever,
though — see the sync question below.

**Local-only, not synced — for now.** `localImagePath` is a device path and is deliberately absent
from `SyncRecipeDto`: a `file://…` string has no meaning on the backend or on another device, and
shipping it would leak a local filesystem path into the sync payload. This means a second device
still hits the same bot wall on pull and has to re-run its own WebView tier — an accepted gap, not
an oversight. Whether and how to synchronize cached image bytes across devices (upload the bytes?
re-derive per device? only for user-picked photos, which have no source URL to re-derive from?) is
tracked separately as [issue #132](https://github.com/jmuci/ChefAI/issues/132), deliberately not
decided here.

**Cost accepted.** One more GET (or WebView round-trip) per import, and up to 5MB of on-device
storage per cached image (`MAX_IMAGE_BYTES`), excluded from Auto Backup / cloud device transfer
since the bytes are trivially re-downloadable. No sweeper reclaims an orphaned file left behind by
an import that's never saved past the draft stage — matching the pre-existing behaviour for
abandoned draft rows themselves.

A second, adjacent bug surfaced by the same investigation was fixed alongside it:

- **Unresolved relative image URLs.** `ScrapedRecipe.imageUrl`'s own KDoc flagged that the value
  "may be relative to `sourceUrl`", but nothing ever resolved it — `RecipeHtmlParser` parses without
  a base URI, and the mapper stored the literal scraped string. A microdata page with
  `<img itemprop="image" src="/img/x.jpg">` saved the literal `/img/x.jpg`, which fails in Coil and
  in `CacheRecipeImage` alike, regardless of any CDN protection. Fixed in `:app` — `ScrapedRecipeMapper`
  now resolves `imageUrl` against `sourceUrl` with `java.net.URI.resolve`, falling back to the raw
  value if that throws — not in `:recipe-scraper`, whose README documents that it "reports what the
  page actually published" and leaves resolution to the caller.

---

## Consequences

**Positive:**
- The integration into the existing editor is minimal: a scraped recipe becomes a `RecipeDraftEntity`
  seeded under a new UUID, and the editor's existing `restoreDraftIfExists` / `populateFromDraft`
  path (already built for auto-save/process-death recovery) picks it up with **no new
  `EditorAction`, no reducer change**. Save takes the normal `createRecipe` + `addBookmark` path.
- `:recipe-scraper` is independently testable and, per Decision 2, potentially independently
  publishable later with no rearchitecting.
- The auth-leak risk (Decision 4) is structurally prevented at the DI level, not just by code review
  discipline — there is no code path where the scraper flow can obtain the authenticated client.

**Negative / accepted tradeoffs:**
- Per-user, per-request scraping cost (no shared cache) — see Decision 1.
- No per-site scrapers — some sites with non-schema.org markup will simply fail to import in v1 and
  fall back to `NoRecipeFound` with a manual-entry offramp.
- A URL that loads fine but isn't a recipe now costs the extra 8s render budget (Decision 5) before
  reporting `NoRecipeFound`, up from roughly a second. `RENDER_BUDGET` is a single constant; tune it
  once there is real usage to tune against.
- `RecipeDraft.toRecipe()` still throws `NumberFormatException` on a blank numeric string, a
  pre-existing contract the mapper works around by emitting `"0"` rather than `""` for absent
  numerics — see `docs/claude/gotchas.md`. Hardening that contract (`toIntOrNull() ?: 0`) is a good
  separate change, kept out of this diff since it touches a documented, tested path used by the
  editor's save flow.
- Cached images (Decision 6) are per-device: a second device re-hits the same CDN wall on pull and
  has to re-run its own WebView tier, and a user-picked local photo can't be synced at all since it
  never had a source URL. Tracked in [issue #132](https://github.com/jmuci/ChefAI/issues/132), not
  solved by this ADR.
