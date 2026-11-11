# ADR-008: Local Database Behavior on Logout and Account Switch

**Date:** 2026-03-08 (amended Aug 2026, Nov 2026)

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

### Amendment (Nov 2026): how `AccountSwitchHandler` actually decides — and a known gap

Two places where the code is narrower than the rules above:

- **"Active anonymous data" is checked as "was the session anonymous".** `SessionManager.login()` /
  `register()` pass the anonymous `localUserId` whenever the previous session was `Anonymous`, which
  is always the case on the login screen. So an interactive switch always takes the *preserve*
  branch, even with no anonymous data at all; `clearAllTables()` is reached only from a cold-start
  `loadSession()` reconciliation.
- **The preserve branch keeps only what the anonymous session owns.** It removes, in one
  transaction: the previous account's recipes (+ images); every meal plan, saved plan (+ photo) and
  bookmark not owned by the anonymous user (so household plans the old account could see go too);
  every cook event not on one of the anonymous user's plans; and the pull cursor. The household
  cache is cleared as well. The server restores whatever had synced when the old account signs in
  again. Whatever the old account still had `PENDING`/`DELETED` is deleted with it, so logout now
  tries to push it first — see the next amendment.

### Amendment (Nov 2026): logout pushes pending changes first (best effort)

The preserve branch above traded "the departing account's rows leak to the next account" for "the
departing account's unpushed edits are lost". `LogoutUseCase` narrows that loss; the profile menu's
logout goes through it.

- **Before the token is cleared**, it runs one push-only pass (`PendingChangesPusher`, implemented
  by `SyncOrchestrator`, no pull) with the still-valid token, capped at
  `LogoutUseCase.PUSH_TIMEOUT_MS` (4 s). A 401 gets one token refresh and one retry, as in
  `SyncWorker`.
- **Logout is never blocked by it.** No network, a timeout or a failure all end in
  `SessionManager.logout()` anyway. The push runs in the caller's coroutine (the menu's
  `viewModelScope`); cancelled mid-push, nothing has been cleared yet and the user is still signed in.
- **Offline with changes waiting, the user is asked first** ("Log out anyway" / "Stay signed in"),
  since no push can even be tried. Online, logout goes ahead and a snackbar reports any rows still
  dirty afterwards (timeout, network failure, or rows the server rejected).
- **What's left is not deleted at logout.** Unpushed rows stay on the device; signing back in with
  the *same* account uploads them on its next sync. They are deleted only when a *different* account
  signs in here first (the preserve branch).

Still not covered: rows the server keeps rejecting are lost on an account switch however often
logout retries them; and the developer-options environment switch (`SwitchApiEnvironment`) still
calls `SessionManager.logout()` directly, without the push.

**Fixed (Nov 2026):** the preserve branch used to keep the previous account's meal plans, saved
plans, bookmarks and cook events on the theory that every read filters by `userId`. Two things
didn't: `recipe_cook_events` has no user column and `observeCookedRecipeIds()` reads the whole
table (the incoming account saw the old "Cooked before" history), and `SyncOrchestrator.push()`
collects meal plans, saved plans and cook events without a user filter (the old account's `PENDING`
rows went out under the new JWT). Keeping the old pull cursor also made the new account's first
pull skip everything it had changed before that moment.

### Amendment (Nov 2026): an interrupted upgrade is finished on restore

`login()`/`register()` save the tokens before the account switch and the upgrade run, so a process
death in between used to leave the anonymous rows owned by an id nothing reads (or, after an
account switch, let the cold start's `handleLogin` wipe them as "nothing to preserve").

- The anonymous id is written as `pendingUpgradeFrom` in the same DataStore edit as the tokens, and
  cleared once `AccountUpgradeUseCase` commits. A failed upgrade leaves it set.
- `loadSession()` finishes a pending upgrade (`handleLogin` with the anonymous id, then the
  upgrade) before the session becomes `Authenticated`. Both steps are no-ops when repeated.
- Every credential login (`isCredentialLogin = true`) clears the pull cursor, the same-account
  branch included. A plain session restore does not, or every cold start would pull from 0.
- Logout also calls `POST /auth/logout` with the refresh token, fire-and-forget on the
  application scope; a failure is logged and the token expires on its own.
- `register()` that answers 409 right after a timed-out attempt for the same email logs in with
  the same credentials once; if that fails too, the 409 is reported.

### Amendment (Aug 2026): these rules cover on-disk files, not just Room tables

The rules above were written purely in terms of the database, which left cached recipe images
(`filesDir/recipe_images/`, added by [ADR-010](adr-010-client-side-recipe-scraping.md) Decision 6)
outside them. Two consequences, both since fixed — see
[ADR-012](adr-012-recipe-image-upload.md) Decision 10:

- **"Removes stale data belonging to the previous authenticated user" includes their images.** Only
  the recipe rows were being deleted, so on a shared device the previous account's photos stayed
  readable after someone else logged in. The departing recipe ids must be collected *before* the bulk
  delete, since afterwards there is no way to tell which files belonged to whom. A blanket
  `deleteAll()` is wrong in this branch — it exists to preserve the anonymous session's own images.
- **A soft-deleted recipe releases its image immediately.** Nothing in the app ever hard-deletes a
  soft-deleted row, so deferring would retain the bytes permanently.

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
