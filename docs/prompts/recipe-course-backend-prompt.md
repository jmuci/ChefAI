# Backend Prompt: Recipe `course` Field, Catalog Backfill, and Course-Aware Meal-Plan Generation

> Generated: October 2026
> Decision record: [ADR-016](../adrs/adr-016-recipe-course.md) in the Android repo.
> Client stop-gap (open): PR [#267](https://github.com/jmuci/ChefAI/pull/267) (on-device dessert denylist).
>
> **This document is self-contained. It can be handed to a session in the `ktor-chefai` repo
> as-is.** It was written from the Android repo without reading the backend's source, so file
> names below are indicative; find the real ones. Where this spec and the backend's existing
> conventions disagree on *mechanics* (migration tool, test style, fixture layout), follow the
> backend's conventions. Where they disagree on *behaviour* (§2 push semantics, §4 eligibility),
> follow this spec, or stop and raise it.

---

## TL;DR

1. Add a nullable `course` column to `recipes`: a closed enum stored as `TEXT` with a `CHECK`.
2. Carry it on the sync wire (`SyncRecipeDto.course`, also on the single-recipe
   `GET /api/v1/recipes/{recipeId}`). **On push, a `null` never overwrites a non-null value.**
3. Classify the **public seed catalog** with an LLM agent into a reviewed CSV, then apply it with a
   deterministic migration and regenerate the seed fixtures from the same CSV.
4. Both generation endpoints only auto-schedule `MAIN` and `SALAD` recipes, plus unknowns that
   don't look like desserts.

Ship order: this backend change first. The Android client ships its half after it's deployed.

---

## 1. Schema

```sql
ALTER TABLE recipes ADD COLUMN course TEXT NULL
  CHECK (course IN ('MAIN','SALAD','SOUP','SIDE','STARTER','BREAKFAST','DESSERT','SNACK','DRINK','CONDIMENT'));
CREATE INDEX recipes_course_idx ON recipes (course);  -- optional; generation filters on it
```

- **`NULL` means unknown**, not "none". Every existing row starts `NULL` until §3 runs.
- Single-valued: one primary course per recipe.
- `CONDIMENT` covers sauces, dressings, spice mixes and stocks.
- Mirror the enum in Kotlin (`RecipeCourse`) and map it in the Exposed table definition the way
  existing enum-like columns (`privacy`) are mapped.

## 2. Sync and API wire format

Add `course: String?` (the enum name, or `null`) to:

- `SyncRecipeDto`, on both `POST /sync/push` and `GET /sync/pull?since=…`
- the anonymous-capable `GET /api/v1/recipes/{recipeId}` response (same aggregate shape as a pull
  page)
- the recipe search results, only if they already carry full recipe fields. Don't widen them just
  for this.

**Push semantics:**

- **`COALESCE(:incoming, existing)`.** A push carrying `course = null` or no `course` key at all
  must leave a stored non-null value untouched. Two kinds of client send nulls that must not wipe
  the backfill: Android builds that predate the field, and recipes that were locally dirty when a
  client upgraded. A user can change a course but never clear it back to unknown, and the client
  UI is designed around that.
- A non-null incoming value follows the recipe's normal last-writer-wins on `updatedAt` (ADR-006),
  like any other user-editable field.
- An unrecognised value is a `400` for the request, matching how invalid `privacy` is handled.
  Don't coerce it to `NULL`.

**Pull semantics:** emit the stored value as-is. The client already maps unknown strings to `null`,
so adding an enum value later is backward compatible.

**Do not bump `updated_at` when the backfill (§3) writes `course`.** A bumped timestamp would win
LWW over a genuinely newer unpushed edit on a device. The Android client resets its own pull cursor
on upgrade to re-fetch the catalog instead.

## 3. Catalog backfill: agent classifies, a human reviews, a migration applies

This is the part to be careful with. **The agent never writes to a database.**

### 3.1 Scope

Only recipes that are `privacy = 'PUBLIC'` **and** belong to the seed/editorial catalog (the
`ChefAI Editorial` creator, plus whatever else the seed fixtures load). **Never send users' private
recipes to an LLM as part of this.** User recipes stay `NULL`. Generation (§4) handles unknowns.

### 3.2 Produce the mapping file

For each in-scope recipe, gather: `uuid`, `title`, `description`, tag names, label names,
ingredient names, and the first three steps. Classify each into one `course` with a confidence in
[0, 1] and a one-line rationale. Write:

```
seed/recipe-courses.csv        # path indicative; put it next to the existing seed fixtures
recipe_id,course,confidence,rationale
dead0000-cafe-4000-8000-000000000112,DESSERT,0.98,"Labelled Dessert; sugar/butter/flour; baked cookies"
```

Rules for the classifier:

- **Confidence < 0.8 → leave `course` empty in the CSV** (the recipe stays `NULL`) and list it in
  the PR description for manual review. A wrong course is worse than an unknown.
- Pick the dish's **primary** course. A composed salad that is a full meal (protein + greens, e.g.
  chicken Caesar) is `SALAD`, not `MAIN`. A side salad is still `SALAD`. Soups are `SOUP` even when
  hearty.
- Breakfast-specific dishes (pancakes, overnight oats, granola) are `BREAKFAST`, even though
  they're sweet.
- Smoothies, juices and cocktails are `DRINK`. Sauces, dressings, pestos, spice blends and stocks
  are `CONDIMENT`.
- Ignore cuisine and dietary labels (`Italian`, `Vegan`, …). They don't determine course.
- Sort the CSV by `recipe_id` so diffs stay stable.

### 3.3 Apply it

- One migration reads the CSV (or a generated SQL file from it, whichever the migration tool
  prefers) and runs `UPDATE recipes SET course = :course WHERE uuid = :id AND course IS NULL`. It's
  idempotent, and it never overwrites a value a user has since set.
- **Regenerate the seed fixtures from the same CSV**, so a freshly seeded database and a migrated
  one are identical. If the fixtures are JSON/SQL with inline recipe rows, add `course` to each
  row. If they're generated, add it to the generator.
- To correct a classification later, make a one-line CSV edit plus a follow-up migration (or
  re-run, given the `IS NULL` guard). Never hand-edit rows in production.

## 4. Generation eligibility

Apply this **before** any preference filter (diet, prep time, variety), in **both**:

- `POST /meal-plans/{id}/generate` (authenticated, plan row exists)
- `POST /api/v1/meal-plans/generate` (anonymous-capable, stateless)

and for **both** `recipeSource` values (`COLLECTION_ONLY`, `INCLUDE_PUBLIC`).

| `course` | Eligible for an auto-generated lunch/dinner slot |
|---|---|
| `MAIN`, `SALAD` | yes |
| any other value | no |
| `NULL` | yes, **unless** any tag or label name, trimmed and lower-cased, is exactly one of `dessert`, `desserts`, `sweets`, `cake`, `cakes`, `cookie`, `cookies`, `pastry`, `pastries` |

- Match on **whole names**, not substrings: a `Sweet Potato` tag must not exclude a recipe. This
  keyword list is copied from the Android client's `MealPlanScheduler`; keep them in sync.
- **No widening fallback for this filter.** If the generator has a "not enough candidates → widen
  the pool" step for diet or prep time, it must not re-admit ineligible courses. Fill fewer slots
  (partial days with `null` are already allowed by `meal-plans-backend-prompt.md` §3) rather than
  schedule a dessert.
- Manual plan edits through sync are untouched. A user can put any recipe in any slot by hand.
- Keep the eligible set (`MAIN`, `SALAD`) as a single constant. It's expected to grow (`SOUP`) or
  become a preference later.

## 5. Tests

- **Migration:** column exists, `CHECK` rejects an unknown value, backfill is idempotent and skips
  rows with a non-null `course`.
- **Push:** (a) a push with `course` omitted keeps the stored value; (b) a push with
  `course: null` keeps the stored value; (c) a push with a different non-null value and a newer
  `updatedAt` overwrites it; (d) an invalid value returns `400`.
- **Pull / single-recipe GET:** `course` round-trips; `NULL` serialises as `null`.
- **Generation**, for each endpoint × `recipeSource`:
  - Seed a pool of `MAIN`, `SALAD`, `DESSERT`, `BREAKFAST`, untagged `NULL`, and `NULL` tagged
    `Dessert`. Assert only the `MAIN`, `SALAD` and untagged `NULL` recipes are ever assigned.
  - A pool of only ineligible recipes produces no assignments, not a widened pool.
  - A `NULL` recipe tagged `Sweet Potato` stays eligible.
- **Seed parity:** a freshly seeded DB and a migrated DB agree on `course` for every catalog
  recipe.

## 6. Definition of Done

- [ ] `course` column + `CHECK` + Kotlin enum + Exposed mapping (§1)
- [ ] `course` on `SyncRecipeDto` push/pull and `GET /api/v1/recipes/{recipeId}`, with `COALESCE` push
      semantics and no `updated_at` bump on backfill (§2)
- [ ] `seed/recipe-courses.csv` produced by the agent, sorted, low-confidence rows left empty and
      listed in the PR for review (§3.2)
- [ ] Backfill migration + seed fixtures regenerated from the same CSV (§3.3)
- [ ] Eligibility rule in both generation endpoints, both recipe sources, no widening fallback (§4)
- [ ] Tests per §5
- [ ] PR description states the deploy is a prerequisite for the Android client's ADR-016 change
      (Room v11 + cursor reset), so the client half isn't released first
