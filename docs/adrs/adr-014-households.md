# ADR 014 – Households: Shared Meal Plans & Grocery Lists

**Date:** September 2026
**Status:** Accepted (Android side landing incrementally — see Status below)
**Author:** Claude (Sonnet 5), on behalf of Jose Mucientes
**Related:** [ADR-006](adr-006-sync-protocol.md), [ADR-007](adr-007-anonymous-first.md),
[ADR-008](adr-008-data-handling-across-sessions.md),
`docs/prompts/households-android-prompt.md`, `docs/prompts/households-backend-prompt.md`

---

## Context

Every row in ChefAI is scoped to a single user — `recipes.creator_id`, `meal_plans.user_id`, and
every sync query filters `= userId`. Households let a family or flatshare share **one meal plan and
its grocery list**, editable by everyone in it.

Two things constrain the answer:

1. **Re-owning existing data is off the table.** Migrating `meal_plans` (or worse, `recipes`) to
   household ownership would be a large, hard-to-reverse change to a sync protocol already in
   production. Sharing has to be a layer added on top, not a rewrite of what's underneath.
2. **The grocery list has no sync path at all today.** `shopping_list_checks` is deliberately
   local-only (same pattern as the meal-plan cooked toggle) — there is no field for it in the sync
   payload. Sharing a list means building that path from nothing, on both client and server.

The full design — including the backend half — lives in
`docs/prompts/households-backend-prompt.md` and `docs/prompts/households-android-prompt.md`; this
ADR records the decisions those prompts commit to, for anyone who lands in this code without having
read either.

---

## Decision 1: Sharing is a scope layer, not an ownership migration

`recipes.creator_id` never changes. Three new tables (`households`, `household_members`,
`household_invites`) and one nullable `meal_plans.householdId` are the entire schema surface. A plan
with `householdId == null` behaves exactly as it does today — sharing is opt-in per plan, not a
mode switch on the account.

A recipe assigned to a shared plan's day becomes visible to members through the backend sync
protocol's existing **gap clause** (the same union pattern that already keeps an old tag reachable
when a newer recipe references it) — never by changing the recipe's `privacy`, and never by
re-owning it. A user's recipe library, search results, and bookmarks are **not** shared and are not
touched by this feature at all.

## Decision 2: Household membership lives on `UserSession.Authenticated`, not a parallel state

```kotlin
data class Authenticated(
    val user: User,
    val authToken: AuthToken,
    val household: AuthenticatedHousehold? = null,
) : UserSession()

data class AuthenticatedHousehold(val householdId: UUID, val role: HouseholdRole)
```

Every existing `when (session)` call site keeps compiling untouched, and "no household yet" is
simply `household == null` rather than a fourth session state or a second `StateFlow` that could
drift out of sync with the first. A user belongs to **at most one household** — enforced by a DB-level
partial unique index on the backend, not just a UI convention — so there is no active-household
switcher anywhere in this app.

## Decision 3: Household CRUD is plain REST, not the sync protocol

Create/rename/delete a household, create/accept/decline an invite, and remove/leave a member are
authorization operations, not offline-editable data with a dirty queue. They go through a direct
Ktor `HouseholdApiService`, the same shape as `AuthApiService` — no `syncState`, no push/pull.

The local `households` / `household_members` / `household_invites` tables
([`HouseholdEntity`](../../app/src/main/java/com/tenmilelabs/chefai/core/data/local/room/HouseholdEntity.kt),
[`HouseholdMemberEntity`](../../app/src/main/java/com/tenmilelabs/chefai/core/data/local/room/HouseholdMemberEntity.kt),
[`HouseholdInviteEntity`](../../app/src/main/java/com/tenmilelabs/chefai/core/data/local/room/HouseholdInviteEntity.kt))
are a **read-through cache**: wholesale deleted and reinserted on every refresh, the same idiom
`meal_plan_days` already uses for a plan's days. There is deliberately no local foreign key from
`meal_plans.householdId` to `households.uuid` — a real FK would either cascade-delete plans on every
refresh or block the refresh outright, exactly why `meal_plan_days.dinnerRecipeId` also carries no
local FK.

**Consequence, accepted deliberately:** a member removed from a household keeps seeing stale local
membership (including a stale role) until the next successful refresh. This is fine only because the
client cache is never the security boundary — every mutation is re-authorized against the server's
own membership table regardless of what this device's cache still believes.

## Decision 4: `shopping_list_checks` becomes a `SyncableCrossRef`, not a `SyncableEntity`

Its primary key is the composite `(mealPlanId, itemKey)` — there is no single `uuid` identity for
`SyncableEntity` to attach to. `SyncableCrossRef` (already used by
[`BookmarkedRecipeEntity`](../../app/src/main/java/com/tenmilelabs/chefai/core/data/local/room/BookmarkedRecipeEntity.kt),
itself keyed on a composite `(userId, recipeId)`) is the existing interface for exactly this shape,
and is what this entity now implements.

An explicit `checked: Boolean` column is added rather than continuing to encode "checked" as "the row
exists" — a boolean survives becoming a synced, last-writer-wins field the way a row's mere presence
cannot (a delete and a "this device hasn't heard about it yet" are indistinguishable on the wire
otherwise). **Transitionally, this column has no consumer yet**: unticking an item still deletes the
row, exactly as before, until the sync wiring for a shared list lands in a later change. The schema
lands once, now, so that later change is additive rather than a second migration.

## Decision 5: The Room migration backfills existing rows as historical fact, not as new edits

Every pre-existing `shopping_list_checks` row is backfilled `checked = 1`, `syncState = 'SYNCED'`,
`updatedAt = checkedAt`. It is tempting to default new sync-tracking columns to `'PENDING'` so
nothing is "missed" — but a `PENDING` row is a promise to push it as if the user just ticked it,
which isn't true of a tick made months before this feature existed, and the server may not even
recognize the plan it belongs to as this device's to edit yet. The row stays a local fact until the
next real toggle marks it `PENDING` again.

---

## Decision 6: App Links host and token extraction

Invite links use `https://chefai.app/invite?token=...` — matching the backend's default
`household.inviteBaseUrl` (`ktor-chefai`'s `Application.kt`), not a placeholder chosen
independently on the Android side.

`InviteLinkParser.extractToken` (`core/util/`) takes a plain `String`, not `android.net.Uri`,
parsed via `java.net.URI` — the same reason [`extractSharedRecipeUrl`] parses share-sheet text
that way rather than with the Android type: it keeps the parser unit-testable on the plain JVM,
with no Robolectric dependency. `MainActivity.consumeInviteIntent` converts `intent.data` to a
`String` at the one call site that has to. Wrong scheme, wrong host, wrong path, or a missing/blank
`token` query parameter all return `null` uniformly, mirroring `previewInvite`'s own "uniform 404"
precedent from A3 — the caller only needs to know "was there an invite here," not why not.

`MainActivity.consumeInviteIntent` mirrors the existing `consumeShareIntent` pattern exactly
(capture into `mutableStateOf`, clear the intent's data as it's read, call from both `onCreate` and
`onNewIntent`) rather than adopting Navigation-Compose's own deep-link machinery — one intent-entry
pattern in this codebase, not two.

**Manual verification** (matching `docs/sync-deep-dive.md`'s checklist style):

```
adb shell am start -W -a android.intent.action.VIEW -d "https://chefai.app/invite?token=abc" com.tenmilelabs.chefai
```

Verify: cold start (app not running), warm start (app backgrounded), and after `onNewIntent` while
the app is already foregrounded on some other screen — all three should land on the accept-invite
screen. Real device verification of the `https` App Link itself (not just the `am start` shortcut
above, which bypasses domain verification) additionally requires `assetlinks.json` to be live at
the real host — see the Consequences section below.

---

## Status

This ADR is written and the Room schema (this migration, v8→v9) lands in the same change. The
household domain/network/UI layers and App Links (PRs A1–A7) have landed. The
anonymous→authenticated join flow and the actual sync wiring for `meal_plans.householdId` and
`shopping_list_checks` land in a sequence of further changes — see
`docs/prompts/households-android-prompt.md` §9 for the full PR sequence and which backend endpoints
each step depends on.

---

## Consequences

**Good**

- Zero risk to existing personal recipes, meal plans, or accounts — every existing row's behavior is
  provably unchanged (`householdId` defaults to null; every other column is untouched).
- The read-through cache design means household membership can never itself cause a Room FK
  violation on refresh, regardless of ordering between household and meal-plan sync.
- The schema for the eventual shared-grocery-list sync lands in one migration instead of two.

**Bad / deferred**

- **No deferred deep linking.** An invite link opened when the app isn't installed has no Play
  Store hand-off yet; the manual invite-code entry screen is the acknowledged v1 fallback.
- **Silent last-writer-wins on the grocery list**, once it's wired up: two people ticking the same
  item in the same aisle will occasionally see a tick disappear with no explanation. `SyncState
  .CONFLICT` exists in the enum but is unused here, same as everywhere else in this codebase.
- **No "who checked this" attribution** in the UI, even though `checkedBy` exists on the wire —
  deferred as a follow-up once the base sync path is proven.
- **`assetlinks.json` hosting is outside this repository.** App Links verification silently falls
  back to a disambiguation dialog until `https://chefai.app/.well-known/assetlinks.json` is served
  with this app's signing cert fingerprint — a deploy-pipeline dependency, not something fixed by
  an app-side change. `android:autoVerify="true"` is already set on the intent filter; it has
  nothing to verify against until that file exists.
