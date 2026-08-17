# Search Tab — Implementation Plan

**Goal:** Move recipe search off the Home screen into its own bottom-nav tab, and give that tab a
browse landing page built from simple, image-free category cards (à la SideChef's search page).

**Branch:** `claude/search-tab-category-cards-xmyxuz`
**Executor:** Sonnet 5, one step per session/commit. Steps are ordered; each one compiles and is
independently committable.

---

## Decisions already made (do not re-litigate)

| Question | Decision |
|---|---|
| Tapping a category card | Sets the search query to the category's English term and expands the existing search bar. Reuses the shipped `GET /api/v1/recipes/search` (title/tag/label matching). **No new data layer, no new endpoint, no new ViewModel.** |
| "Search by Ingredients" row | **Deferred.** Not in this pass — it is image-driven in the mock. |
| Bottom-nav order | `Home, Search, Recipes, Meal Plans` — Search goes in the **second** slot. |
| Card art | None. Gradient background + title text only. Real art lands in a later ticket. |
| Search bar mechanics | Keep the M3 `SearchBar` **exactly as it works today** (collapsed bar overlaying the page; expands to a full-screen results view). Do not rewrite it into an inline/docked field — that is a separate refinement, see "Explicitly out of scope". |

---

## Context you need before starting

Current state (all of this already works and is tested — do not break it):

- `search/ui/RecipeSearchOverlay.kt` — M3 `SearchBar` composable. Owns its `expanded` state locally
  in a `rememberSaveable`. Renders results in the expanded view via `RecipeListCard`.
- `search/ui/RecipeSearchViewModel.kt` — owns `query: StateFlow<String>` and
  `uiState: StateFlow<SearchUiState>`. 300 ms debounce, 3-char minimum, remote-then-local-fallback.
  `SearchUiState` = `Idle | Searching | Results | Empty | Error`. **This ViewModel does not change
  in this plan** (one optional test addition in Step 7).
- `home/ui/HomeScreen.kt` — hosts the search bar as a `Box` overlay on top of the SDUI `LazyColumn`,
  and pads that list with `top = 64.dp` so the collapsed bar doesn't cover the first section.
- `core/ui/navigation/AppDestinations.kt` — `ScreenBaseRoutes` (route string constants),
  `AppDestinations` (enum of destination + `@StringRes title`), `NavigationActions`.
- `core/ui/navigation/BottomNavigationBar.kt` — `TopLevelDestination` enum drives **both** the bottom
  bar and the expanded-width `NavigationRailBar`. Adding an entry updates both for free.
- `core/ui/navigation/ChefAINavGraph.kt` — `navController.createGraph { composable(route) { … } }`.

Relevant conventions: `docs/claude/gotchas.md` (esp. #2 `collectAsStateWithLifecycle`, #3
recomposition, #26 test dispatchers), `.claude/skills/compose-component.md`.

---

## Step 1 — Add the Search destination, tab, icon and strings

Nav plumbing only. The tab renders a stub; extraction happens in Step 2.

### 1.1 New drawable — `app/src/main/res/drawable/ic_search_24dp.xml`

Match the existing 960×960 Material Symbols viewport used by `ic_home_black_24dp.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:height="24dp"
    android:tint="#000000" android:viewportHeight="960" android:viewportWidth="960"
    android:width="24dp">
    <path android:fillColor="@android:color/white" android:pathData="M784,840L532,588Q502,612 463,626Q424,640 383,640Q281,640 210.5,569.5Q140,499 140,398Q140,297 210.5,226.5Q281,156 382,156Q483,156 553.5,226.5Q624,297 624,398Q624,439 610,478Q596,517 572,547L824,799L784,840ZM382,580Q458,580 511,527Q564,474 564,398Q564,322 511,269Q458,216 382,216Q305,216 252.5,269Q200,322 200,398Q200,474 252.5,527Q305,580 382,580Z"/>
</vector>
```

Open the file in Android Studio's preview (or build and eyeball the tab) to confirm it renders a
magnifying glass and not a blob. If the path looks wrong, export `search` from Material Symbols at
24dp instead of hand-fixing it.

### 1.2 `app/src/main/res/values/strings.xml`

Add next to the other `app_dest_title_*` entries:

```xml
<string name="app_dest_title_search">Search</string>
```

### 1.3 `core/ui/navigation/AppDestinations.kt`

- In `ScreenBaseRoutes`, after `HOME`: `const val SEARCH = "search_screen"`
- In the `AppDestinations` enum, after `HOME`:
  `SEARCH(R.string.app_dest_title_search, ScreenBaseRoutes.SEARCH),`

Do **not** add a `NavigationActions.navigateToSearch()` — the bottom bar navigates by route with its
own `popUpTo/restoreState` options, and nothing else links to Search.

### 1.4 `core/ui/navigation/BottomNavigationBar.kt`

Insert into `TopLevelDestination` **between `HOME` and `RECIPES`** (enum order is nav-bar order):

```kotlin
SEARCH(
    icon = R.drawable.ic_search_24dp,
    appDestination = AppDestinations.SEARCH
),
```

Leave `isRouteInSection` alone — Search has no child routes.

### 1.5 `core/ui/navigation/ChefAINavGraph.kt`

Add a `composable` right after the `HOME` one:

```kotlin
composable(route = AppDestinations.SEARCH.route) {
    SearchScreen(
        snackbarHostState = snackbarHostState,
        onRecipeClick = { recipeUuid -> navActions.navigateToRecipeDetail(recipeUuid) },
    )
}
```

### 1.6 Stub `app/src/main/java/com/tenmilelabs/chefai/search/ui/SearchScreen.kt`

Temporary — Step 2 fills it in. Just enough to compile:

```kotlin
@Composable
fun SearchScreen(
    snackbarHostState: SnackbarHostState,
    onRecipeClick: (UUID) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize())
}
```

### Verify
```
./gradlew :app:assembleDebug
```
Manual: four tabs in the order Home, Search, Recipes, Meal Plans; the Search tab opens a blank
screen with "Search" in the top app bar.

**Commit:** `Search tab: add SEARCH destination, bottom-nav entry and icon`

---

## Step 2 — Move the search bar off Home onto the Search tab

The extraction the whole plan hangs off. **Behaviour of search itself must not change.**

### 2.1 Rename `search/ui/RecipeSearchOverlay.kt` → `search/ui/RecipeSearchBar.kt`

Same package, so this is a file + composable rename only (`git mv`, then rename the function).
`RecipeSearchOverlay` is now a misnomer — it is the Search tab's bar, not a Home overlay.

Change the signature to **hoist `expanded`** (Step 6 needs to set it from outside) and to take the
ViewModel explicitly:

```kotlin
/**
 * The Search tab's search bar. Collapsed it sits at the top of the tab; expanded it takes over the
 * screen and renders results. [expanded] is hoisted so the browse page's category cards can open
 * the results view (see [SearchScreen]). [RecipeSearchViewModel] owns query text and results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeSearchBar(
    viewModel: RecipeSearchViewModel,
    snackbarHostState: SnackbarHostState,
    onRecipeClick: (UUID) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
)
```

- Delete the local `var expanded by rememberSaveable { … }` and the `hiltViewModel()` default; use
  the hoisted `expanded` / `onExpandedChange` everywhere the old local state was used
  (`SearchBar(expanded = …, onExpandedChange = …)`, the `InputField`'s two params, `onSearch`, the
  trailing clear button, and the `onRecipeClick` lambda that collapses before navigating).
- Everything else in the file — `SearchResultsContent`, the `LaunchedEffect(viewModel)` snackbar
  collection, the `when (uiState)` branches — stays byte-for-byte identical.

### 2.2 Fill in `search/ui/SearchScreen.kt`

```kotlin
/**
 * The Search tab. A [RecipeSearchBar] overlays the browse landing page; expanding it (by typing or
 * by tapping a category card) covers the page with results. Mirrors how the bar sat on Home before
 * search became its own tab.
 */
@Composable
fun SearchScreen(
    snackbarHostState: SnackbarHostState,
    onRecipeClick: (UUID) -> Unit,
    viewModel: RecipeSearchViewModel = hiltViewModel(),
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Step 5 puts SearchBrowseContent here.

        RecipeSearchBar(
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            onRecipeClick = onRecipeClick,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(R.dimen.padding_small)),
        )
    }
}
```

`SearchScreen` obtains the ViewModel and hands the same instance down — do not let `RecipeSearchBar`
resolve its own.

### 2.3 `home/ui/HomeScreen.kt` — remove search

This is a **faithful revert** of what commit `1ef5354` ("Recipe search: SearchBar UI + Home wiring")
did to this one file. Read that commit first — `git show 1ef5354 -- app/src/main/java/com/tenmilelabs/chefai/home/ui/HomeScreen.kt` —
and undo exactly it. Reverting to the known-good pre-search state is lower risk than a partial
cleanup, because that state actually shipped.

Concretely, four things come back out:

1. **The `Box` wrapper goes away entirely.** Before search, `HomeScreen`'s body was a bare
   `when (val state = uiState) { … }` at top level — no `Box`. `LoadingContent` and `EmptyContent`
   already `fillMaxSize()` internally, so they do not need one. De-indent the `when` back to its
   original nesting.
2. **The `RecipeSearchOverlay(...)` call** and the `// Not a LazyColumn item — …` comment above it.
3. **`HomeContent`'s `contentPadding`** goes back to the exact original single line:
   ```kotlin
   contentPadding = PaddingValues(vertical = dimensionResource(R.dimen.padding_extra_small)),
   ```
   along with the two-line `// Extra top clearance so the collapsed search bar…` comment.
4. **Exactly the six imports that commit added**, no more and no fewer:
   `androidx.compose.foundation.layout.Box`, `…layout.fillMaxWidth`, `…layout.padding`,
   `androidx.compose.ui.Alignment`, `androidx.compose.ui.unit.dp`, and
   `com.tenmilelabs.chefai.search.ui.RecipeSearchOverlay`.

   Keep `androidx.compose.foundation.layout.fillMaxSize` (still used by `HomeContent`'s modifier)
   and `androidx.compose.ui.res.dimensionResource` (still used by the restored `contentPadding`).

Afterwards, `git diff` on this file should be a clean inverse of `1ef5354`'s hunks. If it is not,
something extra was changed — investigate before moving on.

### Verify
```
./gradlew :app:assembleDebug :app:testDebugUnitTest
```
`RecipeSearchViewModelTest` must still pass untouched. Manual: Home has no search bar and its first
SDUI section is no longer pushed down; the Search tab's bar types, debounces, returns results, opens
a recipe, and bookmarks from a result card.

**Commit:** `Search tab: move the search bar off Home (#151 follow-up)`

---

## Step 3 — Category catalog

A static, client-side catalog. Server-driven categories are a later concern — say so in the KDoc so
the next reader doesn't mistake it for a permanent model.

### 3.1 `app/src/main/res/values/strings.xml`

```xml
<!-- Search tab — browse sections -->
<string name="search_section_by_meal">Search by Meal</string>
<string name="search_section_popular_categories">Most Popular Categories</string>

<!-- Search tab — meal categories -->
<string name="search_category_breakfast">Breakfast</string>
<string name="search_category_lunch">Lunch</string>
<string name="search_category_snack">Snack</string>
<string name="search_category_dinner">Dinner</string>
<string name="search_category_dessert">Dessert</string>
<string name="search_category_kid_friendly">Kid-Friendly</string>

<!-- Search tab — popular categories -->
<string name="search_category_low_carb">Low-Carb</string>
<string name="search_category_sandwiches_wraps">Sandwiches &amp; Wraps</string>
<string name="search_category_quick_easy">Quick &amp; Easy</string>
<string name="search_category_budget_friendly">Budget-Friendly</string>
<string name="search_category_air_fryer">Air Fryer</string>
<string name="search_category_vegetarian">Vegetarian</string>
<string name="search_category_protein_packed">Protein-Packed</string>
<string name="search_category_healthy">Healthy</string>
<string name="search_category_cookies">Cookies</string>
<string name="search_category_comfort_food">Comfort Food</string>
```

Note the `&amp;` — a bare `&` breaks the resource compiler.

### 3.2 `app/src/main/java/com/tenmilelabs/chefai/search/ui/model/SearchCategory.kt`

```kotlin
package com.tenmilelabs.chefai.search.ui.model

/** The two browse sections on the Search tab, in render order. */
enum class SearchCategoryGroup(@param:StringRes val titleRes: Int) {
    MEAL(R.string.search_section_by_meal),
    POPULAR(R.string.search_section_popular_categories),
}

/**
 * A browse shortcut on the Search tab. Tapping one runs the ordinary recipe search with [query].
 *
 * [labelRes] is what the user reads and is localisable; [query] is the English term actually sent to
 * `GET /api/v1/recipes/search`, which matches against recipe titles, tags and labels — those are
 * stored in English, so the two must stay separate.
 *
 * Static on purpose: this is a hand-curated catalog, not a server-driven one. If categories ever
 * need to be personalised, they should come down the SDUI home-layout channel instead.
 */
enum class SearchCategory(
    val group: SearchCategoryGroup,
    @param:StringRes val labelRes: Int,
    val query: String,
) {
    BREAKFAST(SearchCategoryGroup.MEAL, R.string.search_category_breakfast, "breakfast"),
    LUNCH(SearchCategoryGroup.MEAL, R.string.search_category_lunch, "lunch"),
    SNACK(SearchCategoryGroup.MEAL, R.string.search_category_snack, "snack"),
    DINNER(SearchCategoryGroup.MEAL, R.string.search_category_dinner, "dinner"),
    DESSERT(SearchCategoryGroup.MEAL, R.string.search_category_dessert, "dessert"),
    KID_FRIENDLY(SearchCategoryGroup.MEAL, R.string.search_category_kid_friendly, "kid friendly"),

    LOW_CARB(SearchCategoryGroup.POPULAR, R.string.search_category_low_carb, "low carb"),
    SANDWICHES_WRAPS(SearchCategoryGroup.POPULAR, R.string.search_category_sandwiches_wraps, "sandwich"),
    QUICK_EASY(SearchCategoryGroup.POPULAR, R.string.search_category_quick_easy, "quick"),
    BUDGET_FRIENDLY(SearchCategoryGroup.POPULAR, R.string.search_category_budget_friendly, "budget"),
    AIR_FRYER(SearchCategoryGroup.POPULAR, R.string.search_category_air_fryer, "air fryer"),
    VEGETARIAN(SearchCategoryGroup.POPULAR, R.string.search_category_vegetarian, "vegetarian"),
    PROTEIN_PACKED(SearchCategoryGroup.POPULAR, R.string.search_category_protein_packed, "protein"),
    HEALTHY(SearchCategoryGroup.POPULAR, R.string.search_category_healthy, "healthy"),
    COOKIES(SearchCategoryGroup.POPULAR, R.string.search_category_cookies, "cookies"),
    COMFORT_FOOD(SearchCategoryGroup.POPULAR, R.string.search_category_comfort_food, "comfort food"),
    ;

    companion object {
        fun of(group: SearchCategoryGroup): List<SearchCategory> = entries.filter { it.group == group }
    }
}
```

Every `query` is ≥ 3 characters, which matters: the ViewModel ignores anything shorter. If you add a
category later, keep that invariant.

### Verify
```
./gradlew :app:assembleDebug
```

**Commit:** `Search tab: category catalog`

---

## Step 4 — `CategoryCard`

A title on a gradient. No image, no recipe count, no icon.

### 4.1 `app/src/main/res/values/dimens.xml`

```xml
<dimen name="category_card_height">96dp</dimen>
<dimen name="card_corner_radius">16dp</dimen>
```

### 4.2 `app/src/main/java/com/tenmilelabs/chefai/search/ui/components/CategoryCard.kt`

Stateless, preview-able, no ViewModel. Tones come from `MaterialTheme.colorScheme` so light/dark and
dynamic color both work — do **not** hardcode the mock's pink/teal hexes.

```kotlin
/** Background/foreground pairing for a [CategoryCard]; cycled across the grid so adjacent cards differ. */
enum class CategoryCardTone { PRIMARY, SECONDARY, TERTIARY }

@Composable
fun CategoryCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: CategoryCardTone = CategoryCardTone.PRIMARY,
)
```

Implementation notes:

- Resolve the tone inside the composable:
  - `PRIMARY` → `primaryContainer` → `secondaryContainer`, text `onPrimaryContainer`
  - `SECONDARY` → `secondaryContainer` → `tertiaryContainer`, text `onSecondaryContainer`
  - `TERTIARY` → `tertiaryContainer` → `primaryContainer`, text `onTertiaryContainer`
- `Card(onClick = onClick, shape = RoundedCornerShape(dimensionResource(R.dimen.card_corner_radius)), colors = CardDefaults.cardColors(containerColor = Color.Transparent))`
  wrapping a `Box` that carries
  `Modifier.fillMaxWidth().height(dimensionResource(R.dimen.category_card_height)).background(Brush.linearGradient(listOf(start, end)))`.
- Title: `MaterialTheme.typography.titleMedium`, `FontWeight.Bold`, tone's content color,
  `maxLines = 2`, `overflow = TextOverflow.Ellipsis`, aligned `Alignment.CenterStart`, padded
  `dimensionResource(R.dimen.padding_medium)`.
- Add `Modifier.testTag("CategoryCard")` inside the component so Step 7 can count cards; tests match
  individual cards by their text.
- `Card(onClick = …)` already exposes the click to a11y and shows a ripple — do not add a separate
  `clickable`.

Four `@Preview`s: light, dark (`ChefAITheme(darkTheme = true)`), a long title
("Sandwiches & Wraps") to prove the 2-line ellipsis, and a `Row` of all three tones side by side.

### Verify
```
./gradlew :app:assembleDebug
```
Check the previews render in Android Studio.

**Commit:** `Search tab: CategoryCard component`

---

## Step 5 — `SearchBrowseContent` (the landing page)

### 5.1 `app/src/main/java/com/tenmilelabs/chefai/search/ui/components/SearchBrowseContent.kt`

```kotlin
/**
 * The Search tab's landing page: browse shortcuts grouped into sections, two cards per row.
 * Stateless — the caller decides what a tap does.
 */
@Composable
fun SearchBrowseContent(
    onCategoryClick: (SearchCategory) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
)
```

**Critical:** use a single `LazyVerticalGrid` for the whole page. Do **not** nest a
`LazyVerticalGrid` inside a `LazyColumn` — Compose throws on the unbounded height. Section headers
become full-width items instead:

```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    modifier = modifier.fillMaxSize().testTag("SearchBrowseContent"),
    contentPadding = contentPadding,
    horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
) {
    SearchCategoryGroup.entries.forEach { group ->
        item(span = { GridItemSpan(maxLineSpan) }, key = "header_${group.name}") {
            BrowseSectionHeader(title = stringResource(group.titleRes))
        }
        itemsIndexed(
            items = SearchCategory.of(group),
            key = { _, category -> category.name },
        ) { index, category ->
            CategoryCard(
                title = stringResource(category.labelRes),
                tone = CategoryCardTone.entries[index % CategoryCardTone.entries.size],
                onClick = { onCategoryClick(category) },
            )
        }
    }
}
```

Imports come from `androidx.compose.foundation.lazy.grid.*` (`LazyVerticalGrid`, `GridCells`,
`GridItemSpan`, `itemsIndexed`) — the `grid` subpackage, not the plain `lazy` one.

`BrowseSectionHeader` is a private composable in this file: `Text` with
`MaterialTheme.typography.headlineSmall`, `FontWeight.Bold`, padded
`vertical = padding_medium, horizontal = padding_extra_small`. `SectionHeaderWithSubtitle` is not
reused — it forces a subtitle and a fixed 60dp height neither section wants.

Previews: light + dark, `onCategoryClick = {}`.

### 5.2 Wire it into `SearchScreen`

Inside the `Box`, **before** `RecipeSearchBar` so the bar paints on top:

```kotlin
SearchBrowseContent(
    onCategoryClick = { /* Step 6 */ },
    contentPadding = PaddingValues(
        start = dimensionResource(R.dimen.padding_medium),
        end = dimensionResource(R.dimen.padding_medium),
        // Clearance for the collapsed search bar, which overlays this page rather than scrolling with it.
        top = dimensionResource(R.dimen.search_bar_clearance),
        bottom = dimensionResource(R.dimen.padding_medium),
    ),
)
```

Add `<dimen name="search_bar_clearance">72dp</dimen>` to `dimens.xml`.

### Verify
```
./gradlew :app:assembleDebug
```
Manual: the Search tab shows both sections, two cards per row, scrolls under the pinned bar, first
header fully visible (not clipped by the bar), and cards taps do nothing yet.

**Commit:** `Search tab: browse landing page with category sections`

---

## Step 6 — Wire card taps to search

In `SearchScreen`, replace the placeholder lambda:

```kotlin
SearchBrowseContent(
    onCategoryClick = { category ->
        viewModel.onQueryChanged(category.query)
        expanded = true
    },
    …
)
```

That is the whole change. `onQueryChanged` feeds the ViewModel's existing debounce pipeline, so the
results view opens on `Searching` and settles into `Results` / `Empty` ~300 ms later — the same path
as typing.

### Verify
Manual, on a device or emulator:
1. Tap **Vegetarian** → the bar expands, the field reads `vegetarian`, results appear.
2. Back / the ✕ button collapses the bar, clears the query and returns to the category grid. If the
   system back button does **not** collapse the expanded bar, add
   `BackHandler(enabled = expanded) { expanded = false }` to `SearchScreen` — but check first, recent
   Material3 `SearchBar` versions already register their own handler and a second one can conflict.
3. Rotate the device while expanded — `expanded` is `rememberSaveable`, so the results view survives.
4. Tap a category with no matching recipes → the existing empty state renders, not a crash.

**Commit:** `Search tab: category cards run a recipe search`

---

## Step 7 — Tests

The ViewModel's own suite already covers the search pipeline and does not change. What is new here is
stateless UI, so it gets Compose tests in `androidTest` — same shape as
`core/ui/components/RecipeListCardTest.kt` (plain `createComposeRule`, no Hilt, no ViewModel).

### 7.1 `app/src/androidTest/java/com/tenmilelabs/chefai/search/ui/components/CategoryCardTest.kt`

- `title_isDisplayed`
- `click_invokesCallback` — a `var clicked = false`, `onNodeWithText("Breakfast").performClick()`,
  assert it flipped
- `longTitle_doesNotCrash_andStaysWithinCardHeight` — render "Sandwiches & Wraps" inside a 200 dp-wide
  box, assert the node's height is at most `category_card_height`

### 7.2 `app/src/androidTest/java/com/tenmilelabs/chefai/search/ui/components/SearchBrowseContentTest.kt`

- `bothSectionHeaders_areDisplayed` — "Search by Meal" and "Most Popular Categories". Pull the strings
  from resources (`composeTestRule.activity.getString(...)` via `createAndroidComposeRule`, or
  `InstrumentationRegistry.getInstrumentation().targetContext.getString(...)`) rather than hardcoding
  them.
- `categoryClick_reportsTheTappedCategory` — capture the `SearchCategory` from the callback, tap
  "Breakfast", assert `SearchCategory.BREAKFAST`. This is the one that would catch a mis-wired
  `itemsIndexed` lambda.
- `firstSection_rendersEveryMealCategory` — scroll to and assert each `SearchCategoryGroup.MEAL`
  label. Use `onNodeWithText(...).performScrollTo()` where needed; a `LazyVerticalGrid` does not
  compose off-screen items.

### 7.3 Optional ViewModel test — `search/ui/RecipeSearchViewModelTest.kt`

Add one case proving a category term flows through the same pipeline:

```
categoryQuery_runsSearch_likeTypedInput
```
`viewModel.onQueryChanged("vegetarian")`, `advanceTimeBy(400)`, assert `SearchUiState.Results` and
that the fake repository recorded exactly one call with `"vegetarian"`. Follow the file's existing
setup exactly — hand-wired `StandardTestDispatcher` + `TestScope`, every assertion inside
`viewModel.uiState.test { }` (see gotchas #26; reading `.value` directly sees `Idle` forever).

### Verify — required by CLAUDE.md
```
./gradlew :app:testDebugUnitTest
```
Report the pass/fail count. Fix any failure before calling the step done. `androidTest` needs a
device/emulator — if none is attached, say so explicitly rather than reporting the Compose tests as
passing.

**Commit:** `Search tab: Compose tests for the category cards and browse page`

---

## Step 8 — Docs

### 8.1 `CLAUDE.md`

In the **Current Gaps** table, rewrite the *Recipe search* row's Android half: search now lives on its
own bottom-nav tab (`SearchScreen`), not as an overlay on Home; the tab's landing page is a static,
client-side category catalog (`SearchCategory`) whose cards run the ordinary text search. Keep the
existing backend description and the pointer to the filters/typo-tolerance/history follow-ups.

Add the deferred work to that row so it is not lost: the "Search by Ingredients" row, real card
artwork, and a dedicated results destination.

### 8.2 `.claude/session-context.md`

The file does not exist yet, though `CLAUDE.md` says to read it at session start. Create it with:
what changed and why, the branch and its purpose, open questions (card art, ingredients row,
whether categories should eventually be server-driven), and next steps.

**Commit:** `Docs: search tab status and session context`

---

## Explicitly out of scope

Do not do these without being asked, even if they seem like natural extensions:

- **An inline/docked search field** that swaps browse content for results in place. The mock hints at
  it, but the expanding `SearchBar` is what ships today and rewriting it risks the search UX that
  #151 just stabilised.
- **A dedicated `search_results?q=…` destination.** Considered and deferred — revisit when filter
  chips land.
- **The "Search by Ingredients" row.**
- **Card artwork, image loading, or per-category colors from a design spec.** Gradients from the
  theme only.
- **Backend changes.** No new endpoint, no category taxonomy on the server, no `tags` filter param.
- **Touching `RecipeSearchViewModel`'s search pipeline**, the repository, DTOs, or Room.
- **Renaming or moving packages** (CLAUDE.md forbids it without asking). The one rename in this plan,
  `RecipeSearchOverlay` → `RecipeSearchBar`, is a file/composable rename inside an existing package.

---

## Risk notes for the executor

| Risk | Mitigation |
|---|---|
| `LazyVerticalGrid` nested in a scrolling parent | Step 5 uses **one** grid with full-width header items. Never wrap it in a `LazyColumn` or `verticalScroll`. |
| Two `RecipeSearchViewModel` instances (bar vs screen) | `SearchScreen` calls `hiltViewModel()` once and passes the instance down; `RecipeSearchBar` has no default. |
| Category grid hidden under the collapsed bar | `search_bar_clearance` top `contentPadding`, mirroring Home's old 64 dp hack. Verify the first header is fully visible on a small screen. |
| Back button doesn't collapse the expanded bar | Check before adding a `BackHandler` — Material3 may already own it (Step 6). |
| Unused imports after the Home edit | Build and read the warnings; don't delete by eye. |
| Localised label used as the search query | `SearchCategory` keeps `labelRes` and `query` separate. Never pass `stringResource(labelRes)` to `onQueryChanged`. |
