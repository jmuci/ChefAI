# QA findings — SCRAPER (recipe-scraper + URL import), 2026-11-09

Probes ran on the JVM only; nothing was run on a device. Anything depending on Android's resolver, ART
stack size, Coil fetchers or the release network policy is marked SUSPECTED. The SSRF guard held up for
the HTTP tier. Counts: 2 × S2, 17 × S3, 8 × S4.

## S2

### SCRAPER-1: StackOverflowError escapes `RecipeHtmlParser.parse`
- Status: CONFIRMED on JVM; device crash SUSPECTED
- Steps: JSON-LD `image`, `author`, `recipeYield`, `recipeCategory`, `keywords` or `inLanguage` nested ~6000 arrays deep (~12KB page); parse on a 0.5–1.6MB-stack thread.
- Expected: README's "never throws" contract holds.
- Actual: StackOverflowError. `catch (e: Exception)` misses it; `DefaultRecipeImporter` and the ViewModel have no handler. Window is roughly depth 5000–12000 and moves with JIT state.
- Suspected cause: `JsonLdRecipeMapper.kt` — `firstImageUrlOrNull`, `yieldStrings`, `firstAuthorNameOrNull`, `commaOrArraySeparated`, `languageTagOrNull` recurse with no depth cap (only `toIngredients`/`toInstructionSteps` use `MAX_NESTING_DEPTH`).

### SCRAPER-2: Non-finite ingredient quantity crashes the draft save
- Status: throw CONFIRMED; app crash inferred from code
- Steps: `recipeIngredient: ["1e999 g flour"]` (or "Infinity g flour", "NaN cups sugar").
- Actual: quantity is Infinity/NaN; `toRecipeDraftEntity()` throws `JsonEncodingException` at `DraftMapper.kt:153`, inside `SaveImportedDraft`, which `ImportRecipeViewModel.import` calls without try/catch.
- Suspected cause: `parseSingleNumber` uses `toDoubleOrNull()`, which also accepts "1d flour" → 1.0, "2f" → 2.0, "1e2 g" → 100. Needs `isFinite()` + digits-only validation.

## S3

- **SCRAPER-3 — repeated ingredient loses its second amount** (CONFIRMED). `["2 tbsp butter","1 tbsp butter"]` keeps only the first; "1 cup sugar" then "sugar" drops the second. `ScrapedRecipeMapper.toRecipeIngredients` dedupes by name (PK recipeId+ingredientId) without merging quantities.
- **SCRAPER-4 — section headings become ingredients** (CONFIRMED). "FOR THE SAUCE:", "For the dough", "Special equipment: …" import with quantity 0, unit "unit", become catalog ingredients and show on shopping lists.
- **SCRAPER-5 — dual-unit lines corrupt name/unit** (CONFIRMED). "100 g / 3.5 oz butter" → name `/ 3.5 oz butter`; "50 g/2 oz butter" → unit null, name `g/2 oz butter`; "1 tbsp/15ml oil" → name `tbsp/15ml oil`; "400g/14oz tomatoes" → quantity null. `IngredientTextParser` handles parentheses but not `/` alternates.
- **SCRAPER-6 — ranges and joiners leave junk in the name** (CONFIRMED). "1 to 1 1/2 cups water" → `1/2 cups water`; "1 1/2-2 cups flour" → `1/2-2 cups flour`. Also "and 1/2", "1 x 400g tin", "plus 1 teaspoon", "+ 2 tbsp", "1 to 2 inch piece", "2 fl oz".
- **SCRAPER-7 — "1,000 g flour" parses as 1 g** (CONFIRMED). `parseSingleNumber` always treats comma as decimal; `firstNutritionIntOrNull` already has the thousands rule.
- **SCRAPER-8 — blank JSON-LD `image` becomes the page URL** (CONFIRMED). `"image":""`, `["","https://x/i.jpg"]`, `[{"url":""},{…}]` return "" and never reach the valid entry; `ScrapedRecipeMapper.resolvedImageUrl` then does `URI(sourceUrl).resolve("")`, so `draft.imageUrl` is the HTML page. Cause: `firstImageUrlOrNull` doesn't reject blanks.
- **SCRAPER-9 — any scheme reaches `draft.imageUrl`** (passthrough CONFIRMED; impact SUSPECTED). `file:`, `content:`, `android.resource:`, `javascript:`, `data:`, `http://192.168.x` pass the mapper unchanged, sync to the backend, and Coil loads them (`recipeImageModel`) without the SSRF guard → blind GET to a LAN host from a crafted page. Microdata lazy-load placeholders also end up as `data:` URIs. Fix: keep only http(s) and run `isSafeHost`.
- **SCRAPER-10 — zero-byte/non-image bytes get cached and beat the working remote URL** (CONFIRMED via MockEngine in `CacheRecipeImage`; UI effect SUSPECTED). 200 `image/jpeg` with empty body, `image/png` containing HTML, or empty WebView result all call `store.write` and set `localImagePath`; `recipeImageModel` prefers the local file. Also `image/svg+xml` is cached while `application/octet-stream`/no Content-Type images are never cached.
- **SCRAPER-11 — HTML-blob / microdata `recipeInstructions` collapses to one step** (CONFIRMED). `<ol><li>`, `<p>…</p><p>`, `<br>` all become one step (`cleanHtmlText` flattens markup before splitting); microdata `<div itemprop=recipeInstructions><ol>` too; "Step 1: … Step 2: …" stays one step.
- **SCRAPER-12 — microdata not scope-aware** (mechanism CONFIRMED on synthetic fixture; real-site frequency unknown). A Person scope with `itemprop=name` before the recipe title makes the title "Jane" (same for image/description); `itemprop="name headline"` not matched → NoRecipeFound; `<a itemprop=author href>` yields the href; HowToStep gives "Step 1Mix.". Cause: `MicrodataExtractor.propertyValues` selects over all descendants.
- **SCRAPER-13 — browser-valid JSON-LD gives NoRecipeFound + 8s WebView retry** (behaviour CONFIRMED; prevalence SUSPECTED). Inputs: JSON wrapped in `<!-- -->` or `//<![CDATA[`; leading BOM; `/* */` comments; trailing commas; script `type="application/ld+json; charset=utf-8"`; `@type` as `https://schema.org/Recipe` or `schema:Recipe`. (Raw newlines in strings, uppercase `type=`, `<\/script>` work.)
- **SCRAPER-14 — by-reference / alternate shapes lose data.** Yoast `image:{"@id":…}` and `author:{"@id":…}` → null; ImageObject with only `contentUrl` → null; `HowToStep` with nested `itemListElement` keeps only step name; `HowToSection` names dropped.
- **SCRAPER-15 — FR/DE/IT/PT ingredients lose unit and keep connectors** (CONFIRMED, incl. live Marmiton). "300 g de farine" → name `de farine`; "3 cuillères à soupe de sucre" → unit null; "60 cl de lait" → name `cl de lait`; "2 EL Öl" → name `EL Öl`. Documented limit in ADR-016, but the editor offers those languages. Language detection itself was right on every fixture.
- **SCRAPER-16 — valid URLs rejected as invalid** (CONFIRMED at `normalizeAndValidateUrl`). IDN hosts (`https://münchen.de/…`, `例え.jp`); URLs containing `|`, space, `{}`, `^`, `"`; redirect `Location` with those chars rejected as "disallowed address". Cause: strict `java.net.URI`; use OkHttp `HttpUrl` or Ktor `Url`.
- **SCRAPER-17 — cookie-gated redirect loops fail with no WebView fallback** (SUSPECTED; MockEngine only). No cookie jar → "Too many redirects", not marked `looksLikeBotWall`, user sees "check your connection".
- **SCRAPER-18 — `http://` links probably fail in release builds** (SUSPECTED, read-only). Only `src/debug` permits cleartext; importer never tries https first or upgrades http.
- **SCRAPER-19 — no field-length caps** (SUSPECTED). Title 3,000 / description 200,000 / step 100,000 / ingredient 5,000 chars pass unchanged. Drafts store steps+ingredients as JSON in one `recipe_drafts` row; a page near the 3MB fetch cap may exceed the 2MB CursorWindow when the editor reads it. Check: save a draft with ~2.5MB of steps on a device and reopen the editor.

## S4

- **SCRAPER-20** — single-blob step splitter deletes numbers: "Bake for 25. Let cool." → "Bake for" + "Let cool." (`NUMBERED_MARKER` = `\s\d{1,2}[.)]\s+`).
- **SCRAPER-21** — `cleanHtmlText` drops text inside `<…>`: "Soup <spicy>" → "Soup"; "Mix a <b and c" → "Mix a". No injection path (script/img/iframe content never kept; no `fromHtml`).
- **SCRAPER-22** — messy numbers pick wrong values. Nutrition "1 serving (270 calories)" → 1, "-5" → 5; durations `PT10M-PT15M` → 25, `PT1H30` → 60, `PT99999999999H` → 2147483647; servings "2 dozen" → 2; ingredient "-1 cup" → -1.0.
- **SCRAPER-23** — thin space, narrow nbsp, zero-width space and fraction slash `⁄` break ingredient parsing (only U+00A0 normalised); a title of only U+200B passes the non-blank check; `recipeIngredient` as one newline-separated string becomes one ingredient.
- **SCRAPER-24** — SSRF hardening gaps (no HTTP-tier bypass found). `normalizeAndValidateUrl` accepts `[::127.0.0.1]`, `[64:ff9b::7f00:1]`, `[64:ff9b::a9fe:a9fe]`, `[2002:7f00:1::]`, `192.0.0.192`, `240.0.0.1`. `0177.0.0.1` accepted on JVM but is octal 127.0.0.1 on Android/Chrome (untested on Android). OkHttp skips `Dns` for IP literals, so only pre-validation protects literals. `normalizeUrlForDisplay` pre-filter lets `[::1]`, `0.0.0.0`, `100.64.x`, decimal/hex/octal IPs through to the import screen (documented as weak).
- **SCRAPER-25** — every `NetworkError` shows "Couldn't reach that site – check your connection" (`ImportRecipeViewModel.kt:88`): 404/410, missing/non-HTML Content-Type, 500, rejected redirect, hop limit. None offers manual entry.
- **SCRAPER-26** — share extraction (`Regex("https?://\S+")`) returns null for `<https://…>`, `HTTP://…`, bare `www.…`; keeps trailing `”`; only tries the first URL.
- **SCRAPER-27** — other limits: ingredient names keep prep notes ("ripe bananas, mashed"); parenthesised sizes dropped; body silently cut at 3MB (recipe after 3.2MB padding → NoRecipeFound + 8s retry); ADR-010 Decision 3 says "ingredient or instruction" but code requires an ingredient; `:99999` ports pass the validator then fail; `.local` hostnames stall ~5s; retry on 503 delays WebView escalation ~3–4s.

## Coverage
- Parser: ~250 JSON-LD/microdata fixtures (structure, yield, duration, image, nutrition, keyword, instruction, injection).
- Ingredient parsing: ~170 lines in en/es/fr/de/it/pt. Language detection: 20+ cases.
- URL guard: ~180 URL strings. Redirect validation and importer via MockEngine (content types, charsets, status codes, 10MB bodies, redirect loops). Mapper-to-draft and `CacheRecipeImage` scenarios.
- Live fetches (10): only BBC Good Food and Marmiton returned HTML; BBC parsed correctly.
- URI/Ktor/OkHttp host differential found no HTTP-tier SSRF bypass.

## Not covered
- No device: WebView tiers, Android resolver, Coil handling of `file:`/`content:` URLs, ART stack depth, release cleartext policy, CursorWindow limit.
- DNS-rebinding timing.
- AllRecipes, Serious Eats, Food Network, Simply Recipes, Cookie+Kate, Budget Bytes, Chef Koch returned 403 to curl; NYT/Food Network/Serious Eats patterns were fixtures only. No live microdata-only site.
