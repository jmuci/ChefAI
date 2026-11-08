# 🔄 ADR 006 – Sync Protocol: Logic, Triggers and Conflicts

**Date:** 2026-03-08 (updated from 2026-02-25)

**Status:** Accepted

**Context:** ChefAI must work fully offline, allow recipe creation without login, and sync data incrementally when connected. This ADR captures the key architectural decisions made during the implementation of RFC-001 (Phases 1–5) and subsequent refinements.

**Supersedes and absorbs:** ADR-003 (Two-Step Sync) and ADR-004 (Data Layer Composition) — both are
now pointers to the **History** section at the end of this file.

**Related:** ADR-001 (Hybrid Architecture), ADR-002 (Local DB & ID Generation), ADR-007 (Anonymous-First), ADR-008 (Data Handling Across Sessions), RFC-001

---

## Decision 1: Push-Before-Pull Sync Protocol

Sync follows a strict two-phase order:

1. **Push** — `POST /sync/push` sends all locally dirty entities (`syncState = PENDING` or `DELETED`) to the server.
2. **Pull** — `GET /sync/pull?since=<checkpoint>` fetches remote deltas since the client's last known `serverTimestamp`.

Push runs first so that the server has the client's latest state before computing the pull delta, avoiding unnecessary conflicts.

### Aggregate-Level Sync

Recipes are synced as **complete aggregates** (recipe + steps + ingredients + tags + labels) in a single DTO. This avoids partial-entity states on the server and simplifies conflict resolution — the server never sees an orphaned step without its parent recipe.

### Push Ingredient Filtering

Push payloads only include ingredient refs where the ingredient has `syncState = SYNCED` in the local `ingredients` table — i.e., it was previously pulled from the server. Locally-seeded or unrecognized ingredient IDs are dropped with a warning log. This prevents `INGREDIENT_NOT_FOUND` rejections that would leave recipes stuck as `PENDING` indefinitely.

### Pull Reference Data Ordering

The pull response includes reference data alongside recipes. These are upserted in FK dependency order before any recipe row is written:

```
allergens → source_classifications → ingredients → tags → labels → recipes
```

The `recipes.creatorId → users.uuid` FK constraint is satisfied without any stub creation. `SessionManager` writes the authenticated user to the local `users` table during `login()`, `register()`, and `loadSession()` — before sync fires — so pulled recipes that reference the owner's `creatorId` always find a matching row.

### What a push carries (Nov 2026)

Recipes were the original scope. A push now also carries bookmarks (only the authenticated user's),
meal plans (+days), grocery-item checks (ADR-014), cook events (ADR-017) and saved meal plans
(ADR-018); the pull returns the same families. Recipes are the only batched family — the others
ride in the last batch.

### Batching & Pagination

- Push batches dirty recipes in groups of 50.
- Pull paginates via `hasMore` / `serverTimestamp` cursor.

### Rationale

- Push-before-pull reduces conflict frequency by ensuring the server sees local changes before reporting deltas.
- Aggregate-level sync maintains consistency without cross-table transactional sync.
- Ingredient filtering prevents server rejections from locally-seeded or legacy data.
- Persisting the authenticated user at login/register/loadSession satisfies the FK before pull fires, with no stub rows needed.
- Batching keeps HTTP payloads bounded for large offline editing sessions.

---

## Decision 2: Last-Writer-Wins Conflict Resolution

All conflicts are resolved by comparing `updatedAt` timestamps:

- **Push conflict:** If the server has a newer version (`stale_version`), the server version replaces the local copy. The client marks it as `SYNCED`.
- **Pull conflict:** If a pulled recipe overlaps with a local `PENDING` recipe:
  - Server newer (`updatedAt` > local) → server wins, local overwritten, marked `SYNCED`.
  - Local newer → local preserved as `PENDING`, will be pushed in the next cycle.

The server is **authoritative** once data is synced — there is no client-side merge or CRDT.

### Rationale

- Simple, deterministic, and easy to reason about.
- Sufficient for a single-user-multi-device scenario where simultaneous edits to the same recipe are rare.
- Avoids the complexity of operational transforms or CRDTs (a non-goal per RFC-001).

---

## Decision 3: Sync Lifecycle Triggers

Sync is triggered via `SyncScheduler` (backed by Android WorkManager). All one-time requests share
the unique work name `recipe_sync`:

| Trigger | Method | Policy | Behaviour |
|---------|--------|--------|-----------|
| Login / Register | `requestImmediateSync()` + `schedulePeriodicSync()` | KEEP | Immediate full sync, then periodic |
| App foreground | `requestImmediateSync()` | KEEP | Via `ProcessLifecycleOwner.ON_START` |
| Local mutation | `requestMutationSync()` | KEEP | 5-second initial delay to batch rapid edits |
| Bookmark change | `requestBookmarkSync()` | REPLACE | 5-second delay, reset by each tap |
| Pull-to-refresh | `requestManualSync()` | REPLACE | Never silently dropped behind queued work |
| Periodic | `schedulePeriodicSync()` | KEEP | Every **24 hours**, **charging + connected** only |
| Logout | `cancelAllSync()` | — | Cancels sync and both image sweeps |

REPLACE cancels a *running* worker too, not just a queued one; `SyncWorker` treats that
cancellation as "superseded" (status back to `Idle`), not as a failure. After a successful sync the
worker enqueues the image backfill and upload sweeps (`scheduleImageBackfill` /
`scheduleImageUpload`, ADR-012).

All work requests require network connectivity (`NetworkType.CONNECTED` constraint) and use **exponential backoff** (30s base; `SyncWorker` gives up after 3 retries) on failure.

*Amended (Nov 2026):* periodic sync was originally every 15 minutes. It is now daily on a charger —
foreground, mutation and pull-to-refresh triggers cover the interactive cases.

### Rationale

- Foreground sync ensures the user sees fresh data when opening the app.
- Mutation debounce avoids excessive sync traffic during rapid editing (e.g., adding multiple ingredients).
- Periodic sync catches changes from other devices even when the app is backgrounded.
- Exponential backoff avoids hammering a struggling server.

---

## Decision 4: SyncScheduler Abstraction

`SessionManager` and `DefaultRecipeRepository` depend on a `SyncScheduler` interface rather than `SyncManager` directly:

```kotlin
interface SyncScheduler {
    fun requestImmediateSync()
    fun requestMutationSync()
    fun requestBookmarkSync()
    fun requestManualSync()
    fun schedulePeriodicSync()
    fun scheduleImageBackfill()
    fun scheduleImageUpload()
    fun cancelAllSync()
}
```

`SyncManager` (which depends on `Context` and `WorkManager`) implements this interface. In tests, `FakeSyncManager` provides a lightweight implementation that counts method calls.

`SessionManager` accepts `SyncScheduler` via `Provider<SyncScheduler>` to avoid init-order issues — `SessionManager.init{}` calls `loadSession()` before the Hilt dependency graph is fully resolved.

### Rationale

- Decouples business logic from Android framework classes.
- Enables fast, hermetic unit tests without Robolectric or instrumented tests.
- `Provider<>` defers resolution, preventing circular-init crashes.

---

## Consequences

### Benefits

- **Full offline capability:** All CRUD operations work without connectivity.
- **Predictable sync:** Push-before-pull with last-writer-wins is deterministic and debuggable.
- **Testable:** `SyncScheduler` and `ConnectivityObserver` interfaces enable most of the 1000+ unit tests with zero Android framework dependency.
- **Observable:** `SyncStatusIndicator` in the TopAppBar shows real-time sync state (syncing, synced, error, offline).
- **Resilient to data gaps:** The authenticated user is persisted to Room at login so the FK is satisfied before pull fires; push skips unrecognized ingredient references.

### Trade-offs

- **Last-writer-wins can lose edits:** If two devices edit the same recipe simultaneously, the slower device's changes are silently overwritten. Acceptable for v1 single-user scenarios.
- **No real-time push:** Sync is poll-based (periodic + event-driven). A future WebSocket/FCM layer could reduce latency.
- **Aggregate-level granularity:** Editing a single step re-syncs the entire recipe aggregate. Fine for typical recipe sizes but may need refinement for very large entities.
- **Ingredient filtering is silent:** Ingredient refs dropped during push are not surfaced to the user. If this happens in production, it indicates a data integrity issue worth investigating.

---

## History: ADR-003 and ADR-004 (Nov 2025)

Both were written before the sync was built and were merged here in November 2026. Their files
remain as pointers; the original text is in git history.

**ADR-003 — Two-Step Synchronization.** Chose push (`POST /sync/push`) then pull
(`GET /sync/pull?since=<timestamp>`), last-writer-wins on `updatedAt`, WorkManager triggers on
startup, connectivity and local edits, and a `sync_metadata` checkpoint. All of that survives in
Decision 1 and the triggers above. What did not: an **Outbox table** queuing JSON mutation payloads.
Each `SyncableEntity` row's `syncState` (`PENDING`/`DELETED`) is the outbox, and
`SyncOrchestrator` queries `getAllDirty()` per table.

**ADR-004 — Data Layer Composition.** What held, and is still the rule:

- One repository per aggregate root (`RecipesRepository`, `MealPlanRepository`, …), interfaces in
  `domain/repository/`, implementations in `data/repository/`.
- Room is the local source of truth: all writes go to Room first, and the UI reads only Room flows.
- Repositories write Room and call `SyncScheduler`; they never push or pull.

What never landed and should not be copied: the `outbox` table above; a
`LocalDataSource`/`RemoteDataSource` interface pair per repository (repositories inject DAOs
directly, plus a feature-specific network data source only where they call the API outside sync);
and sync and conflict resolution inside repositories — that is `SyncOrchestrator`, driven by
`SyncWorker`. DI is Hilt; Koin was never used.

---

## References

- [RFC-001: Offline-First Architecture & Sync Protocol](../rfcs/rfc-001-offline-first-sync.md)
- [Sync Deep Dive: Step-by-Step & Manual Testing](../sync-deep-dive.md)
- ADR-001: Hybrid Architecture Choice
- ADR-002: Local DB Choice & ID Generation
- ADR-007: Anonymous-First Session Model
- ADR-008: Local Database Behavior on Logout and Account Switch
