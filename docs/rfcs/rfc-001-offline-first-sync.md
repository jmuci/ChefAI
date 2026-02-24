# RFC-001: Offline-First Architecture & Sync Protocol

**Authors:** Jose Mucientes

**Status:** Draft

**Date:** 2026-02-23

**Supersedes:** ADR-003 (Two-Step Sync), ADR-004 (Data Layer)

**Related:** ADR-001 (Hybrid Architecture), ADR-002 (Local DB & ID Generation)

---

## 1. Summary

This RFC proposes the design for making Pocket Chef truly offline-first. It covers three
interconnected concerns:

1. **Anonymous-first session model** — users use the app without login; data lives locally.
2. **Bi-directional sync protocol** — push local mutations to the backend, pull remote deltas.
3. **Anonymous → authenticated upgrade** — merge local anonymous data into a real account.

The goal is a system where the app works fully offline, syncs incrementally when connected, and
handles the transition from anonymous to authenticated cleanly.

---

## 2. Motivation & Context

### Current State

The Android client has solid offline *infrastructure*:

- All 11 Room entities implement `SyncableEntity` with `uuid`, `updatedAt`, `deletedAt`, `syncState`.
- DAOs expose `getDirty()` queries for entities with `syncState = PENDING`.
- UUIDv7 (client-generated, time-sortable) is used for all IDs.
- `RecipePrivacy` (PUBLIC/PRIVATE) controls visibility.
- Indices on `(syncState, updatedAt)` exist on every table.

However, **none of the sync logic is actually implemented**:

- The app requires login to function (no anonymous session).
- `RecipesViewModel` and `DefaultRecipeRepository` use a hardcoded test user UUID.
- There is no Outbox table, no `sync_metadata`, no `SyncWorker`, no WorkManager integration.
- The backend has auth endpoints (`/auth/register`, `/auth/login`, `/auth/refresh`) and basic
  recipe CRUD endpoints (`GET/POST/DELETE /recipes`), but no sync endpoints (`/sync/push`,
  `/sync/pull`). The existing CRUD endpoints are flat (no child entities) and do not support
  aggregate recipe sync.
- `SyncState.CONFLICT` is defined but has no handler.

### Why Now

Offline-first is a core product requirement. Without it:

- Users cannot use the app on the subway, while cooking, or in poor-connectivity kitchens.
- The anonymous-first model (our key differentiator) cannot exist.
- There is nothing to sync because the app cannot produce local data without a server connection.

---

## 3. Goals & Non-Goals

### Goals

- App is fully functional with zero network connectivity.
- Users create, browse, edit, and delete recipes without ever logging in.
- When users register/log in, their anonymous data transfers seamlessly.
- Sync is incremental (deltas only), idempotent, and resilient to interruption.
- Conflict resolution is simple and deterministic.
- Backend is authoritative for data once synced.

### Non-Goals (for this RFC)

- Real-time sync (WebSocket/SSE/FCM push). This is a future evolution.
- Multi-device simultaneous editing with OT/CRDT. Last-writer-wins is sufficient for v1.
- Syncing catalog data (ingredients, tags, labels, allergens). These are server-seeded and
  read-only on the client for v1.
- Meal plans, grocery lists, or any feature beyond recipe CRUD.
- Image upload/sync. Images remain URL-based references for v1.

---

## 4. Design Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     Android Client                              │
│                                                                 │
│  SessionManager                                                 │
│  ┌──────────────────────────────────────────────┐               │
│  │  Anonymous(localUserId)                      │               │
│  │  Authenticated(user, token)                  │               │
│  └──────────────────────────────────────────────┘               │
│                                                                 │
│  Room DB (Source of Truth for reads)                             │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ recipes │ recipe_steps │ recipe_ingredients │ ...          │  │
│  │  ↑ syncState = PENDING|SYNCED|DELETED|CONFLICT            │  │
│  ├───────────────────────────────────────────────────────────┤  │
│  │ sync_metadata (entity_type → last_synced_at)              │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  SyncWorker (WorkManager)                                       │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  1. Push: POST dirty entities → /sync/push               │  │
│  │  2. Pull: GET deltas         → /sync/pull?since=X        │  │
│  │  3. Update sync_metadata                                  │  │
│  └───────────────────────────────────────────────────────────┘  │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTPS (Ktor)
┌────────────────────────────▼────────────────────────────────────┐
│                     Backend (Ktor + PostgreSQL)                  │
│                                                                 │
│  POST /auth/register                                            │
│  POST /auth/login                                               │
│  POST /auth/refresh                                             │
│  POST /sync/push      ← receives dirty entities                │
│  GET  /sync/pull      ← returns deltas since checkpoint        │
│  POST /auth/upgrade   ← merges anonymous → authenticated       │
│                                                                 │
│  PostgreSQL                                                     │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ users (uuid, display_name, email, ...)                     │  │
│  │ recipes (uuid, creator_id, updated_at, server_updated_at) │  │
│  │ recipe_steps, recipe_ingredients, recipe_tags, ...        │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. Anonymous-First Session Model

### 5.1 Session States

The current `UserSession` sealed class changes from:

```kotlin
// CURRENT
sealed class UserSession {
    data class Authenticated(val user: User, val authToken: AuthToken) : UserSession()
    data object Unauthenticated : UserSession()
    data object Loading : UserSession()
}
```

To:

```kotlin
// PROPOSED
sealed class UserSession {
    data object Loading : UserSession()
    data class Anonymous(val localUserId: UUID) : UserSession()
    data class Authenticated(val user: User, val authToken: AuthToken) : UserSession()
}
```

The app **always has a user context**. `Unauthenticated` no longer exists — it becomes `Anonymous`.

### 5.2 App Startup Flow

```
SessionManager.loadSession():
  1. Read SecurePreferences for stored tokens
  2. If tokens exist AND not expired:
       → Authenticated(user, token)
  3. If tokens exist AND expired:
       → Try refreshToken()
       → Success: Authenticated(user, newToken)
       → Failure: Anonymous(generate or reuse local UUID)
  4. If no tokens stored:
       → Read localUserId from SecurePreferences
       → If exists: Anonymous(existingLocalUserId)
       → If not: Generate UUIDv7, store it, Anonymous(newLocalUserId)
```

### 5.3 Anonymous User in Room

On first launch, the app creates a `UserEntity` in the local database:

```kotlin
UserEntity(
    uuid = generatedLocalUUID,
    displayName = "Guest",
    email = "",
    avatarUrl = "",
    updatedAt = System.currentTimeMillis(),
    deletedAt = null,
    syncState = SyncState.PENDING
)
```

All recipes created offline have `creatorId = localUserId`.

### 5.4 Navigation Change

The `ChefAINavGraph` currently gates on authentication:

```kotlin
// CURRENT: Login is start destination when not authenticated
val startDestination = when (userSession) {
    is UserSession.Authenticated -> AppDestinations.HOME.route
    else -> AppDestinations.LOGIN.route
}
```

This changes to:

```kotlin
// PROPOSED: Home is always the start destination
val startDestination = AppDestinations.HOME.route
// Login/Register available via profile menu or settings
```

### 5.5 Anonymous User on the Backend

The backend does NOT need to know about anonymous users until upgrade. The anonymous session is
purely local. The backend only sees a user when they register or log in.

**Alternative considered:** Create an anonymous backend user on first launch, similar to Firebase
Anonymous Auth. Rejected because:

- Adds unnecessary network dependency to first launch.
- Creates garbage user records for users who never register.
- Complicates the data model with little benefit.
- Defeats the purpose of offline-first.

---

## 6. Sync Protocol

### 6.1 Precondition

Sync **only runs when the user is `Authenticated`**. Anonymous users have no backend account and
therefore no server-side state. Their data lives exclusively in Room until they upgrade.

### 6.2 Sync Order

**Always push before pull.** This ensures:

- Local changes are persisted server-side before we overwrite local state with server data.
- The server has the latest local timestamps for conflict resolution during pull.

```
SyncWorker.doWork():
  1. Verify user is Authenticated (bail if Anonymous)
  2. Push dirty entities to POST /sync/push
  3. Pull deltas from GET /sync/pull?since=last_synced_at
  4. Apply deltas to Room inside a transaction
  5. Update sync_metadata.last_synced_at
```

### 6.3 What Gets Synced (v1 Scope)

Recipes are the primary user-created entity. In v1, we sync the **recipe aggregate**: a recipe and
all its owned children. This means the push/pull payloads bundle:

| Entity | Synced in v1? | Sync strategy |
|--------|:---:|---|
| `recipes` | Yes | Per-entity sync by `syncState` |
| `recipe_steps` | Yes | Synced as children of their parent recipe |
| `recipe_ingredients` | Yes | Synced as children of their parent recipe |
| `recipe_tags` | Yes | Cross-ref, synced as children of their parent recipe |
| `recipe_labels` | Yes | Cross-ref, synced as children of their parent recipe |
| `ingredients` | No | Server-seeded catalog. Read-only on client in v1. |
| `tags` | No | Server-seeded catalog. Read-only on client in v1. |
| `labels` | No | Server-seeded catalog. Read-only on client in v1. |
| `users` | No | Managed by auth flow. Not synced via /sync endpoints. |
| `allergens` | No | Server-seeded catalog. |
| `source_classifications` | No | Server-seeded catalog. |

**Why aggregate-level sync?** A recipe is not meaningful without its steps, ingredients, tags, and
labels. Syncing them independently creates partial state and ordering problems. By bundling them,
we get atomic recipe sync: either the full recipe is pushed/pulled, or none of it is.

### 6.4 Push: Client → Server

#### Request

```
POST /sync/push
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "recipes": [
    {
      "uuid": "01945a3b-...",
      "title": "Pasta Carbonara",
      "description": "Classic Roman pasta...",
      "imageUrl": "https://...",
      "imageUrlThumbnail": "https://...",
      "prepTimeMinutes": 10,
      "cookTimeMinutes": 20,
      "servings": 4,
      "creatorId": "01945a3b-...",
      "recipeExternalUrl": null,
      "privacy": "PRIVATE",
      "updatedAt": 1708000000000,
      "deletedAt": null,
      "steps": [
        {
          "uuid": "01945a3c-...",
          "orderIndex": 0,
          "instruction": "Boil water..."
        },
        {
          "uuid": "01945a3d-...",
          "orderIndex": 1,
          "instruction": "Cook guanciale..."
        }
      ],
      "ingredients": [
        {
          "ingredientId": "01945a3e-...",
          "quantity": 400.0,
          "unit": "grams"
        }
      ],
      "tagIds": ["01945a40-...", "01945a41-..."],
      "labelIds": ["01945a42-..."]
    }
  ]
}
```

**Notes:**

- `recipes` is an array. The client batches all dirty recipes into one push.
- Each recipe includes its full set of `steps`, `ingredients`, `tagIds`, and `labelIds`.
- `tagIds` and `labelIds` reference server-known catalog entries. If the client references an
  unknown ID, the server ignores that association (does not fail the push).
- `updatedAt` is client-side millisecond timestamp.
- `deletedAt != null` signals a soft delete.
- UUIDs are transmitted as standard hyphenated strings (not byte arrays).

#### Response

```json
{
  "accepted": [
    { "uuid": "01945a3b-...", "serverUpdatedAt": 1708000005000 }
  ],
  "conflicts": [
    {
      "uuid": "01945a3f-...",
      "reason": "SERVER_NEWER",
      "serverVersion": {
        "uuid": "01945a3f-...",
        "title": "Updated on other device",
        "updatedAt": 1708000003000,
        "steps": [...],
        "ingredients": [...],
        "tagIds": [...],
        "labelIds": [...]
      }
    }
  ],
  "errors": [
    {
      "uuid": "01945a4a-...",
      "reason": "INVALID_CREATOR",
      "message": "creatorId does not match authenticated user"
    }
  ],
  "serverTimestamp": 1708000005000
}
```

**Server-side push processing (per recipe):**

```
FOR each recipe in request.recipes:
  1. Validate creatorId == authenticated user's UUID
  2. Look up existing record by uuid
  3. IF not exists:
       INSERT recipe + children.
       Set updated_at = client.updatedAt, server_updated_at = NOW().
       → Add to accepted[] with serverUpdatedAt = server_updated_at as millis
  4. IF exists AND existing.server_updated_at <= client.updatedAt:
       UPDATE recipe + children.
       Set updated_at = client.updatedAt, server_updated_at = NOW().
       → Add to accepted[] with serverUpdatedAt = server_updated_at as millis
  5. IF exists AND existing.server_updated_at > client.updatedAt:
       Server version is newer (edited on another device).
       → Add to conflicts[] with server version
  6. IF validation fails (wrong creator, malformed data):
       → Add to errors[]
```

> **Note:** The comparison in step 4/5 uses `server_updated_at` (the server-authoritative
> Instant column), not `updated_at` (the client-submitted Long column). After a previous
> successful push, the client stores the returned `serverUpdatedAt` as its local `updatedAt`,
> so this comparison works correctly.

**Client-side post-push processing:**

```
FOR each accepted recipe:
  UPDATE syncState = SYNCED, updatedAt = serverUpdatedAt
  UPDATE all child entities (steps, ingredients, cross-refs) syncState = SYNCED

FOR each conflict recipe:
  // v1: Server wins. Overwrite local with server version.
  REPLACE local recipe and children with serverVersion
  SET syncState = SYNCED
  // (v2 could show a conflict resolution UI)

FOR each error:
  LOG error. Keep local entity as PENDING. Retry on next sync.
```

### 6.5 Pull: Server → Client

#### Request

```
GET /sync/pull?since=1708000000000&limit=100
Authorization: Bearer <token>
```

**Parameters:**

- `since` (required): The `last_synced_at` value from `sync_metadata`. `0` on first pull.
- `limit` (optional, default 100): Max number of recipe aggregates to return. Enables pagination
  for large initial syncs.

#### Response

```json
{
  "recipes": [
    {
      "uuid": "01945a3b-...",
      "title": "Pasta Carbonara",
      "description": "...",
      "imageUrl": "...",
      "imageUrlThumbnail": "...",
      "prepTimeMinutes": 10,
      "cookTimeMinutes": 20,
      "servings": 4,
      "creatorId": "01945a3b-...",
      "recipeExternalUrl": null,
      "privacy": "PRIVATE",
      "updatedAt": 1708000005000,
      "deletedAt": null,
      "steps": [...],
      "ingredients": [...],
      "tagIds": [...],
      "labelIds": [...]
    }
  ],
  "serverTimestamp": 1708000005000,
  "hasMore": false
}
```

**What the server returns:**

- All recipes owned by the authenticated user WHERE `server_updated_at > since`.
- All PUBLIC recipes WHERE `server_updated_at > since` (so the user sees others' shared recipes).
- Soft-deleted recipes (where `deleted_at IS NOT NULL`) — so the client can delete them locally.
- `hasMore: true` if more pages remain. Client calls again with `since=serverTimestamp`.

**Client-side pull processing:**

```
BEGIN TRANSACTION
FOR each recipe in response.recipes:
  local = Room.getRecipeById(recipe.uuid)
  IF local == null:
    INSERT recipe + children with syncState = SYNCED
  ELSE IF local.syncState == SYNCED:
    REPLACE recipe + children. Keep syncState = SYNCED.
  ELSE IF local.syncState == PENDING:
    // Local has unpushed changes. This shouldn't normally happen because
    // we push before pull. But if it does (e.g., user edited during sync):
    IF server.updatedAt > local.updatedAt:
      // Server wins. Overwrite. (v1 strategy)
      REPLACE with server version. Set syncState = SYNCED.
    ELSE:
      // Local is newer. Keep local as PENDING. Will push next cycle.
      SKIP server version.
  IF recipe.deletedAt != null:
    Soft-delete locally (set deletedAt, syncState = SYNCED)

UPDATE sync_metadata SET last_synced_at = response.serverTimestamp
COMMIT
```

### 6.6 Sync Metadata Table

#### Android (Room)

```kotlin
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val entityType: String,   // e.g., "recipes"
    val lastSyncedAt: Long                // server timestamp from last pull
)
```

This requires a Room database version bump (v2 → v3) with migration:

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_metadata (
                entityType TEXT NOT NULL PRIMARY KEY,
                lastSyncedAt INTEGER NOT NULL DEFAULT 0
            )
        """)
    }
}
```

#### Backend (PostgreSQL)

Not needed on the backend. The server simply queries `WHERE server_updated_at > :since` on each
request. No server-side per-client checkpoint is stored — the client owns its own checkpoint.

### 6.7 Sync Triggers (WorkManager)

```kotlin
// 1. On app foreground
val foregroundSync = OneTimeWorkRequestBuilder<SyncWorker>()
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .build()

// 2. Periodic (every 15 min when connected)
val periodicSync = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .build()

// 3. After local mutation (debounced)
// Called from repository after createRecipe/updateRecipe/deleteRecipe
val mutationSync = OneTimeWorkRequestBuilder<SyncWorker>()
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .setInitialDelay(5, TimeUnit.SECONDS)  // debounce rapid edits
    .build()
```

Use `ExistingWorkPolicy.KEEP` for mutation-triggered syncs to avoid piling up workers.

---

## 7. Anonymous → Authenticated Upgrade

### 7.1 Client-Side Flow

```
User taps "Create Account" or "Log In"
  → SessionManager.register(username, email, password)
     OR SessionManager.login(email, password)
  → Backend returns { userId, token, ... }
  → Client calls AccountUpgradeUseCase.execute(anonymousUserId, authenticatedUserId):
      1. Query all recipes WHERE creatorId = anonymousUserId
      2. UPDATE creatorId = authenticatedUserId for each
      3. UPDATE all child entities (steps, ingredients, cross-refs): syncState = PENDING
      4. Delete or reassign the anonymous UserEntity
      5. Store auth tokens in SecurePreferences
      6. Keep the anonymous localUserId in SecurePreferences
         (it will be reused if the user ever logs out back to anonymous mode)
      7. Emit UserSession.Authenticated
  → Trigger immediate sync (pushes all local recipes to server)
```

### 7.2 Backend Endpoint (Optional: `/auth/upgrade`)

For v1, the upgrade is **purely client-side**. The backend never knew about the anonymous user.
The client simply:
- Registers a new account (or logs in).
- Reassigns local `creatorId` fields.
- Pushes everything as new data via `/sync/push`.

A dedicated `/auth/upgrade` endpoint could be useful in v2 if we want server-side awareness of
the anonymous → authenticated transition (e.g., analytics, migration validation). For now, the
normal register + push flow is sufficient.

### 7.3 Edge Cases

| Scenario | Resolution |
|----------|-----------|
| **Offline register** | Not supported. Registration requires network. Show clear error. |
| **Conflict: same email exists** | Backend returns 409 Conflict. Client shows "email already registered." |
| **User logs in (not registers) on existing device with anonymous data** | Same upgrade flow: reassign creatorId, push. Server merges with any existing server recipes. |
| **User logs in on new device (no local data)** | No anonymous data to merge. Pull from server populates local DB. |
| **User logs out** | Return to `Anonymous` state. **Restore the existing `localUserId`** from `SecurePreferences` (the same UUID used before login). Local recipes remain visible. Only auth tokens are cleared. This is intentional: Pocket Chef is a personal device app and data continuity on logout is preferred over session isolation. |
| **User registers, goes offline immediately** | Tokens stored. Recipes marked PENDING. Sync runs when connectivity returns. |

---

## 8. Conflict Resolution

### 8.1 Strategy: Last-Writer-Wins (v1)

The conflict resolution strategy for v1 is **server-authoritative last-writer-wins**:

- During **push**: if the server's `server_updated_at` is newer than the client's `updatedAt`,
  the server's version wins. The server returns it in the `conflicts` array and the client
  overwrites its local copy.
- During **pull**: if a local entity has `syncState = PENDING` and the server sends a newer
  version, the server version wins.

This is simple, deterministic, and sufficient for a single-user-with-multiple-devices scenario.

### 8.2 How Timestamps Work (Dual-Timestamp Pattern)

The backend uses a **dual-timestamp pattern** on all syncable entity tables:

- `updated_at` (`Long`, milliseconds) — stores the **client-submitted** timestamp. Preserved as-is
  from the push payload. Useful for display, debugging, and auditing the client's view of time.
- `server_updated_at` (`Instant` / `TIMESTAMPTZ`) — set by the server via `Clock.System.now()` on
  every write. This is the **authoritative** timestamp used for sync conflict detection and delta
  pull queries.

**Timestamps flow:**

1. **Client** sets `updatedAt = System.currentTimeMillis()` when creating/editing a recipe locally.
2. **On push**, the client sends `updatedAt` (Long millis).
3. **Server** stores `updated_at = client.updatedAt` and sets `server_updated_at = NOW()`.
4. **Conflict detection** compares the existing record's `server_updated_at` against the client's
   `updatedAt`. If `server_updated_at > client.updatedAt`, the server version is newer.
5. **On push acceptance**, the server returns `serverUpdatedAt` (the `server_updated_at` value
   converted to millis). The client stores this as its local `updatedAt`, replacing its original
   client timestamp.
6. **On pull**, the server queries `WHERE server_updated_at > :since`. The `since` value is the
   `serverTimestamp` from the last pull response, stored in the client's `sync_metadata`.

This dual-timestamp approach preserves the client's original timestamp while using a server-
authoritative timestamp for all sync logic, avoiding clock-skew issues between devices.

### 8.3 Why Not CRDTs or OT?

| Approach | Pros | Cons | Verdict |
|----------|------|------|---------|
| Last-writer-wins | Simple, deterministic, no merge logic | Can lose edits in rare race conditions | **Chosen for v1.** Acceptable for a recipe app with primarily single-device use. |
| Field-level merge | Preserves more edits | Complex merge logic, field-level tracking | Overkill for v1. Revisit if multi-device editing becomes a pain point. |
| CRDT | Eventually consistent, no conflicts | Significant complexity, library dependency | Far too complex for a recipe app. |
| Manual resolution UI | User decides | UX complexity, interrupts flow | Not worth it for v1. Could add later. |

### 8.4 Conflict Visibility

In v1, conflicts are resolved silently (server wins). The user is not shown a merge UI.

If we want conflict visibility in v2:

- Use `SyncState.CONFLICT` to mark entities.
- Show a UI banner: "Some recipes were updated on another device. [Review Changes]"
- Present a diff view for each conflicted recipe.

---

## 9. Backend Schema Changes

### 9.1 Current Backend State

The backend (`ktor-chefai`) already has significantly more infrastructure than originally assumed.
This section documents the **actual** current state to avoid re-implementing what already exists.

**Authentication (fully implemented):**

- Auth endpoints: `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`
- JWT (HMAC256) with 1-hour access tokens and 30-day refresh tokens
- Refresh token rotation with reuse detection (SHA-256 hashed, stored in `refresh_tokens` table)
- BCrypt password hashing (cost 12)
- Comprehensive input validation (`InputValidator`) with sanitization

**Database tables (all 12 tables already defined in Exposed DSL):**

- `UserTable`, `RecipeTable`, `RecipeStepTable`, `IngredientTable`
- `RecipeIngredientTable`, `RecipeTagTable`, `RecipeLabelTable`
- `TagTable`, `LabelTable`, `AllergenTable`, `SourceClassificationTable`
- `RefreshTokenTable`
- Schema creation via `SchemaUtils.create()` in `DatabaseInit.kt` (currently disabled in prod;
  tables are managed via Docker/SQL)

**Recipe CRUD endpoints (basic, not sync-aware):**

- `GET /recipes` — returns user's own + public recipes (requires JWT auth)
- `POST /recipes` — creates a flat recipe (no steps/ingredients/tags/labels)
- `DELETE /recipes?uuid=...` — **hard deletes** the recipe (not soft delete!)
- `GET /recipes/byName?title=...` and `GET /recipes/byId?uuid=...`
- All protected behind `authenticate("auth-jwt")`

**Existing domain/service layers:**

- `RecipesRepository` interface with `PostgresRecipesRepository` implementation
- `RecipesService` with `getAccessibleRecipes(userId)`, `createRecipe()`, `deleteRecipe()`
- `RecipeDAO` (Exposed DAO entity), `daoToModel()` mapper
- Relationship data classes in `RecipeMapping.kt`: `RecipeIngredient`, `RecipeLabel`, `RecipeTag`

**What does NOT exist yet:**

- No `/sync/push` or `/sync/pull` endpoints
- No aggregate recipe handling (push/pull with children)
- No soft-delete implementation (`removeRecipe` does a hard `DELETE`)
- No `SyncService` or sync-specific processing logic
- The existing `CreateRecipeRequest` DTO is flat (no steps, ingredients, tags, labels)

### 9.2 Existing Backend Schema (Actual Exposed DSL Tables)

All tables already exist in `infrastructure/database/tables/`. They use `UUID` primary keys with
`"uuid"` as the column name. The backend uses a **dual-timestamp pattern** (see Section 8.2):
- `updated_at`: `long()` — client-submitted timestamp in milliseconds
- `server_updated_at`: `timestamp()` — server-authoritative Kotlin `Instant` (`TIMESTAMPTZ`)

> **Note:** The `UserTable` is the exception — it uses `timestamp()` (Instant) for both `created_at`
> and `updated_at`, does not have `deleted_at` or `server_updated_at`, and has no soft-delete support.

**Entity tables (UUIDTable):**

```kotlin
// UserTable — unique: has Instant timestamps, no soft delete, no server_updated_at
object UserTable : UUIDTable("users", "uuid") {
    val user_name = text("user_name")
    val display_name = text("display_name").default("")
    val email = text("email").uniqueIndex()          // NOT nullable
    val avatar_url = text("avatar_url").default("")
    val password_hash = varchar("password_hash", 255) // NOT nullable
    val created_at = timestamp("created_at").clientDefault { Clock.System.now() }
    val updated_at = timestamp("updated_at").clientDefault { Clock.System.now() }
}

object RecipeTable : UUIDTable("recipes", "uuid") {
    val title = text("title")
    val description = text("description")
    val image_url = text("image_url")
    val image_url_thumbnail = text("image_url_thumbnail")
    val prep_time_minutes = integer("prep_time_minutes")
    val cook_time_minutes = integer("cook_time_minutes")
    val servings = integer("servings")
    val creator_id = uuid("creator_id")               // ⚠ No FK reference to UserTable
    val recipe_external_url = text("recipe_external_url").nullable()
    val privacy = text("privacy")
    val updated_at = long("updated_at")                // Client timestamp (millis)
    val deleted_at = long("deleted_at").nullable()      // Soft delete (millis)
    val server_updated_at = timestamp("server_updated_at") // Server-authoritative
}

object RecipeStepTable : UUIDTable("recipe_steps", "uuid") {
    val recipe_id = reference("recipe_id", RecipeTable)  // FK with cascade
    val order_index = integer("order_index")
    val instruction = text("instruction")
    val updated_at = long("updated_at")
    val deleted_at = long("deleted_at").nullable()
    val server_updated_at = timestamp("server_updated_at")
}

object IngredientTable : UUIDTable("ingredients", "uuid") {
    val display_name = text("display_name")
    val allergen_id = reference("allergen_id", AllergenTable).nullable()
    val source_primary_id = uuid("source_primary_id").nullable()  // ⚠ No FK to SourceClassificationTable
    val updated_at = long("updated_at")
    val deleted_at = long("deleted_at").nullable()
    val server_updated_at = timestamp("server_updated_at").clientDefault { Clock.System.now() }
}

object TagTable : UUIDTable("tags", "uuid") { ... }          // Same pattern: updated_at (Long) + server_updated_at (Instant)
object LabelTable : UUIDTable("labels", "uuid") { ... }      // Same pattern
object AllergenTable : UUIDTable("allergens", "uuid") { ... } // Same pattern
object SourceClassificationTable : UUIDTable("source_classifications", "uuid") { ... } // Same pattern
```

**Junction tables (composite PK, no UUID):**

> **Note:** Junction tables use **camelCase** column names (e.g., `recipeId`, `updatedAt`) while
> entity tables use **snake_case** (e.g., `recipe_id`, `updated_at`). This inconsistency already
> exists in the codebase.

```kotlin
object RecipeIngredientTable : Table("recipe_ingredients") {
    val recipeId = reference("recipeId", RecipeTable)
    val ingredientId = reference("ingredientId", IngredientTable)
    val quantity = double("quantity")
    val unit = text("unit")
    val updatedAt = long("updatedAt")
    val deletedAt = long("deletedAt").nullable()
    val syncState = text("syncState")                  // ⚠ syncState on backend (see 9.4)
    val serverUpdatedAt = timestamp("serverUpdatedAt")
    override val primaryKey = PrimaryKey(recipeId, ingredientId)
}

object RecipeTagTable : Table("recipe_tags") {
    val recipeId = reference("recipeId", RecipeTable)
    val tagId = reference("tagId", TagTable)
    val updatedAt = long("updatedAt")
    val deletedAt = long("deletedAt").nullable()
    val syncState = text("syncState")                  // ⚠ syncState on backend
    val serverUpdatedAt = timestamp("serverUpdatedAt")
    override val primaryKey = PrimaryKey(recipeId, tagId)
}

object RecipeLabelTable : Table("recipe_labels") {
    val recipeId = reference("recipeId", RecipeTable)
    val labelId = reference("labelId", LabelTable)
    val updatedAt = long("updatedAt")
    val deletedAt = long("deletedAt").nullable()
    val syncState = text("syncState")                  // ⚠ syncState on backend
    val serverUpdatedAt = timestamp("serverUpdatedAt")
    override val primaryKey = PrimaryKey(recipeId, labelId)
}
```

### 9.3 Required Backend Schema Modifications

Since all tables already exist, the work is about **modifying behavior and adding new code**, not
creating tables from scratch.

**Required code changes:**

| # | Change | Why | Files |
|---|--------|-----|-------|
| 1 | **Change `removeRecipe` from hard delete to soft delete** | Sync protocol requires `deleted_at` so clients can learn about deletions via pull. Currently `PostgresRecipesRepository.removeRecipe()` does `RecipeTable.deleteWhere(...)`. Must change to `UPDATE deleted_at = NOW(), server_updated_at = NOW()`. | `DefaultRecipeRepository.kt` |
| 2 | **Add sync indexes on `server_updated_at`** | Pull queries filter by `server_updated_at > :since`. Needs index for performance. | `RecipeTable`, migration SQL |
| 3 | **Add `created_at` to entity tables (optional)** | Currently only `UserTable` has `created_at`. Useful for auditing but not required for sync. Can defer. | All entity tables |
| 4 | **Decide on `syncState` in junction tables** | Junction tables already have `syncState`. For aggregate-level sync where children travel with the parent recipe, this column is unused by the server. Consider removing it or ignoring it. See Section 9.4. | Junction tables |
| 5 | **Fix `publicRecipes()` query** | `PostgresRecipesRepository.publicRecipes()` queries `privacy eq "public"` (lowercase) but the `Privacy` enum is `PUBLIC` (uppercase). This is a latent bug. | `DefaultRecipeRepository.kt` |

**New code to create:**

| # | New artifact | Description | Location |
|---|-------------|-------------|----------|
| 1 | `SyncRoutes.kt` | Route handlers for `POST /sync/push` and `GET /sync/pull` | `presentation/routes/` |
| 2 | `SyncService.kt` | Push processing (accept/conflict/error) and pull query logic | `domain/service/` |
| 3 | `SyncDtos.kt` | `SyncPushRequest`, `SyncPushResponse`, `SyncPullResponse`, `SyncRecipe`, etc. | `application/dto/` |
| 4 | `SyncRepository.kt` | Data access for aggregate recipe queries (recipe + children joins) | `domain/repository/` + `infrastructure/database/repositoryImpl/` |
| 5 | Sync integration tests | Test push/pull with accept, conflict, and error scenarios | `src/test/` |

### 9.4 Key Schema Design Decisions

1. **Dual-timestamp pattern**: All syncable entity tables have both `updated_at` (Long millis,
   client-submitted) and `server_updated_at` (Instant/TIMESTAMPTZ, server-authoritative). Only
   `server_updated_at` participates in sync conflict resolution and delta pull queries. See
   Section 8.2 for the full timestamp flow.

2. **`is_anonymous` on users**: Not needed for v1 (anonymous users are local-only). The `UserTable`
   currently has no `is_anonymous` column and doesn't need one — it can be added in v2 if
   server-side anonymous users are introduced.

3. **`created_at` column**: Currently only `UserTable` has `created_at`. Other entity tables do not.
   Not required for sync, but can be added later for auditing. The `server_updated_at` column serves
   as the effective creation timestamp for new records.

4. **Soft deletes**: `deleted_at IS NOT NULL` marks deletion. The server retains soft-deleted records
   for a retention period (e.g., 90 days) so pull queries can inform clients about deletions.
   A background job purges records older than the retention period. **Important:** The current
   `removeRecipe()` does a hard DELETE and must be changed to a soft delete for sync to work.

5. **`syncState` on junction tables**: The junction tables (`RecipeIngredientTable`,
   `RecipeTagTable`, `RecipeLabelTable`) already have a `syncState` text column. Since sync is
   aggregate-level (entire recipe with children), the server does not use this column — it is
   effectively a client-side artifact that leaked into the backend schema. For v1, the server
   should **ignore** this column during push/pull. Consider removing it in a future cleanup.

6. **No `outbox` table on backend**: The client's `syncState = PENDING/DELETED` IS the outbox.
   No separate outbox table is needed on either side.

7. **No FK from `RecipeTable.creator_id` to `UserTable`**: The current schema has `creator_id` as a
   plain `uuid()` column without a foreign key reference. This means the database does not enforce
   referential integrity between recipes and users. For v1 this is acceptable — the sync push
   endpoint validates `creatorId == authenticated user UUID` at the application level. A FK
   constraint can be added later if needed.

8. **Column naming inconsistency**: Entity tables use snake_case (`recipe_id`, `updated_at`) while
   junction tables use camelCase (`recipeId`, `updatedAt`). This inconsistency already exists and
   should not be changed mid-migration. New code should be aware of both conventions.

---

## 10. Android Schema Changes

### 10.1 New Entity: `SyncMetadataEntity`

```kotlin
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val entityType: String,
    val lastSyncedAt: Long = 0
)
```

### 10.2 New DAO: `SyncMetadataDao`

```kotlin
@Dao
interface SyncMetadataDao {
    @Query("SELECT lastSyncedAt FROM sync_metadata WHERE entityType = :entityType")
    suspend fun getLastSyncedAt(entityType: String): Long?

    @Upsert
    suspend fun upsert(metadata: SyncMetadataEntity)
}
```

### 10.3 Database Migration (v2 → v3)

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_metadata (
                entityType TEXT NOT NULL PRIMARY KEY,
                lastSyncedAt INTEGER NOT NULL DEFAULT 0
            )
        """)
    }
}
```

### 10.4 Changes to Existing Entities

No structural changes needed. All entities already have `syncState`, `updatedAt`, `deletedAt`.

The `UserEntity` gains no new columns, but the app logic changes: the anonymous user is created as
a `UserEntity` with a locally generated UUID.

---

## 11. Backend API Specification

### 11.1 POST /sync/push

**Auth:** Required (Bearer token).

**Request Body:**

```kotlin
@Serializable
data class SyncPushRequest(
    val recipes: List<SyncRecipe>
)

@Serializable
data class SyncRecipe(
    val uuid: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val imageUrlThumbnail: String,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    val creatorId: String,
    val recipeExternalUrl: String?,
    val privacy: String,
    val updatedAt: Long,
    val deletedAt: Long?,
    val steps: List<SyncRecipeStep>,
    val ingredients: List<SyncRecipeIngredient>,
    val tagIds: List<String>,
    val labelIds: List<String>
)

@Serializable
data class SyncRecipeStep(
    val uuid: String,
    val orderIndex: Int,
    val instruction: String
)

@Serializable
data class SyncRecipeIngredient(
    val ingredientId: String,
    val quantity: Double,
    val unit: String
)
```

**Response Body:**

```kotlin
@Serializable
data class SyncPushResponse(
    val accepted: List<AcceptedEntity>,
    val conflicts: List<ConflictEntity>,
    val errors: List<SyncError>,
    val serverTimestamp: Long
)

@Serializable
data class AcceptedEntity(
    val uuid: String,
    val serverUpdatedAt: Long
)

@Serializable
data class ConflictEntity(
    val uuid: String,
    val reason: String,
    val serverVersion: SyncRecipe
)

@Serializable
data class SyncError(
    val uuid: String,
    val reason: String,
    val message: String
)
```

**HTTP Status Codes:**
- `200 OK` — Push processed (check `accepted`, `conflicts`, `errors` arrays).
- `401 Unauthorized` — Token expired or invalid.
- `400 Bad Request` — Malformed payload.

### 11.2 GET /sync/pull

**Auth:** Required (Bearer token).

**Query Parameters:**
- `since` (Long, required): Millisecond timestamp. `0` for first sync.
- `limit` (Int, optional, default 100): Max recipes per response.

**Response Body:**

```kotlin
@Serializable
data class SyncPullResponse(
    val recipes: List<SyncRecipe>,
    val serverTimestamp: Long,
    val hasMore: Boolean
)
```

**What the server queries:**

> **Note:** The query uses `server_updated_at` (the server-authoritative TIMESTAMPTZ column), not
> `updated_at` (the client-submitted Long column). The `:since` parameter is compared as millis
> converted to an Instant. Junction tables use camelCase column names (`"recipeId"`, `"tagId"`)
> per the existing schema convention.

```sql
-- Step 1: Get recipe UUIDs matching the delta criteria
SELECT r.uuid
FROM recipes r
WHERE r.server_updated_at > :since_as_instant
  AND (r.creator_id = :userId OR r.privacy = 'PUBLIC')
ORDER BY r.server_updated_at ASC
LIMIT :limit

-- Step 2: For each recipe UUID, load the full aggregate (recipe + children)
-- In Exposed, this is done programmatically: query RecipeStepTable, RecipeIngredientTable,
-- RecipeTagTable, RecipeLabelTable WHERE recipe_id / "recipeId" IN (:recipeUuids)
```

**HTTP Status Codes:**
- `200 OK` — Pull response with recipes.
- `401 Unauthorized` — Token expired or invalid.

---

## 12. Implementation Plan

### Phase 1: Android Offline-First (Weeks 1–2)

| # | Task | Files |
|---|------|-------|
| 1 | Add `Anonymous` to `UserSession` sealed class | `UserSession.kt` |
| 2 | Update `SessionManager` to auto-create anonymous user on first launch | `SessionManager.kt`, `SecurePreferences.kt` |
| 3 | Remove login gate from `ChefAINavGraph` (always start at HOME) | `ChefAINavGraph.kt` |
| 4 | Inject `SessionManager` into `RecipesViewModel`, use real user ID | `RecipesViewModel.kt` |
| 5 | Remove hardcoded test UUID from `DefaultRecipeRepository` | `DefaultRecipeRepository.kt` |
| 6 | Wire `HomeScreen` to `RecipesRepository` | `HomeViewModel.kt`, `HomeScreen.kt` |
| 7 | Add `SyncMetadataEntity`, DAO, DB migration v2→v3 | `SyncMetadataEntity.kt`, `SyncMetadataDao.kt`, `ChefAIDataBase.kt` |
| 8 | Unit tests for anonymous session creation and user wiring | Tests |

### Phase 2: Anonymous → Authenticated Upgrade (Week 3)

| # | Task | Files |
|---|------|-------|
| 1 | Create `AccountUpgradeUseCase` | New file in `auth/domain/usecase/` |
| 2 | Implement creatorId reassignment in Room (transaction) | `AccountUpgradeUseCase.kt` |
| 3 | Update `SessionManager.login/register` to call upgrade use case | `SessionManager.kt` |
| 4 | Store user profile locally during login (SecurePreferences or Room) | `SecurePreferences.kt` |
| 5 | Test: anonymous CRUD → register → verify ownership transfer | Tests |
| 6 | Test: login on new device → pull populates local DB | Tests |

### Phase 3: Backend Sync Endpoints (Week 4)

> **Note:** All 12 Exposed table definitions already exist (see Section 9.2). No new tables needed.
> The existing recipe CRUD endpoints (`GET/POST/DELETE /recipes`) remain unchanged for non-sync
> clients. The sync endpoints are additive.

| # | Task | Files |
|---|------|-------|
| 1 | **Change `removeRecipe` to soft delete** (set `deleted_at` + `server_updated_at` instead of hard `DELETE`) | `DefaultRecipeRepository.kt` |
| 2 | **Fix `publicRecipes()` query** — uses `"public"` (lowercase) but enum is `PUBLIC` | `DefaultRecipeRepository.kt` |
| 3 | **Add index** on `RecipeTable.server_updated_at` for pull query performance | SQL migration or `DatabaseInit.kt` |
| 4 | Create `SyncDtos.kt` — `SyncPushRequest`, `SyncPushResponse`, `SyncPullResponse`, `SyncRecipe`, etc. | `application/dto/SyncDtos.kt` |
| 5 | Create `SyncRepository` — aggregate recipe queries (recipe + children joins) for push/pull | `domain/repository/SyncRepository.kt`, `infrastructure/database/repositoryImpl/` |
| 6 | Create `SyncService` — push processing (accept/conflict/error) and pull logic with pagination | `domain/service/SyncService.kt` |
| 7 | Create `SyncRoutes.kt` — `POST /sync/push` and `GET /sync/pull` route handlers (behind `auth-jwt`) | `presentation/routes/SyncRoutes.kt` |
| 8 | Wire sync routes into `configureRouting()` | `Routing.kt` |
| 9 | Backend integration tests for push (accept, conflict, error) and pull (pagination, delta) | `src/test/` |

### Phase 4: Android Sync Client (Weeks 4–5)

| # | Task | Files |
|---|------|-------|
| 1 | Create `SyncNetworkDataSource` (Ktor client for push/pull) | New file in `core/data/network/` |
| 2 | Create `SyncWorker` (CoroutineWorker) | New file in `core/data/sync/` |
| 3 | Implement push logic (query dirty → POST → process response) | `SyncWorker.kt` |
| 4 | Implement pull logic (GET → upsert Room → update checkpoint) | `SyncWorker.kt` |
| 5 | Wire WorkManager triggers (foreground, connectivity, mutation) | `SyncModule.kt` (Hilt) |
| 6 | Add sync status observable (for UI indicator) | `SyncManager.kt` |
| 7 | Add sync DI module | `SyncModule.kt` |
| 8 | Integration tests with mock server | Tests |

### Phase 5: Hardening & Polish (Week 5–6)

| # | Task | Files |
|---|------|-------|
| 1 | Retry with exponential backoff on sync failure | `SyncWorker.kt` |
| 2 | UI indicator: syncing / synced / offline / error | UI components |
| 3 | End-to-end test: offline create → online sync → verify server | Tests |
| 4 | End-to-end test: multi-device conflict → last-writer-wins | Tests |
| 5 | Soft-delete purge job on backend (90-day retention) | Backend scheduled task |
| 6 | Clean up stale TODOs, merge paging branch, update docs | Various |
| 7 | Write ADR for anonymous-first and sync decisions | `docs/adrs/` |

---

## 13. Alternatives Considered

### 13.1 Sync Approach Alternatives

| Approach | Description | Why rejected |
|----------|-------------|-------------|
| **Full-state sync** | Client sends entire DB, server diffs | Bandwidth-heavy, doesn't scale. |
| **Event sourcing / CQRS** | Log all mutations as events, replay | Massive complexity for a recipe app. |
| **Firebase Realtime DB / Firestore** | Managed real-time sync | Vendor lock-in, no Postgres, no custom auth. |
| **CRDTs (Automerge, Yjs)** | Conflict-free replicated data types | Over-engineered for recipe CRUD. Would require a fundamentally different data model. |
| **GraphQL subscriptions** | Real-time push via WebSocket | Adds server complexity. Can layer on later as an optimization over pull-based sync. |
| **Delta sync (chosen)** | Push dirty entities, pull changed entities | Simple, proven pattern. Good fit for low-conflict, single-user-primary-device usage. |

### 13.2 Anonymous User Alternatives

| Approach | Description | Why rejected |
|----------|-------------|-------------|
| **Server-side anonymous user (Firebase-style)** | Create a backend user on first launch with no credentials | Requires network on first launch. Creates garbage accounts. Defeats offline-first. |
| **Device ID as user ID** | Use Android device ID | Not portable across devices. Privacy concerns. UUID collision risk across installs. |
| **No user ID for anonymous** | Skip creatorId for offline recipes | Breaks FK constraints, complicates upgrade, loses ownership tracking. |
| **Client-generated UUIDv7 (chosen)** | Generate UUID locally, store in prefs | Simple, no network needed, survives reinstall if backed up, clean upgrade path. |

### 13.3 Conflict Resolution Alternatives

| Approach | Description | Why rejected for v1 |
|----------|-------------|-------------|
| **Client wins** | Always keep local version | Data loss on other devices. |
| **Server wins (chosen for v1)** | Always keep server version | Can lose most-recent local edits in rare cases. Acceptable trade-off. |
| **Manual merge UI** | Show conflict to user | UX complexity. Not worth it for rare conflicts in a recipe app. |
| **Field-level merge** | Merge non-conflicting fields | Significant implementation complexity. |
| **Operational transform** | Transform concurrent operations | Massive complexity. Designed for real-time collaborative editing. |

---

## 14. Security Considerations

- **Push validation**: Server must verify `creatorId == authenticated user UUID` for every pushed
  recipe. A user cannot push recipes claiming another user as creator.
- **Pull scoping**: Server only returns the user's own recipes + PUBLIC recipes. Private recipes
  from other users are never returned.
- **Token refresh during sync**: If a sync fails with 401, the `SyncWorker` should attempt a token
  refresh and retry once before failing.
- **Soft-delete retention**: Purged records (past retention period) will not appear in pull
  responses. If a client hasn't synced in >90 days, they may need a full re-sync.
- **Rate limiting**: Backend should rate-limit sync endpoints to prevent abuse (e.g., max 10
  push requests per minute per user).

---

## 15. Observability & Monitoring

- **Sync metrics (backend)**: Track push/pull request count, latency, conflict rate, error rate
  per user.
- **Sync status (client)**: Expose `SyncState` observable for UI (syncing, synced, offline, error).
- **Logging**: Log sync operations with Timber (client) and structured logging (backend). Include
  entity counts, conflict counts, and duration.
- **Alerting**: Alert on elevated conflict rates or sync error rates (may indicate a bug in
  timestamp handling).

---

## 16. Open Questions

1. **Catalog seeding**: How do tags, labels, ingredients, and allergens get populated on a fresh
   client install? Options: bundled in the APK asset database (current approach), or a dedicated
   `GET /catalog` endpoint. Recommend keeping the current asset DB approach for v1.

2. **Image sync**: Recipes reference image URLs. If a user attaches a local photo, how is it
   uploaded? This RFC defers image upload to a future RFC. For v1, images are URL-based only.

3. **Pagination on push**: Should push support pagination for users with hundreds of dirty recipes
   (e.g., first sync after upgrade)? Recommend: yes, batch in groups of 50. Add to implementation.

4. **Clock skew**: Client and server clocks may differ. The dual-timestamp pattern (see Section 8.2)
   mitigates this: `server_updated_at` is always set by the server via `Clock.System.now()`, so
   delta pull queries are unaffected by client clock drift. Clock skew only affects the conflict
   detection comparison during push (`existing.server_updated_at > client.updatedAt`). After a
   successful push, the client replaces its `updatedAt` with the server's `serverUpdatedAt`, so
   subsequent comparisons use server-authoritative values. For v1, no special handling needed.

5. **Full re-sync**: What happens if a client's local DB is corrupted or sync_metadata is lost?
   Recommend: delete `sync_metadata`, set all entities to `syncState = PENDING`, and re-run sync.
   The push-before-pull flow handles this naturally (existing server entities win by timestamp,
   new local entities get pushed).

---

## 17. Glossary

| Term | Definition |
|------|-----------|
| **Anonymous session** | Local-only user session with client-generated UUID. No backend account. |
| **Authenticated session** | User logged in with backend credentials. Has JWT tokens. |
| **SSOT** | Single Source of Truth. Room (local) for reads; backend (remote) for authoritative state. |
| **Dirty entity** | An entity with `syncState = PENDING` or `DELETED`. Needs to be pushed. |
| **Delta sync** | Only syncing entities that changed since the last checkpoint. |
| **Checkpoint** | The `last_synced_at` timestamp stored in `sync_metadata`. |
| **Aggregate** | A recipe plus all its children (steps, ingredients, tag/label cross-refs). |
| **Soft delete** | Marking `deletedAt` rather than physically removing a row. |
| **Last-writer-wins** | Conflict resolution where the most recent `updated_at` wins. |
