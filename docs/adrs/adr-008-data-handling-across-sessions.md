# ADR-008: Local Database Behavior on Logout and Account Switch

**Date:** 2026-03-08

**Status:** Accepted

**Context:** Because the app is offline-first, local data from a previously logged in user may exist without immediate server synchronization. We need to define how the **local Room database behaves when a user logs out and logs in with another account**.

**Related:** ADR-002 (Local DB & ID Generation)

## Decision

When a user logs in:

- If the **newly authenticated user ID is different from the previous user ID**, the application **clears the local Room database**.
- If the **same user logs in again**, the database is **preserved**.
- Logging out **does not immediately clear local data**.

Implementation rule:
```kotlin
if (previousUserId != null && previousUserId != newUserId) {
    database.clearAllTables()
}
```

The database reset happens **after successful authentication but before synchronization begins**.

---

## Rationale

This approach significantly **simplifies the synchronization system**.

Because the database only contains data for **one user at a time**, we avoid:

- Multi-user data scoping
- Per-query filtering
- Sync conflicts between multiple local owners

The sync engine can safely assume:

> "All local data belongs to the currently authenticated user."

This dramatically reduces edge cases and simplifies both client and backend logic.

---

## Consequences

### Positive

- Cleaner sync architecture
- Fewer edge cases
- Easier debugging
- Offline-first UX preserved
- Simple implementation

### Negative

- Multi-user device scenarios are not supported
- Local data for the previous user is removed when switching accounts

---

## Alternatives Considered

### Option 1 — Keep all data indefinitely

**Description**

All data remains in the database regardless of account changes. Queries must be scoped by `ownerId`.

**Pros**

- Maximum offline capability
- Supports multiple users on the same device
- No database clearing required

**Cons**

- Requires every query to filter by `ownerId`
- Higher risk of cross-account data leakage bugs
- Sync logic becomes more complex
- Increased cognitive load for developers

**Conclusion**

Rejected due to **higher complexity and risk of subtle bugs**.

---

### Option 2 — Wipe data immediately on logout

**Description**

All local data is deleted when a user logs out.

**Pros**

- Very clear data ownership model
- Eliminates cross-user contamination

**Cons**

- Breaks offline-first experience
- Users may lose unsynced data
- Anonymous users lose their recipes
- Poor UX if logout occurs accidentally

**Conclusion**

Rejected because it **violates offline-first principles and harms UX**.

---

### Option 3 — Wipe data only when a different account logs in (Selected)

**Description**

Local data is preserved on logout.  
If a different user logs in, the local database is cleared before syncing.

**Pros**

- Maintains offline-first behavior
- Prevents cross-account data mixing
- Simplifies synchronization logic
- Low implementation complexity
- Predictable behavior

**Cons**

- Data for a logged-out user remains locally until another account logs in

**Conclusion**

Selected as the **best balance between simplicity, correctness, and user experience**.

---

## Future Considerations

We still need to define a eviction policy to determine what content stays cached as the local DB grows.

If ChefAI later needs to support **true multi-user devices**, the architecture may evolve to:

- Scope entities by `ownerId`
- Maintain multiple user datasets locally

At the current stage of the product, this additional complexity is not justified.