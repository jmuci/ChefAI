# QA findings — LOGIC (domain logic probing), 2026-11-09

Baseline: `./gradlew :app:testDebugUnitTest` = 1478 tests, 0 failed, 0 skipped (HEAD 36bb2b1a).
Counts: 1 × S1, 2 × S2, 12 × S3, 8 × S4. All CONFIRMED by a probe or exact code line except LOGIC-7, 16, 23
(SUSPECTED). LOGIC-1/2 UI triggers and the remote vector were not run on a device. Probe tests were deleted.
Note: this worktree predates `LogoutUseCase` (commit df817892, #334 on `main`), so the logout push path was not covered.

## S1 / S2

### LOGIC-1 (S1): ingredient quantity ≥ ~1.8e306 throws `NumberFormatException: Infinite or NaN`
- `QuantityFormat.decimal` (QuantityFormat.kt:23-31) checks `isFinite()` on the input, but `value*100` overflows to Infinity and reaches `BigDecimal(Infinity)`.
- Hits `IngredientAmountFormatter` (recipe details) and `ShoppingListBuilder`. The recipe editor accepts a pasted "1e307" (RecipeEditorReducer.kt:111), the value persists, and the screens crash every time they open. A synced public recipe with such a value is a possible remote crash (server validation not checked).
- Fuzz: 841 of 20,000 random lists with such rows threw.

### LOGIC-2 (S2): NaN/Infinity ingredient quantity breaks every sync
- Pushing it throws `JsonEncodingException` (NetworkModule Json has no special-float support). The recipe stays PENDING, so each push fails before the pull runs.
- Gets in via the editor (pasted "Infinity"/"NaN") and the scraper (q=NaN for "NaN g flour"; see SCRAPER-2).

### LOGIC-3 (S2): session comes back after logout when a token refresh is in flight
- Repro: start `refreshToken()`, call `logout()`, let the refresh complete → session flips back to Authenticated and tokens are re-written to secure prefs.
- Cause: `refreshTokenLocked` (SessionManager.kt:543-585, write at 579) re-writes state from a stale `currentSession`.

## S3

- **LOGIC-4** — diet matching is a substring test (MealPlanScheduler.kt:147-151): "Non-Vegetarian", "Non-Vegan", "not gluten-free" tags satisfy the restriction and get scheduled; if nothing matches the restriction is silently dropped.
- **LOGIC-5** — account upgrade overwrites the account's star/go-to marks with the anonymous plain bookmark (`INSERT OR REPLACE`, BookmarkedRecipeDao.kt:83-86). Confirmed against real in-memory Room.
- **LOGIC-6** — pull handlers inconsistent about LWW/local state: a server recipe tombstone overwrites a newer local PENDING edit; bookmark pulls ignore local state (a tombstone clears a PENDING star; a local DELETED bookmark is resurrected); a locally DELETED meal plan is resurrected when its push returned an error.
- **LOGIC-7 (SUSPECTED)** — interrupted login wipes anonymous data: on restore `handleLogin` is called with `anonymousUserId = null` (SessionManager.kt:135), taking the `clearAllTables` branch. Branch confirmed with a mock DB, not a real process death.
- **LOGIC-8** — shopping list doesn't merge egg/eggs, tomato/tomatoes, onion/onions, or flour with all-purpose flour; a name containing U+00A0 doesn't merge with the same name using a normal space.
- **LOGIC-9** — tiny amounts render as "0 oz"/"0 g" (IMPERIAL, 1 g saffron at 10% = 0.0035 oz → "0 oz"), contradicting ADR-013's "a real amount never rounds to zero".
- **LOGIC-10** — scraper reads "1-1/2 cups" as 1; "1,000 g" and "1.000 g" as 1 g (comma/dot treated as decimals). See SCRAPER-7.
- **LOGIC-11** — more scraper mis-parses: "8 fl oz milk" gets no unit; "1 cup/240 ml milk" and "100g/3.5oz butter" lose quantity or unit; "2 lb 4 oz potatoes" → q=2 lb, name "4 oz potatoes"; U+202F/U+2009/U+3000 separators lose the quantity.
- **LOGIC-12** — generator treats a tag as dessert only if it exactly equals one of 9 words; "Dessert Recipes", "Desserts & Sweets", "Brownies", "Pie" etc. get scheduled as dinners, unlike the Recipes-tab Dessert filter.
- **LOGIC-13** — in lunch+dinner plans, when exactly one non-go-to recipe remains, the same recipe is served at lunch and dinner on one day (15 of 300 sweep cases; MealPlanScheduler.kt:73-100).
- **LOGIC-14** — `LocalMealPlanGenerator` (line 45) draws on every recipe on the device via unfiltered `getRecipesPreviewStream`, so a logged-out account's private recipes can reach an anonymous plan on the offline fallback.
- **LOGIC-15** — step-timer parser: "1h30m" → 30 min; "1,5 hours" → 5 h; "1:30 hours" → 30 h; ⅜ ⅝ ⅞, "an hour", "half an hour" → null.
- **LOGIC-16 (SUSPECTED, design question)** — METRIC mode: milk 500 ml + 1 l + 1 cup shows "1.77 kg"; water shows "4.5 kg". Density table includes pourables, contradicting the comment in `UnitConversion`.

## S4

- **LOGIC-17** — density suffix matching prices "ice cream"/"whipped cream" as plain cream (232 g/cup).
- **LOGIC-18** — small imperial amounts snap to 1/8 and distort: 2 g → ⅛ oz (+77%); 2 cups → 470 ml while 1 cup → 240 ml.
- **LOGIC-19** — section classifier misfiles "garlic powder" (produce), "butter beans" and "cream of tartar" (dairy), "sweet peppers" (spices).
- **LOGIC-20** — coriander the spice merges with cilantro the herb: "1 bunch + 1 tsp".
- **LOGIC-21** — `UuidV7Generator` uses `UUIDv7.generate()`, not monotonic within a millisecond. Library is Java-21 bytecode while unit tests run on JDK 17, so tests always take the `UUID.randomUUID()` fallback (200,000 test ids were all v4); the JVM suite never exercises real UUIDv7.
- **LOGIC-22** — after a rejected refresh the session goes Anonymous but stored credentials survive, so every cold start briefly publishes Authenticated. `DataModules.kt:158` sets `fallbackToDestructiveMigration`. Migrations 1→16 replayed in SQLite match each exported schema JSON exactly.
- **LOGIC-23 (SUSPECTED)** — a pulled recipe with unknown `privacy` or malformed uuid throws and stalls the whole pull (SyncMapper.kt:152).
- **LOGIC-24** — smaller: unit labels not pluralised ("19 cup", "⅓ cups"); decimal separator not localised; plan length 0 reports "no recipes"; "Max prep time" ignores cook time and unknown times; `kotlin.math.round` is half-to-even.

## Coverage
- ~70 shopping-list cases across AS_WRITTEN/METRIC/IMPERIAL + 20,000-case fuzz; every `MeasurementUnit` × 18 quantities × 5 names.
- 300-seed scheduler property sweep; dates/timezones across 8 zones, a leap day, a DST week, `weekStartFor`; `MealPlanReorder` 5,000-case property test (0 violations).
- Sync apply logic (LWW, tombstones, cooked-mark carry-forward, cursor, malformed DTOs); account upgrade and switch against real Room via Robolectric; SessionManager restore/logout/refresh races.
- ~55 timer phrases, ~40 ingredient lines, ISO duration/servings parsing, 29 language-detection pages (no bugs).

## Not covered
- `LogoutUseCase` (not in this worktree). Nothing run on a device (LOGIC-1/2 UI repros, real process death for LOGIC-7). Household sync beyond clear-on-logout/switch; `GenerateMealPlanUseCase` network paths.
