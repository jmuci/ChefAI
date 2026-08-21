# Backend Prompt: `COLLECTION_ONLY` Meal-Plan Generation Picks Recipes Outside the User's Collection

> Generated: August 2026
> No tracking issue filed yet — diagnosed live from a client-visible symptom (a permanently blank
> meal slot on a real test account), not from reading this repo's actual source.
> Spec this violates: [`meal-plans-backend-prompt.md`](./meal-plans-backend-prompt.md), §3
> "Recipe candidate filtering rules."
>
> **This document is self-contained — it can be handed to a session in the `ktor-chefai` repo
> as-is.** It was written entirely from the outside (pulling and inspecting the Android client's
> local Room database off a running emulator). Treat §2 as a strong lead, not a diagnosis of the
> code.

---

## TL;DR

`POST /meal-plans/{id}/generate` with `recipeSource: COLLECTION_ONLY` produced a plan whose day 2
dinner references a recipe UUID that is **not in the calling user's `bookmarked_recipes`** — the
documented candidate set for `COLLECTION_ONLY` is `recipe_id IN (SELECT recipe_id FROM
bookmarked_recipes WHERE user_id = :userId AND deleted_at IS NULL)`, and this recipe fails that
predicate. Because the client's recipe pull is scoped to `creator_id = :userId OR privacy =
'PUBLIC'`, and this recipe is apparently neither, the client can never receive it — the slot is
permanently unresolvable, no matter how many times the device syncs.

**Not theoretical** — reproduced from a real account's on-device state (`TestUser`,
`test1@ex.com`), currently in this condition:

```
Plan 01A02271-c655-7952-993e-2c7503a4e6a6  "3-day meal plan"  recipeSource=COLLECTION_ONLY
  Day 0 dinner → 01a00067-6565-7106-a554-e9179716eb63  ✅ in bookmarked_recipes, ✅ recipe exists locally
  Day 1 dinner → 019ffe7a-83a3-70ef-ab7f-9e3455373fd1  ❌ NOT in bookmarked_recipes, ❌ recipe never
                                                            arrived in any pull for this account
  Day 2 dinner → 019ffb62-b429-7e9e-a5dc-03f8a1689870  ✅ in bookmarked_recipes, ✅ recipe exists locally
```

Day 1's recipe is one that this plan's own candidate pool should never have contained.

---

## 1. How this was found

Pulled the running app's Room database directly off the device (`adb exec-out run-as
com.tenmilelabs.chefai cat databases/ChefAI.db{,-wal,-shm}`) for the account showing a "Recipe not
available" row in the meal-plan detail screen, then queried it with `sqlite3`:

```sql
-- The plan and its 3 days
SELECT hex(uuid), hex(mealPlanId), dayIndex, hex(dinnerRecipeId), hex(lunchRecipeId)
FROM meal_plan_days WHERE mealPlanId = x'01a02271c6557952993e2c7503a4e6a6' ORDER BY dayIndex;

-- Confirm two of the three recipes exist locally and are titled; the third returns no rows
SELECT hex(uuid), title, privacy, deletedAt, syncState FROM recipes
WHERE hex(uuid) IN ('01A0006765657106A554E9179716EB63',
                     '019FFE7A83A370EFAB7F9E3455373FD1',
                     '019FFB62B4297E9EA5DC03F8A1689870');
--  → only the first and third rows come back. The middle recipe (day 1's) isn't in the table at
--    all — not soft-deleted, just never delivered. (117 recipes total on device, 0 soft-deleted.)

-- Confirm it's also absent from this user's collection (the thing COLLECTION_ONLY should scope to)
SELECT hex(recipeId) FROM bookmarked_recipes WHERE hex(userId) = 'E69845FF5A3D49F89ED4F0F956EC8779';
--  → 7 rows, day 0's and day 2's recipe UUIDs both present, day 1's UUID is not one of them
```

So: the recipe is neither in the user's bookmarks nor anywhere in the ~117 recipes the client's
pull has ever delivered to this account, despite the plan itself being `syncState=SYNCED` (i.e.
came from the server, not locally generated — the on-device `LocalMealPlanGenerator` fallback only
ever assigns recipe UUIDs it just read out of local Room, so it structurally cannot produce a
dangling reference; this can only originate server-side).

---

## 2. What to check in `ktor-chefai`

Not knowing this repo's structure, in likely order of suspicion:

1. **Does the `COLLECTION_ONLY` candidate query actually filter by `bookmarked_recipes` for the
   requesting user, or does something upstream widen the pool first?** A plausible, sympathetic
   shape for this bug: the Android client's own on-device fallback scheduler
   (`MealPlanScheduler.eligible()`) deliberately *falls back to the unfiltered candidate list*
   when a stricter filter (diet, prep time) would leave zero matches, specifically so a short
   collection doesn't produce an empty plan. If the server generator has an analogous "not enough
   candidates in the strict set → widen the pool" fallback, and that fallback isn't scoped back
   down to `bookmarked_recipes ∪ own recipes` (e.g. it falls all the way through to "any public
   recipe" the same way `INCLUDE_PUBLIC` would), that would produce exactly this symptom for
   accounts with a small collection. Worth checking how many bookmarks `TestUser` had at
   generation time (7, per above) against `planLengthDays` (3) × meals/day (1, `mealType=DINNER`)
   — a pool of 7 should never need widening for a 3-slot plan, so if this *is* the mechanism, the
   widening threshold/condition itself is probably the bug (e.g. triggering on something other
   than "candidate count < slots needed").
2. **Is the candidate query scoped to the right user at all?** A `:userId` bound incorrectly (e.g.
   session/plan-owner mismatch, or the query defaulting to "all bookmarks" rather than "this
   user's bookmarks") would also explain a recipe from *someone else's* collection leaking in.
3. **Does anything cross-contaminate `COLLECTION_ONLY` with `INCLUDE_PUBLIC`?** E.g. a shared
   candidate-fetch code path where the `recipeSource` branch is evaluated once but a later
   ranking/scoring step re-queries without re-applying it.

A useful next step on that side: log or inspect the actual candidate set the generator computed
for this specific `(userId, planId)` at generation time, if that's recoverable — that would settle
whether the query itself was wrong or the *assignment* step pulled from somewhere it shouldn't
have after a correct query.

---

## 3. Impact

Any `COLLECTION_ONLY` plan generated for an account whose collection is small relative to
`planLengthDays × meals/day` risks landing a recipe the client can never resolve. Per-user this
shows up as a meal-plan slot stuck reading "Recipe not available" forever (see
[`MealPlanMealRow.kt`](../../app/src/main/java/com/tenmilelabs/chefai/mealplans/ui/components/MealPlanMealRow.kt))
— there's no client-side retry that fixes it, because the recipe was never a candidate the pull
endpoint would ever serve to that account (`creator_id = :userId OR privacy = 'PUBLIC'`, per
[RFC-001 §11.2](../rfcs/rfc-001-offline-first-sync.md)). It also *silently* inflates that plan's
progress denominator client-side today, though the Android client has since been changed to drop
an unresolvable reference outright rather than display it broken (see §5).

---

## 4. Suggested acceptance test

> Generating a `COLLECTION_ONLY` plan must never assign a `dinner_recipe_id`/`lunch_recipe_id`
> that is not in `SELECT recipe_id FROM bookmarked_recipes WHERE user_id = :userId AND deleted_at
> IS NULL` for that plan's owner — including when the collection is smaller than the number of
> slots being filled.

A test seeding an account with a small bookmark collection (fewer recipes than
`planLengthDays × meals/day`) and asserting every assigned recipe ID is a member of that exact set
would have caught this.

---

## 5. Client-side mitigation (already added — informational, no action needed here)

The Android client (`SyncOrchestrator.applyPulledMealPlan`) now drops a pulled day's recipe
reference if the recipe doesn't resolve locally, logging a warning instead of persisting the
dangling UUID — the slot then behaves like any other unfilled one (skipped by `MealPlanBoard`)
rather than rendering a permanent "Recipe not available" row. That bounds the *symptom* on this
one account and any future recurrence, but is not a substitute for the real fix: a plan generated
this way is still short a meal the user asked for, invisibly.

## 6. Definition of Done

- [ ] Root cause identified in the `COLLECTION_ONLY` candidate query or assignment step (§2)
- [ ] Fix makes every assigned recipe for a `COLLECTION_ONLY` plan a member of the owning user's
      `bookmarked_recipes` — including when the collection is smaller than the plan's slot count
      (fill fewer slots rather than reach outside the collection; §3 of
      `meal-plans-backend-prompt.md` already allows partial days with `null`)
- [ ] Regression test per §4, using a seeded account with a small collection
- [ ] Audit whether `INCLUDE_PUBLIC` generation has the analogous problem (a recipe that is
      neither `privacy = 'PUBLIC'` nor bookmarked by nor owned by the user leaking into an
      `INCLUDE_PUBLIC` plan) — not exercised by the repro above, but the same underlying mechanism
      (if §2.1's hypothesis is right) would affect it too
