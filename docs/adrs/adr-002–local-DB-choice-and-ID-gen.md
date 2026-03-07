# 🧱 ADR 0002 – Local Database Choice & ID Generation

**Date:** ~~2025-11-10~~ **Updated:** 2026-03-07  
**Status:** Accepted  
**Context:** The ChefAI Android app is offline-first and needs a local persistence layer for recipes, meal plans, and grocery lists, plus a backend-driven identifier strategy compatible with distributed systems.

---

## Decision

We will:

1. Use **SQLite via Room** as the local on-device database.
    
2. Use **client-generated UUIDv7 identifiers** for all persistent entities.

3. The client-generated ID will be treated as the **canonical ID** and accepted by the backend during synchronization.


---

## Rationale

### SQLite via Room

- **Relational fit:** Recipes, ingredients, steps, and meal plans have clear one-to-many and many-to-many relationships, which align perfectly with a relational model.
    
- **Built-in FTS support:** SQLite FTS5 enables full-text search for recipes and ingredients.
    
- **Offline-first & transactional:** ACID compliance, robust local caching, and atomic updates make it ideal for offline operation.
    
- **Performance:** Expected user datasets (hundreds to thousands of records) are trivial for SQLite.
    
- **Ecosystem maturity:** Excellent tooling and type safety through Jetpack Room, Kotlin coroutines, and Flow integration.
    

### Client Generated UUIDv7 ID Generation

- **Time-sortable:** IDs encode creation time, which improves database index locality compared to random UUIDv4 values.
    
- **Globally unique:** Safe for distributed systems without coordination.
    
- **Client-generated:** Because UUIDs have no collision risk and we want to support offline creation of entities, we generate them **client-side.**
    
- **Library:** We use Block's UUID v7 generation library, wrapped by [UuidV7Generator](../../app/src/main/java/com/tenmilelabs/chefai/core/data/local/UuidV7Generator.kt).newId()
    
- **No entity prefixes:** Prefixes (e.g., `rec_`, `usr_`) add no functional value and are omitted for simplicity; the schema ensures relational integrity.

- **Supports offline-first architecture**: Entities can be created and referenced locally without requiring backend connectivity.

- **Simplifies synchronization**:  Using canonical client-generated IDs eliminates the need for temporary IDs and ID remapping during sync.

- **Improves database write performance**:  UUIDv7 identifiers are **time-ordered**, which improves database index locality compared to random UUIDv4 values.


#### Trade-offs for Client Generated UUIDv7 ID Generation

- **Larger storage size**: UUIDs are larger than sequential integer IDs (16 bytes vs ~8 bytes).

- **Potential timestamp leakage**: UUIDv7 embeds timestamp information, which could reveal approximate creation time.

- **Slightly more complex client generation**: UUIDv7 requires a library since it is not yet available in the standard Java/Kotlin UUID implementation.

## Alternatives Considered

### Backend-generated IDs

Rejected because it would require temporary client IDs and complex ID remapping during synchronization.

### UUIDv4

Rejected because random UUIDs degrade database index locality compared to time-ordered UUIDv7.

## Consequences

- The backend must accept client-generated IDs as canonical.
- Synchronization endpoints must be **idempotent** (e.g., UPSERT by ID).
- All client-created entities must generate their ID at creation time.

---

## Consequences

- The backend must accept client-generated IDs as canonical. 
 
- Synchronization endpoints must be **idempotent** (e.g., UPSERT by ID).

- All client-created entities must generate their ID at creation time.

- Migrations in Room must maintain FTS tables.
     
- This design ensures future compatibility with Postgres + Exposed on the backend.