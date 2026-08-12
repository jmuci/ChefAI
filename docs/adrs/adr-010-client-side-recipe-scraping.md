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
- `RecipeDraft.toRecipe()` still throws `NumberFormatException` on a blank numeric string, a
  pre-existing contract the mapper works around by emitting `"0"` rather than `""` for absent
  numerics — see `docs/claude/gotchas.md`. Hardening that contract (`toIntOrNull() ?: 0`) is a good
  separate change, kept out of this diff since it touches a documented, tested path used by the
  editor's save flow.
