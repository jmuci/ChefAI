# 🔄 ADR 006 – Anonymous-First Session Model & Sync Protocol

**Date:** 2026-02-25
**Status:** Accepted
**Context:** ChefAI must work fully offline, allow recipe creation without login, and sync data incrementally when connected. This ADR captures the key architectural decisions made during the implementation of RFC-001 (Phases 1–5).
**Supersedes:** ADR-003 (Two-Step Sync)
**Related:** ADR-001 (Hybrid Architecture), ADR-002 (Local DB & ID Generation), RFC-001

---

## Decision 1: Anonymous-First Session Model

Users interact with the app immediately without registration. The session model has three states:

```kotlin
sealed class UserSession {
    data object Loading : UserSession()
    data class Anonymous(val localUserId: UUID) : UserSession()
    data class Authenticated(val user: User, val authToken: AuthToken) : UserSession()
}
```

- On first launch, a `UUIDv7` local user ID is generated, stored in `SecurePreferences`, and a corresponding `UserEntity` is inserted into Room.
- All recipes created offline use `creatorId = localUserId`.
- There is **no server-side anonymous user**. The backend only knows about a user after registration.
- Registration triggers an account-upgrade flow (`POST /auth/upgrade`) that re-parents all local recipes from the anonymous UUID to the new authenticated user UUID.

### Rationale

- Eliminates the login barrier — users can explore and create recipes instantly.
- UUIDv7 (client-generated, time-sortable) avoids the need for a server round-trip to assign IDs.
- Not creating server-side anonymous users keeps the backend simple and avoids ghost-account accumulation.

---

## Decision 2: Push-Before-Pull Sync Protocol

Sync follows a strict two-phase order:

1. **Push** — `POST /sync/push` sends all locally dirty entities (`syncState = PENDING` or `DELETED`) to the server.
2. **Pull** — `GET /sync/pull?since=<checkpoint>` fetches remote deltas since the client's last known `serverTimestamp`.

Push runs first so that the server has the client's latest state before computing the pull delta, avoiding unnecessary conflicts.

### Aggregate-Level Sync

Recipes are synced as **complete aggregates** (recipe + steps + ingredients + tags + labels) in a single DTO. This avoids partial-entity states on the server and simplifies conflict resolution — the server never sees an orphaned step without its parent recipe.

### Batching & Pagination

- Push batches dirty recipes in groups of 50.
- Pull paginates via `hasMore` / `serverTimestamp` cursor.

### Rationale

- Push-before-pull reduces conflict frequency by ensuring the server sees local changes before reporting deltas.
- Aggregate-level sync maintains data consistency without requiring cross-table transactional sync.
- Batching keeps HTTP payloads bounded for large offline editing sessions.

---

## Decision 3: Last-Writer-Wins Conflict Resolution

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

## Decision 4: Sync Lifecycle Triggers

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

## Decision 5: SyncScheduler Abstraction

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

- **Zero-friction onboarding:** Users create recipes immediately; registration is optional.
- **Full offline capability:** All CRUD operations work without connectivity.
- **Predictable sync:** Push-before-pull with last-writer-wins is deterministic and debuggable.
- **Testable:** `SyncScheduler` and `ConnectivityObserver` interfaces enable 169 unit tests with zero Android framework dependency.
- **Observable:** `SyncStatusIndicator` in the TopAppBar shows real-time sync state (syncing, synced, error, offline).

### Trade-offs

- **Last-writer-wins can lose edits:** If two devices edit the same recipe simultaneously, the slower device's changes are silently overwritten. Acceptable for v1 single-user scenarios.
- **No real-time push:** Sync is poll-based (periodic + event-driven). A future WebSocket/FCM layer could reduce latency.
- **Aggregate-level granularity:** Editing a single step re-syncs the entire recipe aggregate. Fine for typical recipe sizes but may need refinement for very large entities.
- **Anonymous data is local-only:** If the user uninstalls without registering, all anonymous data is lost. This is by design — the upgrade flow incentivizes registration.

---

## References

- [RFC-001: Offline-First Architecture & Sync Protocol](../rfcs/rfc-001-offline-first-sync.md)
- ADR-001: Hybrid Architecture Choice
- ADR-002: Local DB Choice & ID Generation
- ADR-003: Two-Step Synchronization (superseded by this ADR)
