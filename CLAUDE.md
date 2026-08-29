# ChefAI — Claude Code Instructions

## Permissions
The following `gh` commands are pre-authorized (no confirmation needed):
- `gh issue create` — creating GitHub issues
- `gh api graphql` — GraphQL API queries against GitHub

## Session Memory
On every session start, read `.claude/session-context.md` for current project state.
After completing significant work (features, fixes, architectural changes), update that file with:
- What was changed and why
- Current branch and its purpose
- Any blockers, open questions, or next steps
- Updated project status if milestones changed

---

## Project Overview
**Pocket Chef (ChefAI)** — Offline-first Android recipe management app. Users save recipes, browse a library, generate meal plans, and create grocery lists. Aimed at healthy eating, reducing food waste, and saving time.

- **Android**: Kotlin, Jetpack Compose, Hilt, Room, Ktor client
- **Backend**: Kotlin, Ktor server, PostgreSQL, Exposed DSL, JWT
- **Auth model**: Anonymous-first → optional account upgrade
- **Data model**: Room is local SSOT; backend is authoritative after sync

## Architecture
Hybrid Architecture (Google Modern Android + selective Clean Architecture). See [docs/architecture.md](docs/architecture.md) for full details.

```
UI (Compose + ViewModel + StateFlow)
        ↓ events / ↑ state
Domain (Use Cases, Repository interfaces, Domain models)
        ↓
Data (Repository impls, Room DAOs, Ktor client, Mappers)
```

### Layers
- **UI**: Compose screens, ViewModels exposing `StateFlow<UiState>`. Stateless, previewable composables. No business logic.
- **Domain** (optional): Use cases when logic is reused/complex. Repository interfaces. Pure Kotlin, no Android deps.
- **Data**: Repository implementations, Room entities, network DTOs, mappers at boundaries.

### Package Structure (Feature-Based)
```
com.tenmilelabs.chefai/
├── auth/           (data, domain, ui)
├── recipes/        (data, domain, ui)
├── home/           (ui)
├── mealplans/      (ui)
└── core/           (shared: data, domain, ui, di, util)
```
Rule: start in feature package, move to `core/` only when a second feature needs it.

See [ADR-005](docs/adrs/adr-0005-feature-based-package-structure.md) for full guidelines.

### Modules
Besides `:app`, the project has `:recipe-scraper` — a pure-Kotlin (KMP, `jvm()`-only target today)
module with no Android/Hilt/Room/Ktor dependencies. It parses HTML into a structured recipe
(`RecipeHtmlParser`, JSON-LD + microdata) and does no network I/O; `:app` fetches pages and maps the
result into a `RecipeDraft`. See [ADR-010](docs/adrs/adr-010-client-side-recipe-scraping.md) and
`recipe-scraper/README.md`. **Never name a package/class after a Kotlin reserved word** (`import`,
`object`, `when`, …) — see `docs/claude/gotchas.md` for why.

---

## Coding Rules

### Kotlin & Compose
- Idiomatic Kotlin: null-safety, extension functions sparingly
- Compose: `remember{}` only when needed; hoist state; stable parameters; avoid unnecessary recomposition
- ViewModels expose `StateFlow<UiState>` with sealed UI state (Loading, Success, Error)
- Flow/StateFlow for async streams; avoid LiveData
- Prefer constructor injection via Hilt; avoid singletons
- Coroutines: structured concurrency, `Dispatchers.IO` for I/O, never block main

### Architecture Boundaries
- Separate domain models from Room entities & DTOs; map at boundaries
- Repository interfaces in `domain/repository/`, implementations in `data/repository/`
- Keep domain models in `domain/model/`. Never leak Room/DTO types into UI or Domain
- Use mapping functions under `data/mapper/`
- No Android framework dependencies in Domain
- All logic in Domain/Data should be unit-testable without Android
- Domain layer use cases: single-responsibility, stateless, invokable via `operator invoke`
- Repositories expose `Flow` for streams; queries accept plain Kotlin primitives/value objects

### Data & Persistence
- All entities use UUIDv7 IDs (time-sortable). 16-byte blobs in SQLite, native UUIDs in Postgres
- DB schema uses snake_case
- Local persistence: Room with FTS5 enabled
- Entities implement `SyncableEntity` with `syncState`, `updatedAt`, `deletedAt`
- All writes go to Room first; UI reads only from Room flows
- Networking: Ktor client + Kotlinx Serialization; robust error handling; no blocking I/O

### Sync Protocol 
Two-step sync per [ADR-006](docs/adrs/adr-006-sync-protocol.md):
1. `POST /sync/push` — uploads entities with `syncState IN (PENDING, DELETED)`
2. `GET /sync/pull?since=<timestamp>` — fetches backend deltas
- Conflicts: last-writer-wins based on `updatedAt`
- Sync triggers via WorkManager: app foreground, connectivity restored, post-mutation debounce
- Push before pull

### Style
- Logging: Timber; structured messages; avoid noisy logs in production
- Prefer Material 3 components; avoid deprecated Compose APIs
- KDoc on public APIs
- Favor readability and simplicity over performance
- Keep indirection minimal. Introduce layers only when they buy clarity/testability/reuse

---

## Code Generation Patterns

### New Compose Screen
- Stateless UI function + `@Preview`
- ViewModel using Hilt with `StateFlow<UiState>` (sealed: Loading/Success/Error)
- Material3 components, loading + error + empty states
- Navigation event callback
- Unit test for ViewModel
- No business logic in UI; repository used in ViewModel

### Ktor Client Feature
- Kotlinx Serialization for DTOs
- Logging + timeout config
- Suspend network calls
- Map DTO → domain model
- Error handling (network, parse, timeout)
- Unit tests with Ktor MockEngine

### Room Schema Change
- `@Entity` + `@PrimaryKey`
- TypeConverters if needed
- DAO with suspend functions & Flow
- Repository interface + implementation
- Hilt module to provide DAO + DB
- Migration if schema changes
- Test for DAO using in-memory DB

### Testing
- JUnit4; coroutineTestRule + Turbine if Flow used
- Given/When/Then style
- Fake repository or MockK as needed (prefer fakes over mocks)
- Coverage of success + error cases
- Pragmatic duplication of fakes between `test/` and `androidTest/` is acceptable

---

## PR & Change Rules
- Propose diffs, not full file rewrites
- Provide small, reviewable diffs; include rationale in comments
- Don't rename packages or reorganize modules without asking first
- For schema changes: include migration + in-memory DAO tests
- For major architectural decisions: write an ADR in `docs/adrs/` following existing format
- When unsure: ask clarifying questions before large refactors; suggest alternatives with tradeoffs

## Verification After Code Changes
After completing any code change (bug fix, feature, refactor), always run the unit tests as a final verification step:

```
./gradlew :app:testDebugUnitTest
```

- Report the number of tests passed/failed
- If any tests fail, fix them before considering the task done
- Do not ask the user whether to run tests — just run them

---

## Current Gaps (Aug 2026)
| Area | Status                                                                              |
|------|-------------------------------------------------------------------------------------|
| HomeScreen | Server-driven UI — `GET /api/v1/home/layout` returns components + a sidecar; the sidecar itself is a static server-side JSON fixture (`HomeLayoutService.loadSidecarJson()`), not per-user personalization yet. |
| Meal Plans — Android UI | Done. Wizard + list screen + **detail screen**: tapping a plan card opens a day-grouped week view. Each meal is a compact row (`MealPlanMealRow`) that opens the recipe, with a chef-hat toggle (`core/ui/components/CookedToggleButton`, shared) that marks it cooked; cooked meals drop to a dimmed "Cooked this week" section at the bottom and a progress bar tracks the week (also shown on the list card). The recipe details screen shows the same toggle when opened from a meal plan slot — the `MEAL_PLAN_RECIPE_DETAIL` route carries `mealPlanDayId`/`mealPlanSlot` nav args alongside the recipe id (a recipe can fill more than one slot in a week, so the day+slot pair, not the recipe id, is what a toggle acts on); plain recipe details opened any other way has no toggle. Cooked state is per **slot** (`meal_plan_days.dinnerCookedAt`/`lunchCookedAt`, DB v6, `MIGRATION_5_6`), local-only — the sync payload has no field for it, so `SyncOrchestrator.applyPulledMealPlan` carries marks forward by `dayIndex` and only when the slot still holds the same recipe. |
| Meal Plans — Backend sync | Client side is wired (push/pull in `SyncOrchestrator`, `SyncMealPlanDto`); backend spec in `docs/prompts/meal-plans-backend-prompt.md`. **Gap: `applyPulledMealPlan` is skipped when there is no authenticated user** (the FK needs one), so an anonymous session can never receive a server-generated plan. |
| Meal Plans — AI generation | Backend `POST /meal-plans/{id}/generate` still outstanding. An on-device fallback now fills plans regardless: `LocalMealPlanGenerator` + the pure `MealPlanScheduler` deal the user's own recipes into the week, honouring plan length, meal type, max prep time, variety, and dietary restrictions (matched textually against tags/labels, falling back to the unfiltered pool rather than returning a short plan). Both the wizard and the detail screen try the server first and fall back — including when the call succeeds but delivers no days, which is what an anonymous session sees. Server results overwrite local ones on the next pull. |
| Meal Plans — Android sync wiring | Done — `SyncOrchestrator` pushes dirty plans and applies pulled ones; `SyncDtos` carries `SyncMealPlanDto`/`SyncMealPlanDayDto`. Cooked timestamps are deliberately **not** in the payload (see the UI row above). |
| Conflict resolution | SyncState.CONFLICT defined but unused                                     |
| RecipesViewModel user wiring | Done — resolves the real user via `SessionManager.userSession` (`Anonymous`/`Authenticated`), no hardcoded UUID. |
| Recipe search | Done (#151, #166–#170, #186–#188). Full-text search over title/tags/labels. Backend: `GET /api/v1/recipes/search`, Postgres `tsvector`/GIN (generated column, no extension), tag/label match via a CTE, rate-limited. Android: its own bottom-nav tab (`SearchScreen`, second slot) rather than a Home overlay. An M3 `SearchBar` (debounced 300ms, 3-char minimum) pins over a browse landing page (`SearchBrowseContent`) — a static, client-side catalog (`SearchCategory`, grouped by meal and by popularity) of gradient, image-free cards. Tapping a card feeds its English query term into the same `RecipeSearchViewModel` → `RecipeSearchRepository` pipeline typing does; results map straight to the domain `RecipePreview` (no Room write). **#187**: anonymous sessions now search the same backend PUBLIC catalog as authenticated ones (the endpoint no longer 401s without a JWT) — the on-device `LIKE` scan is purely the offline/network-failure fallback for every session now, not an anonymous-only default. Results reuse `RecipeListCard` (now takes `isInCollection`/`onSaveToCollection`/`modifier`); tapping saves inline or opens the existing detail screen. Full plan and rationale: `docs/prompts/search-tab-plan.md`. Deferred: the "Search by Ingredients" row (image-driven in the design, needs card art first), real card artwork/thumbnails, and a dedicated `search_results?q=…` destination if filter chips land. Filters (vegetarian, protein, …), typo tolerance (`pg_trgm`), and search history remain explicit follow-ups — see the plan at `~/.claude/plans/i-have-now-plenty-ticklish-raccoon.md`. **#186/#188 fixed**: opening or bookmarking a search result the device hadn't synced yet (common for anonymous sessions, which never pull) used to dead-end on a "not yet synced" snackbar forever. `RecipesRepository.getOrFetchRecipe` now falls back to the backend's anonymous-capable `GET /api/v1/recipes/{recipeId}` (full aggregate + FK reference data, same shape as a pull page) and persists via a new `SyncOrchestrator.fetchAndPersistRecipe`, reusing `upsertRecipeAggregate` — before `RecipeSearchViewModel.onRecipeClick`/`onSaveToCollection` proceed. |
| Recipe URL import | Done — paste a URL, scrape via `:recipe-scraper` (JSON-LD/microdata), pre-fill the editor. Hero image is downloaded and cached on-device at import time (`CacheRecipeImage`, ADR-010 Decision 6) — same two-tier ladder as the HTML fetch, so it survives the CDNs that block plain HTTP. No per-site scrapers, no nutrition data. Share-target intent (T14) done (#126). SSRF guard hardened (#179, ADR-010 Decision 7) — resolver-backed host validation (DNS rebinding + IPv6 coverage) and per-hop redirect re-validation. |
| Recipe images — cross-device | Done (ADR-011 Stage 1, ADR-012 Stage 2). Bytes never ride the sync payload; a server-owned `imageBlobId` content hash does. Resolution ladder: local file → authenticated GET from ChefAI → HTTP/WebView scrape ladder. **Invariant: blank `imageUrl` ⟺ the image is the user's own and cannot be re-derived.** Blob bookkeeping lives in `recipe_image_state`, never on `recipes`. |
| Recipe images — upload | Done (ADR-012, #94/#132). **Every** image is uploaded, scraped as well as user-taken, by `RecipeImageUploadWorker` (unmetered, *not* charging-only) after a successful sync, gated on `syncState = 'SYNCED'` so the recipe exists server-side first. Serving follows recipe visibility, not provenance. Publicly serving scraped images is an accepted posture revisited at ~50 users ([#144](https://github.com/jmuci/ChefAI/issues/144)); two server config flags reverse it without a client release. Backend spec: `docs/prompts/recipe-image-upload-backend-prompt.md`. Still missing: real thumbnails (`imageUrlThumbnail` is set to `imageUrl` and is a lie), and deletion is still not undoable — the local file goes at once, the server copy survives 30 days. |
| Recipe delete | Done — soft delete via a button on the recipe details screen. No undo, no list swipe-to-delete, no delete from the meal-plan recipe route. |
| Shopping lists | Done (#191–#193). Per-meal-plan, not a standalone/freeform grocery list: `ShoppingListBuilder` (pure Kotlin, `mealplans/domain/shoppinglist/`) turns a plan's recipe ingredients into a grouped list — aggregated by normalized name, scaled by planned servings ÷ each recipe's own servings × how many slots that recipe fills that week, grouped into `GrocerySection`s in grocery-store walk order. Reachable via a FAB from the meal-plan detail screen (`ShoppingListScreen`). Checked-off state is local-only, keyed by `(mealPlanId, itemKey)` in `shopping_list_checks` (DB v7, `MIGRATION_6_7`, `ON DELETE CASCADE` from `meal_plans`) — no sync payload field, same local-only pattern as the meal-plan cooked toggle. [`docs/grocery-api-landscape.html`](docs/grocery-api-landscape.html)'s delivery-partner research (Instacart/Albertsons/Kroger cart APIs) is unrelated to this and remains unactioned. No ADR written yet for this feature. |
| Measurement units | Done (ADR-013). A profile setting — profile menu → **Settings** (`settings/ui/SettingsScreen`, the first screen behind the long-dormant `ScreenBaseRoutes.SETTINGS`) — reads recipes `As written` / `Metric` / `Imperial`. Applied **at display time only**, after scaling, by the pure `core/domain/units/` package (`UnitConversion`, `UnitNormalizer`, `IngredientDensity`, `IngredientAmountFormatter`); `recipe_ingredients` is never written and `unit` stays free-text `String` (its `//TODO Make enum` deliberately left open). Dimensional conversion is the floor; only ~40 curated staples cross cups↔grams, rendered with a leading `≈`, and suppressed for `chopped`/`grated`/`shredded`… names. Counting units and unrecognised ones never convert. The shopping list converts **before** aggregating, so `1 cup flour` + `125 g flour` becomes one line. Preference is device-local DataStore (`chefai_user_prefs`), not synced, default `AS_WRITTEN`. **Not covered: step text**, temperatures included — no per-recipe override either. |
| Recipe step timer | (#202, #206, #208) Core done, manually verified on an emulator. `parseStepDurationSeconds` (`core/util/StepDurationParser.kt`) regex-extracts a duration from a step's free text ("Bake for 30 minutes" → 1800s); a clock icon shows next to any step it can parse and starts the app-wide `RecipeTimerController` singleton (`core/data/timer/`, `@ApplicationScope`-scoped so it outlives the screen that started it and survives navigation). Its tick loop deliberately runs on `Dispatchers.Main` rather than the shared background dispatcher — `start`/`pause`/`resume`/`cancel` are all called from Compose click handlers (already Main), so confining the ticks there too makes every mutation single-threaded by construction with no lock (#208 fixed a real race where a `pause()` could be silently clobbered back to "running" by an in-flight tick). `start()` returns the `RecipeTimerState?` it replaced, driving a "Replaced the timer for Step N" toast when a second step's timer preempts the first. A `FloatingRecipeTimerWidget` (`core/ui/timer/`, split into a stateless previewable content composable + a thin ViewModel-resolving entry point), hosted once above `ChefAINavGraph` in `MainActivity`, shows the countdown with drag-to-reposition (position now survives a stop/start cycle and re-clamps on rotation, not just reset on process death), pause/resume, and cancel; `RecipeTimerNotifier` posts a notification on completion (requests `POST_NOTIFICATIONS` on API 33+ right before the first timer starts), covered by this repo's first Robolectric tests (`RecipeTimerNotifierTest` — Robolectric added as a test-only dependency for this). Known limitations, not yet done: only one timer can run at a time (starting a second replaces the first, now at least surfaced via the toast above); the widget's dragged position and the timer itself do **not** survive process death (no persistence layer — the bonus "survives app restart" and "system widget when minimized" acceptance criteria are unimplemented); "overnight"/"until golden brown"-style non-numeric durations are unsupported by design. |

**Next milestone**: Complete all tasks to prepare for Monstro Demo

---

## Skills (Task Playbooks)

When performing specific tasks, load the matching skill from `.claude/skills/`:

| Task | Skill File |
|------|-----------|
| Build a new Compose screen or component | [`.claude/skills/compose-component.md`](.claude/skills/compose-component.md) |
| Create or modify a ViewModel | [`.claude/skills/viewmodel.md`](.claude/skills/viewmodel.md) |
| Review code (PR or ad-hoc) | [`.claude/skills/code-review.md`](.claude/skills/code-review.md) |
| Modify existing code (bug fix, refactor, feature addition) | [`.claude/skills/update-code.md`](.claude/skills/update-code.md) |

---

## Reference Docs

### Project Docs
- [Architecture Overview](docs/architecture.md)
- [Authentication System](docs/authentication.md)
- [Sync Deep Dive](docs/sync-deep-dive.md) — step-by-step push/pull flow, scenarios, manual test checklist
- [RFC-001: Offline-First Sync](docs/rfcs/rfc-001-offline-first-sync.md) — superseded by ADR-006/007/008; kept for historical context
- ADRs in [docs/adrs/](docs/adrs/)

### Claude Docs
- [Decisions (ADR Index)](docs/claude/decisions.md)
- [Code Conventions](docs/claude/conventions.md)
- [Gotchas & Lessons](docs/claude/gotchas.md)
- [Session Onboarding](docs/claude/onboarding.md)
