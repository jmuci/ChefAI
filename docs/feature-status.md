# Feature Status — Detail

_Last updated: November 2026_

CLAUDE.md's **Current Gaps** table keeps one line per area. This file holds the implementation detail
for areas that **have no ADR**; an area with an ADR keeps its detail there and is not repeated here.

---

## Home screen

Server-driven UI: `GET /api/v1/home/layout` returns components plus a sidecar. The sidecar is a
static server-side JSON fixture (`HomeLayoutService.loadSidecarJson()`), not per-user
personalization yet.

## Meal plans — detail screen and cooked marks

The week view, start dates and "+ Add meal" are [ADR-015](adrs/adr-015-meal-plans-week-view.md);
durable cook history is [ADR-017](adrs/adr-017-cook-history-sync.md); saved plans are
[ADR-018](adrs/adr-018-saved-meal-plans.md).

- **Detail screen:** tapping a plan card opens a day-grouped week view. Each meal is a compact row
  (`MealPlanMealRow`) that opens the recipe, with an 18dp checkbox that marks it cooked. A cooked
  meal **stays in place** (screen 16 draws the week as one ordered list, so ticking never reflows
  it): ticked box, struck-through title at 50% ink, meta line dropped. A segmented progress bar
  tracks the week, also shown on the list card.
- **Cooked toggle on recipe details:** a chef-hat toggle (`core/ui/components/CookedToggleButton`)
  shows only when details is opened from a meal-plan slot. The `MEAL_PLAN_RECIPE_DETAIL` route
  carries `mealPlanDayId`/`mealPlanSlot` alongside the recipe id — a recipe can fill several slots
  in a week, so the day+slot pair, not the recipe id, is what the toggle acts on.
- **One day, several slots:** a lunch + dinner day prints its date once; the second slot is ruled
  off by a hairline inset past the date column (`SameDaySlotRule`) rather than a full-bleed rule,
  so the pair reads as one day. Same in the week view (`WeekDayRow`). This is the one deliberate
  exception to the full-bleed rule rhythm in `docs/design/modernist.md`.
- **Removing a meal:** every meal line (detail screen and week view) has a trash button that
  empties the slot (`MealPlanRepository.removeMeal`, dirties the plan so it syncs) with an Undo
  snackbar (`restoreMeal`, which brings the cooked mark back and refuses if the slot was refilled).
  Cook history (ADR-017) is left alone — the cooking happened. The emptied slot becomes an
  **"Add recipe"** gap (`BoardSlot.Open` / `WeekDay.openSlots`) for each slot the plan's meal type
  expects; tapping it opens the saved-recipe picker (`AddMealSheet` with `fixedSlot`) and fills that
  plan's slot directly — unlike the week view's "+ Add meal", which seeds a new plan. Gaps are not
  offered for never-generated plans (no days) or for a slot another plan already fills that date.
  **Gap:** the picker has no "Add a New Recipe" path — the import hand-off only seeds new plans.
- **Cooked marks are per slot** (`meal_plan_days.dinnerCookedAt`/`lunchCookedAt`, DB v6,
  `MIGRATION_5_6`) and **local-only**: the sync payload has no field for them, so
  `SyncOrchestrator.applyPulledMealPlan` carries marks forward by `dayIndex`, and only while the slot
  still holds the same recipe. The durable `recipe_cook_events` log (ADR-017) does sync.

## Meal plans — generation

The backend `POST /meal-plans/{id}/generate` is still outstanding. An on-device fallback fills plans
regardless: `LocalMealPlanGenerator` + the pure `MealPlanScheduler` deal the user's own recipes into
the week, honouring plan length, meal type, max prep time, variety and dietary restrictions (matched
textually against tags/labels, falling back to the unfiltered pool rather than returning a short
plan).

**Go-to first:** go-to bookmarks (`RecipeBookmark.isGoTo`) that pass the filters fill the earliest
slots, each up to `GO_TO_SERVINGS_PER_WEEK` (2) times a week pro rata regardless of variety, never
twice on one day. Only go-tos among the candidates count, so a go-to public recipe is ignored by a
`COLLECTION_ONLY` plan. The server generator's equivalent is jmuci/Chef.ai_Backend#112.

The wizard and the detail screen try the server first and fall back — including when the call
succeeds but delivers no days, which is what an anonymous session sees. Server results overwrite
local ones on the next pull.

## Meal plans — sync

`SyncOrchestrator` pushes dirty plans and applies pulled ones (`SyncMealPlanDto`/
`SyncMealPlanDayDto`); backend spec in `archive/prompts/meal-plans-backend-prompt.md`. Cooked
timestamps are deliberately not in the payload. **Gap:** `applyPulledMealPlan` is skipped when there
is no authenticated user (the FK needs one), so an anonymous session never receives a
server-generated plan.

## Recipe search

Done (#151, #166–#170, #186–#188). Plan and rationale: `archive/prompts/search-tab-plan.md`.

- **Backend:** `GET /api/v1/recipes/search`, Postgres `tsvector`/GIN (generated column, no
  extension), tag/label match via a CTE, rate-limited. Anonymous sessions search the same PUBLIC
  catalog (the endpoint no longer 401s without a JWT, #187); the on-device `LIKE` scan is only the
  offline/network-failure fallback.
- **Android:** its own bottom-nav tab (`SearchScreen`, second slot). An M3 `SearchBar` (debounced
  300ms, 3-char minimum) pins over a browse landing page (`SearchBrowseContent`): a static
  client-side catalog (`SearchCategory`, grouped by meal and popularity) of gradient, image-free
  cards. A card feeds its English query term into the same `RecipeSearchViewModel` →
  `RecipeSearchRepository` pipeline typing does; results map straight to `RecipePreview` (no Room
  write) and reuse `RecipeListCard`.
- **Unsynced results (#186/#188):** `RecipesRepository.getOrFetchRecipe` falls back to the backend's
  anonymous-capable `GET /api/v1/recipes/{recipeId}` and persists via
  `SyncOrchestrator.fetchAndPersistRecipe` (reusing `upsertRecipeAggregate`) before
  `onRecipeClick`/`onSaveToCollection` proceed.
- **"Quick & Easy" is one rule everywhere:** total (prep + cook) time in 1..30 minutes, via
  `QUICK_EASY_MAX_TOTAL_MINUTES` (`core/domain/model/RecipePreview.kt`), used by
  `RecipeFilter.QUICK_EASY` and `SearchCategory.QUICK_EASY`; recipes with no recorded time are
  excluded.
- **Deferred:** "Search by Ingredients" (needs card art), real card artwork, a dedicated
  `search_results?q=…` destination, filters (vegetarian, protein, …), typo tolerance (`pg_trgm`),
  search history.

## Recipe URL import — share target

Scraping, the SSRF guard and image caching are [ADR-010](adrs/adr-010-client-side-recipe-scraping.md);
language detection is [ADR-016](adrs/adr-016-recipe-language.md). The share-target intent (T14, #126,
#328) is not covered by either:

- The chooser lists it as **Pocket Chef** (the intent-filter label; the launcher label is still
  `app_name`).
- A shared URL is **imported straight away**, with no Import tap, once per destination:
  `autoImportStarted` lives in the `SavedStateHandle`, so process death doesn't re-import.
- If that silent fetch returns `NeedsBrowser`, a shared URL **does not** open the in-app browser on
  its own: an "Open this site?" dialog names the host (`ImportRecipeState.browserConfirmUrl`), and
  Cancel leaves the URL in the field. A typed URL still goes straight to the browser
  (security review A6, #354).
- Shared text with no link shows a toast.

## Password reset

Done on Android. Login's "Forgot password?" opens `ForgotPasswordScreen`; the emailed App Link opens
`ResetPasswordScreen`. Details in [authentication.md](authentication.md#password-reset-nov-2026).

- Needs the backend's `/auth/password-reset/*` routes and a verified `assetlinks.json` on
  pocket-chef.app (the same one the household invite links use) for the link to open the app directly.
- Gaps: English only; the 60 s resend cooldown is per screen visit (leaving and returning allows an
  immediate resend — the server's per-email throttle is the real limit); no "open mail app" button;
  the Maestro flow has not been run against a live backend.

## Recipe delete

Soft delete via a button on the recipe details screen. No undo, no list swipe-to-delete, no delete
from the meal-plan recipe route.

## Starred & go-to recipes

Two marks on a bookmark, stored as `bookmarked_recipes.isStarred`/`isGoTo` (DB v14,
`MIGRATION_13_14`), so "starred/go-to ⊂ bookmarked" is structural and removing a bookmark clears
both. Tapping the bookmark (details hero, `RecipeListCard`) opens `BookmarkMenu` (a flat panel shared
with the profile menu via `core/ui/components/flat/FlatMenuPanel`): Bookmark / Star when unsaved,
Star or Remove star / Remove bookmark when saved. Go-to is a separate `GoToToggleButton` (Lucide
`repeat`, placeholder glyph) shown only on bookmarked recipes. All writes go through
`CollectionsRepository.update(userId, recipeId, BookmarkUpdate)`. The Recipes tab has
`STARRED`/`GO_TO` chips.

Synced as optional `starred`/`goTo` on the bookmark DTOs, but **the backend doesn't store them yet**,
so a pull that omits them keeps the local values; cross-device sync waits on the backend.

## Shopping lists

Done (#191–#193). Per meal plan, not a standalone grocery list. `ShoppingListBuilder` (pure Kotlin,
`mealplans/domain/shoppinglist/`) turns a plan's recipe ingredients into a grouped list: aggregated
by normalized name, scaled by planned servings ÷ each recipe's servings × how many slots that recipe
fills, grouped into `GrocerySection`s in store walk order. Reached via a FAB on the meal-plan detail
screen (`ShoppingListScreen`).

- Checked-off state is keyed by `(mealPlanId, itemKey)` in `shopping_list_checks` (`ON DELETE
  CASCADE` from `meal_plans`) and syncs last-writer-wins, showing who checked an item
  ([ADR-014](adrs/adr-014-households.md)). The old "local-only" description predates ADR-014.
- Names are grouped by `ShoppingItemNames`: parenthetical/serving/preparation notes dropped,
  regional synonyms (aubergine/eggplant, courgette/zucchini, …) folded via a data table — key only;
  the shown name is one a recipe used.
- In Metric/Imperial a volume and a weight of a known-density staple merge into one approximate
  total ([ADR-013](adrs/adr-013-measurement-unit-conversion.md)); a bare count beside a measure
  reads "6 whole".
- Edits: long-press a row to reveal a pencil (rename) and an X (remove; snackbar with Undo).
  The list is derived, not stored, so edits are an overlay on `shopping_list_checks`
  (`displayNameOverride`, `removed`) keyed by the original `itemKey` — a rename never orphans a
  tick and a plan rebuild never undoes an edit. They ride the existing grocery sync row
  (row-level LWW, so a concurrent tick can overwrite a rename). Rename is display-only (section and
  quantity stay as computed). A removed item stays hidden even if its quantity later changes.
  Requires the backend's `displayNameOverride`/`removed` fields; until it deploys, edits stay
  local and a pull of that row can overwrite them. The week view's item count ignores removals.
- **Hand-added items** (`shopping_list_custom_items`, migration 17→18): an "Add an item" box under
  the list. A typed line is classified into a section by `GrocerySectionClassifier`, never merged
  with a recipe line, set in italic with the adder's initials (`SquareAvatar`) between checkbox and
  name, and ticks, renames, removes (with Undo) and "Uncheck all"s like any line — rename/remove
  act on its own row, not the `shopping_list_checks` overlay. **Local-only** — no sync field
  exists, so on a shared plan other members don't see them yet; syncing needs a backend
  `SyncGroceryListItem` variant plus `syncState`/`updatedAt`/`deletedAt` on the entity. An anonymous
  session has no display name, so its lines are italic without initials.
- [`grocery-api-landscape.html`](grocery-api-landscape.html)'s delivery-partner research
  (Instacart/Albertsons/Kroger) is unrelated and unactioned. No ADR yet.

## Recipe step timer

Core done (#202, #206, #208, #211), manually verified on an emulator.

- `parseStepDurationSeconds` (`core/util/StepDurationParser.kt`) regex-extracts a duration from a
  step's text ("Bake for 30 minutes" → 1800s); a clock icon on any parseable step starts the
  app-wide `RecipeTimerController` (`core/data/timer/`, `@ApplicationScope`, so it survives
  navigation).
- **Ticks run on `Dispatchers.Main`.** `start`/`pause`/`resume`/`cancel` come from Compose click
  handlers, so confining ticks to Main makes every mutation single-threaded with no lock (#208 fixed
  a `pause()` clobbered back to running by an in-flight tick).
- **Each tick reads a stored `TimeMark` deadline** instead of decrementing a counter (#211):
  `delay(1_000)` always fires late, and a frozen background process stops ticking while the clock
  runs on. `RecipeTimerController.timeSource` is an `internal var` test seam. This fixes the in-app
  countdown only; a timely notification across deep sleep needs an `AlarmManager` alarm.
- `start()` returns the `RecipeTimerState?` it replaced, driving a "Replaced the timer for Step N"
  toast.
- `FloatingRecipeTimerWidget` (`core/ui/timer/`, stateless content + thin ViewModel entry point),
  hosted once above `ChefAINavGraph` in `MainActivity`: countdown, drag-to-reposition (survives a
  stop/start and re-clamps on rotation), pause/resume, cancel. `RecipeTimerNotifier` posts on
  completion and requests `POST_NOTIFICATIONS` on API 33+ before the first timer; covered by the
  repo's first Robolectric tests (`RecipeTimerNotifierTest`).
- **Not done:** one timer at a time; neither the timer nor the widget position survives process
  death; non-numeric durations ("until golden brown") unsupported by design.
