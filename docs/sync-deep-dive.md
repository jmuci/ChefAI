# Sync Deep Dive: Step-by-Step Flow, Scenarios & Manual Testing

**Last updated:** 2026-03-08
**Related:** [ADR-006](adrs/adr-006-sync-protocol.md), [RFC-001](rfcs/rfc-001-offline-first-sync.md)

---

## 1. Key Components

| Component | Location | Role |
|-----------|----------|------|
| `SyncOrchestrator` | `core/data/sync/` | Coordinates push + pull logic |
| `SyncWorker` | `core/data/sync/worker/` | WorkManager worker; calls `SyncOrchestrator.sync()` |
| `SyncManager` / `SyncScheduler` | `core/data/sync/` | Schedules WorkManager jobs |
| `SyncNetworkDataSource` | `core/data/sync/network/` | HTTP calls to `/sync/push` and `/sync/pull` |
| `SyncMapper` | `core/data/sync/mapper/` | Maps between Room entities and sync DTOs |
| Room DAOs | `core/data/local/room/dao/` | Read/write local database |
| `SyncMetadataDao` | " | Persists the pull checkpoint (`serverTimestamp`) |

The `SyncState` enum drives everything:

```
PENDING  → dirty locally, not yet pushed
SYNCED   → in sync with backend
DELETED  → soft-deleted locally, pending server delete
CONFLICT → unused in v1 (reserved for future merge UI)
```

---

## 2. Push Flow — Step by Step

```
SyncWorker.doWork()
  └── SyncOrchestrator.sync()
        └── push()
```

### Step 1 — Collect dirty recipes

```kotlin
val dirtyRecipes = recipeDao.getAllDirty()
// Returns recipes where syncState IN ('PENDING', 'DELETED')
```

If nothing is dirty, push returns immediately with `PushResult(0, 0, 0)`.

### Step 2 — Build aggregates

For each dirty recipe, `buildSyncRecipeDto` assembles the full aggregate:

- `recipeStepDao.getStepsForRecipe(uuid)`
- `recipeIngredientDao.getIngredientsForRecipe(uuid)` — then **filtered**
- `recipeTagCrossRefDao.getTagsForRecipe(uuid)`
- `recipeLabelCrossRefDao.getLabelsForRecipe(uuid)`

**Ingredient filtering:** Only ingredient refs where the `ingredientId` exists in the local `ingredients` table with `syncState = SYNCED` are included. This ensures only server-known ingredients reach the backend. Refs referencing unknown or locally-seeded IDs are dropped with a `Timber.w`.

**NOTE** : Down the line we need to support client created ingredients. 

### Step 3 — Batch and POST

Recipes are grouped into batches of 50 and sent to `POST /sync/push`.

### Step 4 — Process response

The server responds with three buckets:

| Bucket | Client action |
|--------|--------------|
| `accepted` | Mark recipe + all children as `SYNCED`, stamp `updatedAt = serverUpdatedAt` |
| `conflicts` | Server version replaces local. Call `upsertRecipeAggregate(serverVersion)` |
| `errors` | Log warning. Recipe stays `PENDING` and will retry on next sync cycle |

---

## 3. Pull Flow — Step by Step

```
SyncWorker.doWork()
  └── SyncOrchestrator.sync()
        └── pull()
```

### Step 1 — Read checkpoint

```kotlin
val since = syncMetadataDao.getLastSyncedAt("recipes") ?: 0L
```

On first sync (or after a wipe), `since = 0` — a full fetch.

### Step 2 — GET from server

```
GET /sync/pull?since=<checkpoint>&limit=100
```

The response contains:

- `recipes` — full recipe aggregates (with steps, ingredients, tag/label IDs)
- `creators`, `allergens`, `source_classifications`, `ingredients`, `tags`, `labels` — reference data
- `serverTimestamp` — new checkpoint
- `hasMore` — whether another page exists

### Step 3 — Upsert reference data (FK order)

All reference tables are upserted **before** any recipe row, in dependency order:

```
creators				(no FKs)
allergens           (no FKs)
source_classifications  (no FKs)
ingredients         (FK → allergens, source_classifications)
tags                (no FKs)
labels              (no FKs)
```

This guarantees FK constraints are satisfied when recipe children are written.

### Step 4 — Apply each recipe

For each `SyncRecipeDto` in the response, `applyPulledRecipe` runs:

```
syncRecipe.deletedAt != null?
  ├── yes → soft-delete local copy (set deletedAt, syncState = SYNCED)
  └── no  → check local state:
        localRecipe == null          → upsertRecipeAggregate (new)
        localRecipe.syncState == SYNCED  → upsertRecipeAggregate (update)
        localRecipe.syncState == PENDING →
              server.updatedAt > local.updatedAt → server wins, upsertRecipeAggregate
              local.updatedAt >= server.updatedAt → skip (local will push next cycle)
```

### Step 5 — upsertRecipeAggregate

The FK `recipes.creatorId → users.uuid` is satisfied because `SessionManager` writes the authenticated user to the local `users` table during `login()`, `register()`, and `loadSession()` — before sync fires. 

The aggregate is written atomically within the outer Room transaction:

1. `recipeDao.upsertRecipe(recipe)`
2. Delete + re-insert all steps
3. Delete old ingredient refs; insert only those where `ingredientId` exists locally
4. Delete + re-insert tag cross-refs (validated against local tag table)
5. Delete + re-insert label cross-refs (validated against local label table)

### Step 6 — Advance checkpoint

```kotlin
syncMetadataDao.upsert(SyncMetadataEntity("recipes", response.serverTimestamp))
```

The loop repeats if `hasMore = true`.

---

## 4. Scenarios

### Scenario A — Happy path: offline create → sync

**Setup:** User is authenticated, network is restored after creating a recipe offline.

```
[Device]                              [Server]
  |                                      |
  | Create recipe (PENDING)              |
  |                                      |
  | -- POST /sync/push ----------------> |
  |                     accepted=[id]    |
  | <-- 200 ----------------------------- |
  |                                      |
  | Mark recipe SYNCED                   |
  |                                      |
  | -- GET /sync/pull?since=0 ---------> |
  |              recipes=[], hasMore=false|
  | <-- 200 ----------------------------- |
  |                                      |
  ✓ Sync complete
```

**Expected result:** Recipe `syncState = SYNCED`, `updatedAt` = server timestamp.

---

### Scenario B — Push conflict: same recipe edited on two devices

**Setup:** Recipe synced on Device A. User edits it on Device B (offline). Device A also edits it and syncs first.

```
[Device B]                            [Server]
  |                                      |
  | Recipe PENDING, updatedAt=1000       |
  | (server already has updatedAt=2000)  |
  |                                      |
  | -- POST /sync/push ----------------> |
  |   conflicts=[{uuid, serverVersion}]  |
  | <-- 200 ----------------------------- |
  |                                      |
  | upsertRecipeAggregate(serverVersion) |
  | Recipe title = server title          |
  | syncState = SYNCED                   |
  |                                      |
  ✓ Server wins (updatedAt 2000 > 1000)
```

**Expected result:** Local recipe replaced with the server version. Device B's edits are lost (last-writer-wins).

---

### Scenario C — Pull conflict: local is newer

**Setup:** User edits a recipe offline on Device A (PENDING, updatedAt=5000). The server pulls a delta with the same recipe at updatedAt=3000 (an older version from Device B that was synced earlier).

```
[Device A]                            [Server]
  |                                      |
  | Push: recipe PENDING, updatedAt=5000 |
  | (server returns error, recipe stays  |
  |  PENDING for this cycle)             |
  |                                      |
  | Pull: server sends recipe at         |
  |       updatedAt=3000                 |
  |                                      |
  | applyPulledRecipe:                   |
  |   local.syncState == PENDING         |
  |   server.updatedAt (3000) < local    |
  |         .updatedAt (5000)            |
  |   → skip, keep local                 |
  |                                      |
  ✓ Local wins (will push on next cycle)
```

**Expected result:** Local recipe stays `PENDING` with `updatedAt=5000`. Next sync attempt will push it.

---

### Scenario D — Post-wipe re-sync

**Setup:** DB was wiped (e.g., account switch per ADR-008). User logs back in. The server has 15 recipes for this user.

```
[Device]                              [Server]
  |                                      |
  | login() / loadSession():             |
  |   persistAuthenticatedUser()         |
  |   → writes user row to Room (SYNCED) |
  |                                      |
  | Push: nothing dirty                  |
  |   → PushResult(0, 0, 0)             |
  |                                      |
  | Pull: since=0 (no checkpoint)        |
  | -- GET /sync/pull?since=0 ---------> |
  |   recipes=15, ref data included      |
  | <-- 200 ----------------------------- |
  |                                      |
  | Upsert allergens, ingredients, etc.  |
  |                                      |
  | For each recipe:                     |
  |   creatorId FK satisfied (user row   |
  |   already in Room from login step)   |
  |   → upsertRecipeAggregate            |
  |                                      |
  ✓ 15 recipes restored, all SYNCED
```

**Expected result:** All server recipes appear locally. The authenticated user row is always present before pull fires. FK constraints never violated.

---

### Scenario E — Recipe with unknown ingredient IDs

**Setup:** A recipe exists locally with ingredient IDs that were seeded from test data and don't exist on the server.

```
[Device]                              [Server]
  |                                      |
  | buildSyncRecipeDto:                  |
  |   ingredientDao.getSyncedExistingIds |
  |   returns [] (no SYNCED match)       |
  |   → ingredients filtered to []       |
  |                                      |
  | -- POST /sync/push ----------------> |
  |   recipe DTO has ingredients=[]      |
  |                                      |
  |                 accepted=[id]        |
  | <-- 200 ----------------------------- |
  |                                      |
  | Recipe marked SYNCED                 |
  ✓ No INGREDIENT_NOT_FOUND error
```

**Expected result:** Recipe syncs successfully without ingredient refs. A `Timber.w` is logged on device indicating skipped refs.

---

## 5. Manual Testing Checklist

### Prerequisites

- Android emulator or device connected
- Backend server running at `http://10.0.2.2:8080` (emulator) or configured host
- Logcat filter: `SyncOrchestrator`, `SyncWorker`

---

### 5.1 Basic Sync

- [ ] **Create a recipe offline**
  1. Disable network (Airplane Mode)
  2. Create a recipe with title, steps, and ingredients
  3. Verify `syncState = PENDING` in Room (via DB Inspector or log)
  4. Enable network
  5. Verify sync fires automatically within ~5s (mutation debounce)
  6. Verify logcat: `Push: completed — accepted=1, conflicts=0, errors=0`
  7. Verify recipe `syncState = SYNCED` in Room

- [ ] **Pull a recipe created on another device / via API**
  1. Insert a recipe directly on the backend (via API or DB)
  2. Bring app to foreground (triggers immediate sync)
  3. Verify logcat: `Pull: received N recipes`
  4. Verify recipe appears in the app

- [ ] **Edit a synced recipe offline then sync**
  1. Ensure a recipe is `SYNCED`
  2. Disable network, edit the recipe
  3. Verify `syncState = PENDING`
  4. Enable network, wait for sync
  5. Verify `syncState = SYNCED`, `updatedAt` updated on both sides

---

### 5.2 Conflict Resolution

- [ ] **Push conflict — server newer wins**
  1. Have recipe `PENDING` locally with `updatedAt = T1`
  2. Configure server to return a conflict response with a recipe at `updatedAt = T2 > T1`
  3. Trigger sync
  4. Verify local recipe has server's title/content and `syncState = SYNCED`
  5. Verify logcat: `Push: completed — conflicts=1`

- [ ] **Pull conflict — local newer is preserved**
  1. Create a recipe locally with `PENDING` state and high `updatedAt`
  2. Configure server pull to return the same recipe UUID with lower `updatedAt`
  3. Trigger sync
  4. Verify local title unchanged and `syncState = PENDING`
  5. Verify logcat: no recipe replacement for that UUID

---

### 5.3 Pagination

- [ ] **Pull handles multiple pages**
  1. Ensure backend has > 100 recipes for the user
  2. Clear local DB (or use fresh account)
  3. Trigger sync
  4. Verify logcat: `Pull: received 100 recipes, hasMore=true` then `Pull: received N recipes, hasMore=false`
  5. Verify all recipes appear locally

---

### 5.4 Post-Wipe Re-Sync (ADR-008 Scenarios)

- [ ] **Same user re-login after DB wipe**
  1. Login as User A, sync, then clear app data
  2. Login as User A again
  3. Trigger sync
  4. Verify logcat: `Pull: received N recipes`, no FK constraint errors
  5. Verify all recipes restored

- [ ] **Different user login causes DB wipe**
  1. Login as User A, create some recipes, sync
  2. Logout
  3. Login as User B (different account, no anonymous data)
  4. Verify DB cleared before sync
  5. Trigger sync
  6. Verify only User B's recipes are present

- [ ] **Logout → create anonymous recipes → login as new user**
  1. Login as User A, sync, logout
  2. Create 2 recipes anonymously (no account)
  3. Login as User B
  4. Verify User A's data was removed
  5. Verify the 2 anonymous recipes were preserved and upgraded to User B's account

---

### 5.5 Edge Cases

- [ ] **Recipe with seed/unknown ingredient IDs does not block sync**
  1. Inject a `RecipeIngredientEntity` with a non-SYNCED `ingredientId` directly into Room
  2. Trigger sync
  3. Verify logcat: `buildSyncRecipeDto: recipe X has N ingredient ref(s) not known to server, skipping`
  4. Verify push succeeds (`accepted=1`, not `errors=1`)
  5. Verify recipe is `SYNCED`

- [ ] **Sync retries on network failure**
  1. Start sync, cut network mid-flight (e.g., using Android emulator network settings)
  2. Verify logcat: `SyncWorker: Sync failed (attempt 1)`
  3. Verify WorkManager retries (check logcat for subsequent attempts)
  4. Restore network
  5. Verify sync eventually succeeds

- [ ] **Sync indicator shows correct states**
  1. Observe the TopAppBar `SyncStatusIndicator` during:
     - App offline → "Offline" indicator
     - Sync in progress → "Syncing" indicator
     - Sync complete → "Synced" indicator
     - Sync error → "Error" indicator with retry affordance

---

### 5.6 Sync Trigger Verification

- [ ] **Foreground trigger**: Background app, wait > 5s, bring to foreground → sync fires
- [ ] **Mutation trigger**: Edit a recipe → sync fires within 5s (debounce)
- [ ] **Periodic trigger**: Leave app running for 15+ minutes → sync fires automatically
- [ ] **Login trigger**: Logout and re-login → immediate sync fires

---

## 6. Debugging Tips

**Useful logcat tags:**
```
SyncOrchestrator  — push/pull progress and counts
SyncWorker        — WorkManager execution and retry state
SyncNetworkDataSource — raw HTTP request/response logging (debug builds only)
```

**Key log lines to look for:**
```
Push: Found N dirty recipes
Push: completed — accepted=X, conflicts=Y, errors=Z
Pull: starting from checkpoint=T
Pull: received N recipes, hasMore=true|false
Pull: completed — upserted=X, deleted=Y, pages=Z
buildSyncRecipeDto: recipe X has N ingredient ref(s) not known to server, skipping
Persisted authenticated user to Room: <uuid>   ← logged by SessionManager on login/loadSession
```

**DB Inspector queries (Android Studio):**
```sql
-- Check pending recipes
SELECT uuid, title, syncState, updatedAt FROM recipes WHERE syncState != 'SYNCED';

-- Verify authenticated user persisted at login
SELECT uuid, displayName, email, syncState FROM users WHERE syncState = 'SYNCED';

-- Check orphaned ingredient refs
SELECT ri.recipeId, ri.ingredientId
FROM recipe_ingredients ri
LEFT JOIN ingredients i ON ri.ingredientId = i.uuid
WHERE i.uuid IS NULL;

-- Check sync checkpoint
SELECT * FROM sync_metadata;
```
