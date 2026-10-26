# ADR 017 – Cook History Sync

**Date:** October 2026
**Status:** Accepted
**Author:** Claude (Sonnet 5.5), on behalf of Jose Mucientes
**Related:** [ADR-006](adr-006-sync-protocol.md), [ADR-014](adr-014-households.md),
Android [#273](https://github.com/jmuci/ChefAI/issues/273) /
[#294](https://github.com/jmuci/ChefAI/pull/294) (local `recipe_cook_events`), backend
[Chef.ai_Backend#107](https://github.com/jmuci/Chef.ai_Backend/pull/107)

---

## Context

PR #294 added `recipe_cook_events`, the durable "cooked before" history behind the Recipes tab's
filter. It was **local-only**: reinstalling the app, or signing in on a second device, lost it. The
cooked mark on a meal-plan slot (`meal_plan_days.dinnerCookedAt`/`lunchCookedAt`) is also local-only
and stays so — what we want on the server is the *history*, not the mark.

Two properties of the data shape the protocol:

- **It outlives its parents.** The table has no foreign keys to plans, days or recipes, so history
  survives deleting a plan (#294). The server must not add FKs either.
- **It is a log, not a document.** Cooking the same recipe twice is two rows, and an event's content
  never changes. The only edit is "I un-marked that meal".

The existing LWW-by-`updatedAt` machinery (ADR-006) fits mutable documents; applying it to a log
invites resurrection bugs (a stale device re-pushing a live event after another device un-marked
it).

## Decision 1: An event log piggybacked on push/pull, with final tombstones

`cookEvents` rides on `POST /sync/push` and `GET /sync/pull` like bookmarks and grocery items — no
new endpoint. The client-generated event `id` is the idempotency key. Un-marking pushes the same
`id` with `deletedAt` set; **a tombstone is final for its id**. Re-marking the meal mints a **new**
id, so there is no resurrection, no conflict, and the push response has no `conflicts` list.

Alternatives rejected:

- *LWW per event* — needs a resurrect rule and conflict handling for data that has neither a
  legitimate concurrent edit nor a merge.
- *A "recipe ids cooked" set* — loses multiplicity and the plan/slot identity an un-mark on another
  device needs to find the event again.

## Decision 2: `RecipeCookEventEntity` becomes `SyncableEntity` (DB v13)

Adds `updatedAt`, `deletedAt`, `syncState` (`MIGRATION_12_13`). Existing rows backfill as
**`PENDING`** — unlike `shopping_list_checks` (`MIGRATION_6_7`, backfilled `SYNCED`), cook history has
never reached the server, so every existing event must upload. `updatedAt` backfills from `cookedAt`.

`setMealCooked(…, cooked = false)` now **tombstones** the slot's live event (`deletedAt` set,
`PENDING`) instead of deleting it, and `observeCookedRecipeIds` excludes tombstones, so the user-visible
rule from #294 — an undone mis-tap leaves no history — is unchanged. Marking is idempotent (an
existing live event for the slot and recipe is kept); marking after an un-mark inserts a new
UUIDv7. A mark also calls `requestMutationSync()`, since the cooked mark alone never did.

## Decision 3: Push and pull semantics in `SyncOrchestrator`

**Push.** Dirty (`PENDING`/`DELETED`) events ride in the last batch. On `accepted` the row is marked
`SYNCED` with the server timestamp, guarded by `expectedUpdatedAt` (as for meal plans): a tombstone
written while the push was in flight leaves the row `PENDING` so it is pushed next. Per-event
`errors` must not keep a row `PENDING`, or every sync resends it:

| Reason | Handling |
|--------|----------|
| `EVENT_ID_TAKEN` | live event: re-key with a new UUIDv7 and re-queue; tombstone: delete the row (the server never took the live event under that id, so there is nothing to retract) |
| `INVALID_*` | validation failure no resend can fix: park as `CANCELLED` (excluded from `getAllDirty`), keep it locally so "cooked before" still counts it |

**Pull.** Events apply idempotently, per event id: a tombstone marks the local row (even over a
`PENDING` live one — final server-side) and is skipped when the id is unknown; a live event is
inserted when new and otherwise left alone, and never revives a locally un-marked row. No FK means a
pulled event never needs its plan or recipe on this device. The array is **not paginated and does
not move the pull cursor** (the server returns it on every page; re-applying is a no-op).

## Consequences

- **Per user.** The server never shares cook history with a household (ADR-014 does not apply). The
  local table is still wiped per account by `clearAllTables()` on a switch.
- **Deploy order.** The server must accept `cookEvents` first. An older backend ignores the field on
  push, so events would stay `PENDING` unacknowledged — harmless but unsynced. Backend #107 ships
  before this client.
- **Sync payload gains a field.** Optional with an empty default in both directions, so older
  clients and servers are unaffected.
- **Tombstones are kept forever** locally (a handful of bytes each). Pruning `SYNCED` tombstones is
  possible later; it is not needed for correctness since the server's tombstone is final.
- **Still local-only:** the per-slot cooked mark. After a pull on a second device, "cooked before"
  is right but the meal-plan slot itself is not shown as cooked there.
