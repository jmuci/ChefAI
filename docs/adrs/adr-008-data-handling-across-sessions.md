# ADR-008: Local Database Behavior on Logout and Account Switch

**Date:** 2026-03-08

**Status:** Accepted

**Context:** Because the app is offline-first and anonymous-first, local data may exist for:

- a previously authenticated user
- the current anonymous session after logout
- local changes that have not yet synced

We need to define how the **local Room database behaves when a user logs out and logs in with another account** without losing new anonymous work or leaking previous authenticated data across accounts.

**Related:** ADR-002 (Local DB & ID Generation)

## Decision

When a user logs in:

- If there is **no previous authenticated user ID**, the database is **preserved**.
- If the **same authenticated user logs in again**, the database is **preserved**.
- If a **different authenticated user logs in** and there is **active anonymous data**, the app:
  - preserves the anonymous data
  - removes stale data belonging to the previous authenticated user
  - upgrades the anonymous data into the new authenticated account
- If a **different authenticated user logs in** and there is **no anonymous session to preserve**, the application **clears the local Room database**.
- Logging out **does not immediately clear local data** and returns the app to an anonymous session.

The account-switch handling happens **after successful authentication but before synchronization begins**.

---

## Rationale

This approach preserves the offline-first anonymous workflow without allowing stale local data from `user1` to flow into `user2`.

The original "always clear on account switch" rule was safe, but too aggressive for this flow:

1. login as `user1`
2. logout
3. create recipes anonymously
4. login as `user2`

In that case, a full database wipe would destroy the anonymous recipes created after logout, which is not acceptable for an anonymous-first product.

At the same time, we still want to avoid:

- pushing stale `user1` data while `user2` is active
- showing `user1` local data during `user2`'s session
- forcing global `ownerId` filtering onto every query today

The decision therefore is:

- preserve current anonymous work
- remove stale previous-account local data
- only fall back to full DB clearing when there is no anonymous work to preserve

This keeps the sync model practical while respecting offline-first expectations.

---

## Consequences

### Positive

- Cleaner sync architecture
- Fewer edge cases
- Easier debugging
- Offline-first UX preserved
- Anonymous work created after logout is preserved across the next login
- Cross-account local leakage is still prevented

### Negative

- Multi-user device scenarios are still not fully supported
- Local data for the previous authenticated user is removed when switching accounts
- Account-switch logic is more nuanced than a simple `clearAllTables()` rule

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

### Option 3 — Wipe data only when a different account logs in

**Description**

Local data is preserved on logout.  
If a different user logs in, the local database is always cleared before syncing.

**Pros**

- Maintains offline-first behavior
- Prevents cross-account data mixing
- Simplifies synchronization logic
- Low implementation complexity
- Predictable behavior

**Cons**

- Anonymous work created after logout is lost when the next account logs in

**Conclusion**

Rejected because it is too destructive for the anonymous-first workflow.

---

### Option 4 — Preserve current anonymous data, remove previous authenticated data, clear only as fallback (Selected)

**Description**

Local data is preserved on logout.  
If a different user logs in:

- preserve anonymous data created after logout
- remove stale local data that belongs to the previous authenticated account
- upgrade the anonymous data into the new account
- clear the full database only when there is no anonymous session to preserve

**Pros**

- Preserves offline-first anonymous work
- Prevents cross-account data leakage
- Avoids pushing stale previous-account data during the next sync
- Fits the app's anonymous-first product model

**Cons**

- Slightly more complex than unconditional database clearing
- Requires explicit handling of previous authenticated data during account switch

**Conclusion**

Selected as the best balance between **correctness, offline-first UX, and implementation complexity**.

---

## Future Considerations

We still need to define a eviction policy to determine what content stays cached as the local DB grows.

If ChefAI later needs to support **true multi-user devices**, the architecture may evolve to:

- Scope entities by `ownerId`
- Maintain multiple user datasets locally

At the current stage of the product, this additional complexity is not justified.
