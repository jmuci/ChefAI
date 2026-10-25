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
    fun parse(html: String, sourceUrl: String, contentLanguage: String? = null): ScrapeResult
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

A tier counts as a hit only when it produces a non-blank title *and* at least one ingredient;
otherwise the next tier is tried, and if both miss, the result is
`ScrapeResult.NoRecipeFound`.

## Language detection

Every successful result carries `ScrapedRecipe.language`: a `DetectedLanguage` with an ISO 639-1
`code` (`"en"`, `"es"`), a `confidence` (`HIGH` / `LOW`) and the `source` it came from — or `null`
when nothing was strong enough to name one.

Markup signals are read most specific first: the recipe's own `inLanguage` (JSON-LD or microdata),
`<html lang>`, `og:locale`, `<meta http-equiv="content-language">`, then the optional
`contentLanguage` argument (the HTTP header). The highest-priority one is cross-checked against the
recipe's text, scored by counting common words from six language profiles (en, es, pt, fr, it, de):

| Markup                | Text                          | Result       |
|-----------------------|-------------------------------|--------------|
| agrees                | confident                     | markup, HIGH |
| absent                | confident                     | text, HIGH   |
| disagrees             | confident                     | text, LOW    |
| explicit `inLanguage` | not confident                 | markup, HIGH |
| page-level only       | not confident, leans same way | markup, HIGH |
| page-level only       | leans elsewhere, or no lean   | markup, LOW  |
| absent                | not confident                 | `null`       |

Text wins a disagreement because page-level markup is often a theme's untouched template default —
but only at `LOW`, which tells the caller to ask the user.

"Confident" means at least 8 profile-word hits and at least 2× the runner-up. Below that, the text
can still *lean* one way (a strict lead, no tie); a lean that matches page-level markup counts as
corroboration, so a three-line English recipe on an English site doesn't make the caller ask.

Ingredient lines are then re-parsed with the detected language. Unit words are recognised in
English and Spanish regardless of language (they never collide); only connector words are gated —
Spanish ranges (`"2 a 3"`), the `de` in `"200 g de harina"`, and whether a bare `c` means cup.

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
