# 🔄 ADR 006 – Sync Protocol: Logic, Triggers and Conflicts

**Date:** 2026-03-08 (updated from 2026-02-25)

**Status:** Accepted

**Context:** ChefAI must work fully offline, allow recipe creation without login, and sync data incrementally when connected. This ADR captures the key architectural decisions made during the implementation of RFC-001 (Phases 1–5) and subsequent refinements.

**Supersedes:** ADR-003 [adr-003–two-step-BE-sync](adr-003%E2%80%93two-step-BE-sync.md) (Two-Step Sync)

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

Before a recipe is inserted, the orchestrator checks that its `creatorId` exists in the local `users` table. If not, a stub user (`syncState = SYNCED`, empty display fields) is inserted. This handles post-wipe re-sync and public recipes authored by other server users.

### Batching & Pagination

- Push batches dirty recipes in groups of 50.
- Pull paginates via `hasMore` / `serverTimestamp` cursor.

### Rationale

- Push-before-pull reduces conflict frequency by ensuring the server sees local changes before reporting deltas.
- Aggregate-level sync maintains consistency without cross-table transactional sync.
- Ingredient filtering prevents server rejections from locally-seeded or legacy data.
- Creator stub insertion unblocks pull for cross-user recipes without a schema migration.
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

Sync is triggered at four points via `SyncScheduler` (backed by Android WorkManager):

| Trigger | Method | Behaviour |
|---------|--------|-----------|
| Login / Register | `requestImmediateSync()` + `schedulePeriodicSync()` | Immediate full sync, then periodic |
| App foreground | `requestImmediateSync()` | Via `ProcessLifecycleOwner.ON_START` |
| Local mutation | `requestMutationSync()` | 5-second debounce to batch rapid edits |
| Periodic | `schedulePeriodicSync()` | Every 15 minutes (WorkManager minimum) |
| Logout | `cancelAllSync()` | Cancels all pending and periodic work |

All work requests require network connectivity (`NetworkType.CONNECTED` constraint) and use **exponential backoff** (30s base, 3 retries → ~30s, ~60s, ~120s) on failure.

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
    fun schedulePeriodicSync()
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
- **Testable:** `SyncScheduler` and `ConnectivityObserver` interfaces enable 350+ unit tests with zero Android framework dependency.
- **Observable:** `SyncStatusIndicator` in the TopAppBar shows real-time sync state (syncing, synced, error, offline).
- **Resilient to data gaps:** Pull gracefully handles missing creator users via stub insertion; push skips unrecognized ingredient references.

### Trade-offs

- **Last-writer-wins can lose edits:** If two devices edit the same recipe simultaneously, the slower device's changes are silently overwritten. Acceptable for v1 single-user scenarios.
- **No real-time push:** Sync is poll-based (periodic + event-driven). A future WebSocket/FCM layer could reduce latency.
- **Aggregate-level granularity:** Editing a single step re-syncs the entire recipe aggregate. Fine for typical recipe sizes but may need refinement for very large entities.
- **Creator stubs are placeholder data:** Stub users have empty display fields and are not enriched unless a future user-profile sync endpoint is introduced.
- **Ingredient filtering is silent:** Ingredient refs dropped during push are not surfaced to the user. If this happens in production, it indicates a data integrity issue worth investigating.

---

## References

- [RFC-001: Offline-First Architecture & Sync Protocol](../rfcs/rfc-001-offline-first-sync.md)
- [Sync Deep Dive: Step-by-Step & Manual Testing](../sync-deep-dive.md)
- ADR-001: Hybrid Architecture Choice
- ADR-002: Local DB Choice & ID Generation
- ADR-003: Two-Step Synchronization (superseded by this ADR)
- ADR-007: Anonymous-First Session Model
- ADR-008: Local Database Behavior on Logout and Account Switch
