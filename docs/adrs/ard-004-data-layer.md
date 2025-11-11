# 🧱 ADR 004 – Data Layer Composition & Repository Pattern

**Date:** 2025-11-10  
**Status:** Accepted  
**Context:**  
ChefAI is an offline-first Android app that stores and syncs user data (recipes, ingredients, meal plans, and grocery lists) between a local SQLite database and a backend service. The data layer must support both offline operation and background synchronization while maintaining clean separation from the domain and UI layers.

---

## Decision

We will structure the **Data Layer** around a **dual-source Repository pattern**, composed of:

1. **Repositories** per aggregate root (`RecipesRepository`, `MealPlansRepository`, `IngredientsTagsLabelsRepository`).

2. **Two Data Sources** for each repository:

    - **Local Data Source** — backed by **Room (SQLite)** for offline caching and transactional writes.

    - **Network Data Source** — backed by **Ktor Client** (or Retrofit) to communicate with the backend’s REST API.

3. A **Background Sync** mechanism using **WorkManager**, implementing the two-step sync defined in ADR-003:

    - `POST /sync/push` uploads pending local changes from the Outbox table.

    - `GET /sync/pull?since=<timestamp>` fetches deltas from the backend.

4. A shared **SQLite ChefAI Database** containing all entity tables, a `sync_metadata` table, and an `outbox` table for queued mutations.

5. Dependency injection (Hilt/Koin) wiring all these components together.


---

## Rationale

- **Offline-first design:** Users can create and edit recipes and plans locally even when offline.

- **Clear separation of concerns:** Each repository abstracts away whether data originates from local or network sources.

- **Scalable sync:** Two-source architecture aligns with the push/pull sync model from ADR-003 and cleanly supports the Outbox pattern.

- **Testability:** Repositories depend only on interfaces (`LocalDataSource`, `RemoteDataSource`), making unit testing straightforward.

- **Extensibility:** New entities (e.g., “Favorites,” “Shopping Templates”) can be added by introducing a new repository pair without altering existing ones.

- **Maintainability:** The pattern follows Android best practices and Clean Architecture principles, minimizing coupling to frameworks.


---

## Implementation Summary

- **Local Data Sources**

    - Use Room DAOs for persistence.

    - Provide full CRUD, FTS5 queries, and read-write transactions.

    - Include an `Outbox` table:

      `@Entity(tableName = "outbox") data class OutboxEntity(     @PrimaryKey val id: String,     val entityType: String,     val payload: String,     val operation: String, // INSERT / UPDATE / DELETE     val createdAt: Long )`

    - Include a `SyncMetadata` table tracking the `lastSyncedAt` checkpoint.

- **Network Data Sources**

    - Implement API contracts via Ktor Client interfaces.

    - Example:

      `interface RecipeRemoteDataSource {     suspend fun pushChanges(payload: SyncPayload): ApiResponse     suspend fun pullChanges(since: Long): SyncResponse }`

- **Repositories**

    - Combine both sources, exposing a unified interface to domain use cases.

    - Implement conflict resolution (last-writer-wins) and trigger local updates after successful network operations.

- **WorkManager Sync**

    - Periodically or on demand:

        1. Read pending mutations from Outbox.

        2. Call `/sync/push`.

        3. Call `/sync/pull?since=<lastSyncedAt>`.

        4. Merge deltas into local DB inside a transaction.

        5. Update `lastSyncedAt`.

- **Dependency Injection**

    - DI module instantiates DAOs, network clients, repositories, and the `SyncCoordinator`.


---

## Consequences

- The Data layer can scale to millions of rows per user without design changes.

- Each repository can be independently tested and replaced.

- Sync complexity remains localized to WorkManager + repository implementations.

- Local data always reflects the working set, while the backend remains the source of truth.

- Later improvements (push notifications, real-time sync, pagination) can be layered on without redesign.


---