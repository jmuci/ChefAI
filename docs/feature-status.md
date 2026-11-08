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
