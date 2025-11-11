# 🔄 ADR 003 – Two-Step Synchronization with Backend

**Date:** 2025-11-10  
**Status:** Accepted  
**Context:** The ChefAI app must keep user data (recipes, ingredients, meal plans) synchronized between local SQLite storage and the backend. The app is offline-first, and sync must be efficient and robust against intermittent connectivity.

---

## Decision

We adopt a **two-step pull/push synchronization model** using WorkManager:

1. **Push local changes** (`POST /sync/push`)

    - The client sends locally modified or newly created entities (from the Outbox table).

    - Includes pending deletes.

    - The backend merges updates using _last-writer-wins_ based on `updatedAt`.

2. **Pull remote changes** (`GET /sync/pull?since=<timestamp>`)

    - The client requests all backend updates newer than its `lastSyncedAt` checkpoint.

    - The backend returns only changed entities and deletions.

    - The response includes a `serverCheckpoint` timestamp for the client to store.


This flow ensures idempotent, incremental, and efficient syncing.

---

## Rationale

- **Offline-first:** Users can create or edit content locally; changes sync when connectivity resumes.

- **Lightweight:** Sync payloads contain only deltas, not full datasets.

- **Scalable:** Each entity is versioned independently via `updatedAt` and optional `deletedAt`.

- **Conflict resolution:** Handled via timestamp comparison (last-writer-wins).

- **Extensible:** Supports new entity types (e.g., grocery sections) without altering protocol.

- **Future-proof:** Can evolve toward push-based sync (WebSocket/FCM) or CRDTs later.


---

## Implementation Summary

- **WorkManager triggers**

    - On app startup

    - When connectivity resumes

    - When local edits occur

- **Outbox table**

    - Queues unsynced mutations with JSON payloads

    - Flushed when online

- **Sync checkpoint**

    - Tracked in `sync_metadata` table

    - Used to request backend deltas

- **Endpoints**

    - `POST /sync/push` → client → server

    - `GET /sync/pull?since=<timestamp>` → server → client

- **Backend storage**

    - Each entity table includes `updated_at`, `deleted_at`

    - Optionally maintain a `sync_meta` table to store global `last_updated_at` per entity type


---

## Consequences

- Local database always represents the current working set; backend is the source of truth.

- Sync complexity remains isolated to the WorkManager worker and backend `/sync` endpoints.

- As datasets scale, pagination and compression may be added to sync payloads.