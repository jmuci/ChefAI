# Plan: Anonymous-Capable Meal-Plan Generation

> Written: August 2026. Spans **two repositories** — `ktor-chefai` (Phase 1) and `ChefAI` (Phase 2).
> Execute phases in order; Phase 2 depends on Phase 1's endpoint existing.
>
> This plan is written to be run step by step. Each step names the exact files to touch and ends
> with a verification gate. Do not proceed past a failing gate.

---

## 0. What is already true (do not re-investigate)

Verified against `Chef.ai_Backend@88b7b35` and this repo's `main`. **These correct several stale
claims in `CLAUDE.md` — trust this section over that table.**

1. **`POST /meal-plans/{id}/generate` is already implemented and tested.**
   `src/main/kotlin/domain/service/MealPlanGenerationService.kt` + `presentation/routes/MealPlanRoutes.kt`,
   19 unit tests in `src/test/kotlin/domain/service/MealPlanGenerationServiceTest.kt`. It does
   candidate SQL filtering, ranking metadata, leftover-aware ordering, dominant-ingredient spacing,
   and cross-plan history. **Do not rewrite it. This plan reuses `assignRecipesToDays` verbatim.**

2. **It contains no LLM.** It is deterministic SQL + a rule-based assignment loop, despite the
   "AI-style" wording in its KDoc. Adding an LLM is explicitly **out of scope** here.

3. **The `COLLECTION_ONLY` bookmark leak is already fixed** (`6ddf7f8`, backend #57). The prompt doc
   `docs/prompts/meal-plan-generation-collection-only-backend-prompt.md` describes a fixed bug and is
   stale — Step 14 retires it.

4. **The anonymous blocker is HTTP-level, not the FK.** `SessionManager.getCurrentUserId()` returns
   the `localUserId` for `Anonymous` sessions, so the `authenticatedUserId != null` guard in
   `SyncOrchestrator.pull()` is **non-null for anonymous** and only skips during `Loading`. The real
   blocker is `Routing.kt`: `syncRoutes` and `mealPlanRoutes` sit inside `authenticate("auth-jwt")`,
   so anonymous devices 401 on push, pull, *and* generate — they never reach `applyPulledMealPlan`.
   Comments in `SyncOrchestrator.kt`, `CreateMealPlanViewModel.kt`, and `LocalMealPlanGenerator.kt`
   all repeat the wrong explanation. Step 14 fixes them.

5. **The defect that actually matters:** an anonymous device generates from ~16 recipes (the
   `home_sidecar.json` fixture) plus imports. The server catalog holds **789** PUBLIC recipes
   (`seed_themealdb.sql`). A 7-day plan off 16 candidates is visibly repetitive. Closing that gap is
   the entire point of this work.

6. **This is the fifth instance of an established pattern.** Home layout (#58), recipe search (#59),
   single-recipe fetch (#60), and their smoke tests (#63) all made an endpoint anonymous-capable via
   `authenticate("auth-jwt", optional = true)`. Follow those, especially
   `presentation/routes/RecipeSearchRoutes.kt` and `RecipeDetailRoutes.kt`.

---

## 1. The design, in one rule

A new **stateless** endpoint. No plan id, no persistence, no `meal_plans` row, therefore no
`user_id` FK — which is exactly how the anonymous problem dissolves rather than being worked around.

Client routing, decided by session type **and** `recipeSource`:

| Session | `recipeSource` | Path |
|---|---|---|
| Authenticated | either | **Unchanged.** `sync()` → `POST /meal-plans/{id}/generate` → poll → `sync()` |
| Anonymous | `INCLUDE_PUBLIC` | **New.** `POST /api/v1/meal-plans/generate`, persist days locally |
| Anonymous | `COLLECTION_ONLY` | **`LocalMealPlanGenerator`.** Skip the network entirely |

The last row is not a fallback, it is the correct answer: for an anonymous user "my collection" exists
only on the device, so the server cannot answer that question and should not be asked. Anonymous +
`INCLUDE_PUBLIC` is where the 16-vs-789 win lives.

`LocalMealPlanGenerator` remains the offline/error fallback for every row.

**Response carries the recipes inline.** A 7-day `DINNER_AND_LUNCH` plan references up to 14 recipes
the anonymous device has never seen. Returning day assignments alone would force 14 follow-up
`GET /api/v1/recipes/{id}` calls. Instead the response is shaped like a pull page — `days` plus
`recipes` / `referenceData` / `creators` — so the client reuses its existing aggregate-upsert code
and makes exactly one round trip.

---

# PHASE 1 — Backend (`ktor-chefai`)

Working directory: `/Users/jmucientes/Projects/ktor-chefai`
**This is a different repository from the one this plan file lives in.**

### Step 1 — Sync the clone

The local clone is behind origin and has an untracked `src/main/resources/sql/seed_themealdb.sql`.

```bash
cd /Users/jmucientes/Projects/ktor-chefai && git status && git pull --ff-only origin main
```

Branch off `main`. Do not delete or commit the untracked seed file — leave it as you found it.

**Gate:** `git log --oneline -1` shows `88b7b35` or later.

---

### Step 2 — Widen the candidate query to accept an anonymous caller

**File:** `src/main/kotlin/domain/repository/SyncRepository.kt`

Change `findCandidateRecipeIds`'s first parameter to nullable:

```kotlin
suspend fun findCandidateRecipeIds(
    userId: UUID?,                    // was UUID
    recipeSource: String,
    dietaryRestrictionTags: List<String>,
    maxPrepTimeMinutes: Int?
): List<UUID>
```

Update the KDoc: a null `userId` means an anonymous caller and scopes candidates to
`privacy = 'PUBLIC' AND deleted_at IS NULL`; `COLLECTION_ONLY` is not meaningful for a null user and
is treated as `INCLUDE_PUBLIC`.

**File:** `src/main/kotlin/infrastructure/database/repositoryImpl/PostgresSyncRepository.kt`

In the `when (recipeSource)` block (~line 583):

- `"COLLECTION_ONLY"` branch: guard with `if (userId != null)`. When null, fall through to the public
  branch — do not attempt the bookmark query with a null user.
- `else` (INCLUDE_PUBLIC) branch: when `userId` is null, drop the `creator_id eq ...` disjunct and
  filter on `RecipeTable.privacy eq "PUBLIC"` alone.

Steps 3 (dietary label intersection) and 4 (prep-time constraint) are already user-independent —
**leave them untouched.**

**Two fakes implement this interface** and both need the signature widened (each ignores `userId`, so
it is a one-line change in each):
- `src/test/kotlin/domain/service/FakeSyncRepository.kt`
- `src/test/kotlin/infrastructure/database/FakeSyncRepository.kt`

**Gate:** `./gradlew test` compiles and passes as before. No behavior change yet for authenticated callers.

---

### Step 3 — Add the stateless generation entry point to the service

**File:** `src/main/kotlin/domain/service/MealPlanGenerationService.kt`

Add one public method alongside `startGeneration`. **Do not modify `startGeneration`,
`assignRecipesToDays`, `pickFrom`, `rankForDay`, `leftoverAwareShuffle`, or `parsePreferences`** —
they are reused as-is and their 19 tests must keep passing untouched.

```kotlin
/**
 * Generates a schedule without persisting anything — no plan row, no [meal_plan_days] write, no
 * ownership check. The caller (typically an anonymous device, [userId] null) persists the result
 * locally. Runs synchronously: there is no plan row for a client to poll, so unlike
 * [startGeneration] this returns the finished days rather than a 202.
 */
suspend fun generateStateless(userId: UUID?, preferencesJson: String): List<SyncMealPlanDayDto>
```

Body: `parsePreferences` → `findCandidateRecipeIds(userId, ...)` → `findRecipeRankingMetadata` →
`assignRecipesToDays(candidateIds, prefs, rankingMetadata, recentlyUsedElsewhere = emptySet())`.

`findRecentlyUsedRecipeIds` requires a `userId` **and** an `excludePlanId`, and there is no plan here
— pass `emptySet()` for anonymous. For an authenticated caller of this endpoint you may still pass
`emptySet()`; cross-plan history is a refinement the stateless path does not need.

**Gate:** `./gradlew test` — the existing 19 generation tests still pass unmodified.

---

### Step 4 — Response DTO

**File:** `src/main/kotlin/application/dto/SyncDtos.kt`

Add next to `RecipeDetailResponse` (~line 186), reusing its types so the client can run its existing
aggregate-upsert path:

```kotlin
/**
 * Response for `POST /api/v1/meal-plans/generate` — the anonymous-capable stateless generator.
 * Shaped like a pull page rather than a bare day list: an anonymous device has typically never
 * received the assigned recipes, and a 7-day DINNER_AND_LUNCH plan would otherwise need 14
 * follow-up GET /api/v1/recipes/{id} calls. [creators] is separate from [referenceData] for the
 * same FK-safety reason as [RecipeDetailResponse.creators].
 */
@Serializable
data class GenerateMealPlanStatelessResponse(
    val days: List<SyncMealPlanDayDto>,
    val recipes: List<SyncRecipe>,
    val referenceData: SyncReferenceData,
    val creators: List<SyncUser>
)
```

Also add the request DTO. Accept the preferences as the same JSON string the client already stores
and pushes (`SyncMealPlanDto.preferencesJson`), so no new parsing contract is introduced:

```kotlin
@Serializable
data class GenerateMealPlanStatelessRequest(val preferencesJson: String)
```

**Gate:** compiles.

---

### Step 5 — Assemble the aggregates

`generateStateless` returns day assignments only; the route needs full recipe aggregates for the
referenced ids.

`SyncService.getRecipeDetail(userId, recipeId)` already returns exactly one recipe's aggregate +
reference data + creators (it backs `GET /api/v1/recipes/{recipeId}`). Reuse it: collect the distinct
non-null recipe ids across the generated days, call it per id, and merge — union the reference-data
lists and creators, de-duplicating by id.

Put this assembly in `SyncService` (not the route) as e.g. `getRecipeDetails(userId, ids: Set<UUID>)`
so it is unit-testable. If per-id calls prove too chatty against Postgres, a batched query is a later
optimization — correctness first, and a plan references at most 14 recipes.

**Gate:** a unit test asserting the merge de-duplicates shared tags/labels/ingredients across two
recipes rather than repeating them.

---

### Step 6 — Route, mounted under optional auth

**File:** `src/main/kotlin/presentation/routes/MealPlanRoutes.kt`

Add a `Route.mealPlanGenerationRoutes(...)` function — **separate from `mealPlanRoutes`**, because the
two mount under different authentication blocks.

Model it on `RecipeSearchRoutes.kt`:

- `post("/api/v1/meal-plans/generate")`
- `val userId: UUID? = parseUuidOrNull(call.userId)` — null is **not** an error here; comment it the
  way `RecipeSearchRoutes.kt` does
- Receive `GenerateMealPlanStatelessRequest`; `400` on a blank/unparseable `preferencesJson`
- `200 OK` with `GenerateMealPlanStatelessResponse`
- `try/catch` → `500` with `ErrorResponse`, logging via `call.application.environment.log.error`
- Declare its own `val MEAL_PLAN_GENERATE_RATE_LIMIT_NAME = RateLimitName("meal-plan-generate")` and
  wrap the route in `rateLimit(...)`

**File:** `src/main/kotlin/presentation/routes/Routing.kt`

Register the limiter beside the existing ones. Generation is far heavier than search — a lower limit
is right. Key on `call.userId ?: call.request.origin.remoteAddress`, with the same rationale comment
the search limiter carries (a literal `"anonymous"` key would put every signed-out device in one
bucket):

```kotlin
register(MEAL_PLAN_GENERATE_RATE_LIMIT_NAME) {
    rateLimiter(limit = 10, refillPeriod = 60.seconds)
    requestKey { call -> call.userId ?: call.request.origin.remoteAddress }
}
```

Mount the route inside the **existing** `authenticate("auth-jwt", optional = true)` block, alongside
`recipeSearchRoutes` and `recipeDetailRoutes`. Extend that block's comment to cover it.

**Leave `mealPlanRoutes(mealPlanGenerationService)` where it is**, inside `authenticate("auth-jwt")`.
The authenticated flow is unchanged.

**Gate:** `./gradlew test`.

---

### Step 7 — Tests

**Unit** — `src/test/kotlin/domain/service/MealPlanGenerationServiceTest.kt` (add; do not edit existing tests):
- `generateStateless` with a null `userId` returns `planLengthDays` days
- with no candidates, returns days with null slots rather than an empty list or a throw
- honors `mealType = DINNER` by leaving every `lunchRecipeId` null

**Integration** — new `src/test/kotlin/infrastructure/auth/MealPlanGenerationRoutesIntegrationTest.kt`,
modeled on `RecipeSearchRoutesIntegrationTest.kt` / `RecipeDetailRoutesIntegrationTest.kt`:
- **no `Authorization` header → `200`, not `401`** (this is the whole point of the change — assert it explicitly)
- a valid JWT also → `200`
- malformed `preferencesJson` → `400`
- every returned `dinnerRecipeId`/`lunchRecipeId` appears in the response's own `recipes` list
  (the client cannot resolve a reference that isn't there — this is the regression guard for the
  class of bug backend #57 fixed)

**DB integration** — `src/test/kotlin/infrastructure/database/integration/PostgresSyncRepositoryIntegrationTest.kt`:
- `findCandidateRecipeIds(userId = null, ...)` returns only `privacy = 'PUBLIC'`, non-deleted recipes,
  and excludes another user's PRIVATE recipe

**Gate:**
```bash
./gradlew test && ./gradlew dbIntegrationTest
```

Commit, push, open a PR against `jmuci/Chef.ai_Backend`. **Phase 2 needs this deployed** to verify
end-to-end; the client work can be written against `MockEngine` before then.

---

# PHASE 2 — Android (`ChefAI`)

Working directory: this repository / worktree.

### Step 8 — DTOs

**File:** `app/src/main/java/com/tenmilelabs/chefai/core/data/sync/network/dto/SyncDtos.kt`

Add `GenerateMealPlanStatelessRequest` and `GenerateMealPlanStatelessResponse` mirroring Step 4.
Reuse the existing `SyncRecipeDto`, `SyncReferenceDataDto`, and creator DTOs that
`RecipeDetailResponseDto` already uses — **do not define parallel types.**

---

### Step 9 — Network layer

**File:** `app/src/main/java/com/tenmilelabs/chefai/mealplans/data/network/MealPlanNetworkDataSource.kt`

```kotlin
suspend fun generateStateless(preferencesJson: String): GenerateMealPlanStatelessResponse
```

**File:** `.../mealplans/data/network/MealPlanApiService.kt`

Implement it. Model the error handling on `RecipeDetailApiService` rather than the existing
`generateMealPlan` in this same file: prefer a sealed result over a thrown exception, apply an
explicit `timeout { requestTimeoutMillis = ... }` (the shared client sets no default), and rethrow
`CancellationException`. Generation is slower than a recipe fetch — budget accordingly.

`AuthInterceptor` attaches `Authorization` only when a token exists, so an anonymous request goes out
with no header and the optional-auth mount accepts it. No client auth changes needed.

---

### Step 10 — Persist the result

**File:** `app/src/main/java/com/tenmilelabs/chefai/core/data/sync/SyncOrchestrator.kt`

Add a method beside `fetchAndPersistRecipe` (~line 356), which is the template — it already upserts
creators, allergens, source classifications, ingredients, tags, and labels **in FK dependency order**
before calling `upsertRecipeAggregate`. Preserve that ordering exactly; getting it wrong surfaces as
an FK constraint failure at runtime, not at compile time.

```kotlin
/** Persists the recipes carried by a stateless generation response, in FK dependency order. */
suspend fun persistGeneratedRecipes(response: GenerateMealPlanStatelessResponse)
```

Wrap in `transactionRunner { }`, same as `fetchAndPersistRecipe`.

**File:** `.../mealplans/domain/repository/MealPlanRepository.kt` and its `Default` impl

```kotlin
/**
 * Fills [planId] from a server-generated stateless schedule, persisting any recipes the device
 * does not have yet. Used by anonymous sessions, whose plans the server cannot hold.
 */
suspend fun generateStatelessAndSave(planId: UUID, preferencesJson: String): Result<Int>
```

Implementation: call the data source → `persistGeneratedRecipes` → map days to domain →
`saveLocallyGeneratedDays(planId, days)` (which already sets `READY` and marks the plan dirty).

---

### Step 11 — Route by session type

**File:** `.../mealplans/ui/create/CreateMealPlanViewModel.kt`

`generateRemotely` (~line 162) currently runs `sync()` → `requestGeneration` → `delay` → `sync()`.
For an anonymous session every one of those 401s, so the whole sequence is dead weight that costs a
user two failed round trips before the local fallback runs.

Branch **before** that sequence, implementing the table in §1:

```kotlin
when {
    session is UserSession.Anonymous &&
        prefs.recipeSource == RecipeSource.COLLECTION_ONLY -> generateLocally(mealPlanId)
    session is UserSession.Anonymous -> generateStateless(mealPlanId, prefs)
    else -> generateRemotely(mealPlanId)   // unchanged
}
```

Keep `generateLocally` as the fallback when the chosen path returns false.

**File:** `.../mealplans/ui/detail/MealPlanDetailViewModel.kt`

Apply the same branch to its `generateRemotely` (~line 205) — the regenerate button on the detail
screen has the identical problem. Consider extracting the decision into a small shared use case in
`mealplans/domain/usecase/` rather than duplicating it across two ViewModels.

**Do not** change `SessionManager`, `AuthInterceptor`, or the authenticated path.

---

### Step 12 — Tests

- `MealPlanApiServiceTest` — Ktor `MockEngine`: success, `400`, `500`, timeout
- `CreateMealPlanViewModelTest` — one test per row of the §1 table; assert anonymous +
  `COLLECTION_ONLY` **never touches the network**, and that anonymous + `INCLUDE_PUBLIC` never calls
  `syncExecutor.sync()` or `requestGeneration`
- `MealPlanDetailViewModelTest` — same branch coverage
- `SyncOrchestratorTest` — `persistGeneratedRecipes` writes reference data before recipes; a recipe
  already present locally is not duplicated

Prefer fakes over mocks, per `CLAUDE.md`.

**Gate (required by `CLAUDE.md`, do not skip and do not ask first):**
```bash
./gradlew :app:testDebugUnitTest
```
Report the pass/fail count. Baseline before this work was 381 tests passing.

---

### Step 13 — Verify on a device

The unit tests cannot catch the thing most likely to break: FK ordering when persisting recipes into
an anonymous device's near-empty Room DB.

On a **fresh install** (anonymous session, no login):
1. Meal Plans → create a 7-day `DINNER_AND_LUNCH` plan with `INCLUDE_PUBLIC`
2. Confirm the week fills with **varied** recipes — the failure mode being fixed is 16 recipes cycling
3. Tap through to a recipe: full ingredients and steps, not an empty shell
4. Airplane mode → create another plan → confirm it still fills via `LocalMealPlanGenerator`
5. Create a plan with `COLLECTION_ONLY` → confirm it draws only from your own recipes

---

# PHASE 3 — Retire the stale docs

### Step 14 — Fix what this investigation proved wrong

Do this even if Phases 1–2 are descoped; the docs are actively misleading.

**`CLAUDE.md`**, "Current Gaps" table:
- *Meal Plans — AI generation*: drop "Backend `POST /meal-plans/{id}/generate` still outstanding" —
  it shipped. Describe what exists, note it is rule-based rather than an LLM, and record the new
  stateless endpoint and the §1 routing rule.
- *Meal Plans — Backend sync*: replace the claim that `applyPulledMealPlan` is skipped for anonymous
  sessions. The accurate statement is §0.4 — anonymous sessions 401 at the route level and never
  reach that code.

**Code comments repeating the wrong diagnosis:**
- `SyncOrchestrator.kt:312` — "Meal plans require an authenticated user for the FK". Also rename the
  misleading local `authenticatedUserId` (it holds the anonymous `localUserId` too) in both `push()`
  (~line 115) and `pull()` (~line 279).
- `CreateMealPlanViewModel.kt:158` — "an anonymous session has its pulled meal plans skipped"
- `LocalMealPlanGenerator.kt` KDoc — "anonymous sessions never receive pulled meal plans"

**`docs/prompts/meal-plan-generation-collection-only-backend-prompt.md`** — the bug it reports was
fixed in backend `6ddf7f8` (#57). Mark it resolved with a pointer to that commit, or delete it.

**Consider an ADR.** Anonymous-capable-endpoint-by-optional-auth is now the pattern behind #58, #59,
#60, and this change, and it has never been written down. `docs/adrs/` is the place.

---

## Out of scope

- **LLM-based generation.** Separate decision. It needs the Anthropic Java SDK (a new dependency —
  a hard stop requiring Jose's sign-off), changes the latency profile from milliseconds to seconds,
  and is a differentiation argument rather than a correctness one. Roughly $0.05/plan at Opus 5 rates.
- **Letting anonymous sessions push or pull.** That needs server-side device identity. The stateless
  endpoint exists precisely to avoid it.
- **Batching the per-id aggregate fetch in Step 5.** Optimize only if measurements justify it.
- **Raising the `home_sidecar.json` fixture above 16 recipes.** A cheaper partial substitute for this
  whole plan, not a complement to it.

## Definition of done

- [ ] `POST /api/v1/meal-plans/generate` returns `200` with **no** `Authorization` header
- [ ] Every recipe id in a response also appears in that response's `recipes` list
- [ ] `findCandidateRecipeIds(userId = null, …)` returns only PUBLIC, non-deleted recipes
- [ ] Anonymous + `INCLUDE_PUBLIC` fills a 7-day plan from the server catalog, verified on a device
- [ ] Anonymous + `COLLECTION_ONLY` makes no network call
- [ ] The authenticated path is byte-for-byte unchanged in behavior
- [ ] `./gradlew test` and `./gradlew dbIntegrationTest` pass (backend)
- [ ] `./gradlew :app:testDebugUnitTest` passes, count reported (Android)
- [ ] Stale docs and comments in Step 14 corrected
