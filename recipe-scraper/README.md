# :recipe-scraper

A pure-Kotlin library that extracts a structured recipe from a web page's HTML.

No Android, Hilt, Room, or Ktor dependencies — it takes an HTML string and returns a structured
recipe, doing **no network I/O**. The host app (or any other JVM/Kotlin consumer) owns fetching the
page; this module owns parsing it. See [ADR-010](../docs/adrs/adr-010-client-side-recipe-scraping.md)
for the reasoning behind the module boundary and the client-side-vs-backend decision.

## Why this exists

Recipe sites are SEO-driven, so they almost universally embed
[schema.org `Recipe`](https://schema.org/Recipe) structured data — JSON-LD or microdata — in static
HTML for Google's rich-snippet search results. That's enough to extract a usable recipe from most
sites without a headless browser or JS execution.

A minority of sites won't hand that HTML to a plain HTTP client at all — bot protection scoring TLS
and IP fingerprints, or markup only assembled once the page's own scripts have run. That's a
*fetching* problem, not a parsing one: `:app` retries those through a `WebView` and feeds the
rendered DOM back into this same parser. See
[ADR-010 Decision 5](../docs/adrs/adr-010-client-side-recipe-scraping.md).

## Public API

```kotlin
class RecipeHtmlParser {
    fun parse(html: String, sourceUrl: String): ScrapeResult
}

sealed interface ScrapeResult {
    data class Success(val recipe: ScrapedRecipe, val source: ExtractionSource) : ScrapeResult
    data object NoRecipeFound : ScrapeResult
    data class ParseError(val message: String) : ScrapeResult
}
```

`parse` never throws — any unexpected failure inside the HTML or JSON parsers is caught and reported
as `ScrapeResult.ParseError` instead. `sourceUrl` is recorded on the result as-is; it is never read
from the page itself.

`ScrapedRecipe` fields are nullable or empty wherever a page may legitimately omit them — this
library reports what the page actually published and never fabricates or defaults a value. Choosing
fallbacks (e.g. treating a missing prep time as zero) is the caller's job.

## Extraction tiers

Two generic tiers, tried in order — no per-site selectors:

1. **JSON-LD** (`<script type="application/ld+json">`) — the more common format and the more
   reliably structured one.
2. **Microdata** (`itemscope`/`itemprop` attributes) — fallback for sites that don't publish JSON-LD.

A tier counts as a hit only when it produces a non-blank title *and* at least one ingredient or
instruction; otherwise the next tier is tried, and if both miss, the result is
`ScrapeResult.NoRecipeFound`.

## Using it

```kotlin
val parser = RecipeHtmlParser()
when (val result = parser.parse(html, "https://example.com/recipe")) {
    is ScrapeResult.Success -> result.recipe // title, ingredients, instructions, ...
    ScrapeResult.NoRecipeFound -> // no usable schema.org Recipe markup on the page
    is ScrapeResult.ParseError -> // unexpected failure; result.message describes it
}
```

Fetching the HTML (an `HttpClient` GET, URL validation, response size caps, etc.) is the caller's
responsibility — in this repo, that's `com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter`
in `:app`.

## Testing

All tests run on plain JVM (`kotlin.test`, no Robolectric or instrumentation). Fixtures are
hand-authored minimal HTML as Kotlin `const val` strings in `commonTest` — not saved copies of real
sites, and not loaded from `commonTest/resources` (resource loading is awkward in KMP; inline strings
are simpler and just as effective for these small fixtures).

```bash
./gradlew :recipe-scraper:jvmTest
```
