# ADR 016 – A Structured Course Field on Recipes

**Date:** October 2026
**Status:** Proposed
**Author:** Claude (Opus 5.5), on behalf of Jose Mucientes
**Related:** [ADR-006](adr-006-sync-protocol.md), [ADR-010](adr-010-client-side-recipe-scraping.md),
[ADR-015](adr-015-meal-plans-week-view.md), PR [#267](https://github.com/jmuci/ChefAI/pull/267)
**Backend prompt:** [`docs/prompts/recipe-course-backend-prompt.md`](../prompts/recipe-course-backend-prompt.md)

---

## Context

Auto-generated meal plans should fill lunch and dinner slots with **mains and salads only**. Until
#267 they would happily schedule a cheesecake for Tuesday dinner, because nothing in the data model
says what *kind* of dish a recipe is.

What a recipe carries today that comes close:

- `tags` and `labels` — many-to-many, free-text `displayName`s, user-editable vocabulary. The
  seeded catalog uses labels like `Dessert`, `Italian`, `Quick`, `Healthy`, `Chicken`: course,
  cuisine, speed and ingredient all mixed in one bag.
- Nothing else. `RecipeEntity`/`SyncRecipeDto` have no course, category or meal-type column.
  (`SourceClassification.category` is an *ingredient* grocery classification, unrelated.)

#267 shipped the stop-gap: `MealPlanScheduler` drops recipes whose tag/label names exactly match a
dessert denylist (`dessert`, `cake`, `cookies`, …). It works for the seed catalog, but it is the
wrong shape for the rule we actually want:

1. **It is a denylist; the product rule is an allowlist.** "Mains and salads only" means
   breakfasts, drinks, snacks, sauces and sides should be excluded too. A denylist only grows; every
   untagged smoothie is a dinner candidate.
2. **It only runs on-device.** Most plans are generated server-side — authenticated sessions via
   `POST /meal-plans/{id}/generate`, anonymous `INCLUDE_PUBLIC` via `POST /api/v1/meal-plans/generate`.
   The backend has no equivalent filter, and re-implementing tag-text matching there is the same
   fragile heuristic twice.
3. **Free text can't be validated.** A label renamed from `Dessert` to `Sweet treats` silently
   re-admits every dessert.

We also already throw away the best signal we get for free: schema.org `recipeCategory`, which
nearly every recipe site publishes as JSON-LD, is parsed by `:recipe-scraper`
(`JsonLdRecipeMapper.keywords()`, `MicrodataExtractor`) and then flattened into tags alongside
`keywords` and `recipeCuisine`.

---

## Decision 1: A single nullable `course` column on `recipes`, not a label

Add one column, `course`, holding one value of a closed enum:

```kotlin
// core/domain/model/RecipeCourse.kt
enum class RecipeCourse { MAIN, SALAD, SOUP, SIDE, STARTER, BREAKFAST, DESSERT, SNACK, DRINK, CONDIMENT }
```

- **Nullable, and `null` means "unknown"**, never "none". Every existing recipe starts `null`.
- **Single-valued.** A recipe has one *primary* course. A salad that works as a side is still
  `SALAD`; a soup served as dinner is still `SOUP`. Multi-valued would push the ambiguity into every
  consumer, and the one consumer we have (generation) needs a yes/no answer.
- **Stored as the enum name** (`TEXT` in SQLite and Postgres, with a `CHECK` constraint server-side),
  snake_case column name `course`.
- `CONDIMENT` covers sauces, dressings, spice mixes and stocks: things that are recipes but never
  a meal.

**Alternatives rejected:**

- *A reserved label namespace* (e.g. labels prefixed `course:`). No schema change, but it inherits
  every problem in the Context: free text, many-valued, invisible to a `WHERE` clause without string
  matching.
- *A `courses` set / join table.* More expressive, but nothing needs it yet, and turning a scalar
  into a set later is a cheap migration. Going the other way is not.

## Decision 2: Wire format, and `null` never clears a server value

`SyncRecipeDto` gains `val course: String? = null`. The defaulted parameter means a payload from a
server that predates the field still deserialises (`ignoreUnknownKeys` already covers the reverse).

- The client maps the wire string through `RecipeCourse.fromWire(String?): RecipeCourse?`, which
  returns `null` for anything it doesn't recognise. The server can add an enum value without
  breaking older clients; they just treat it as unknown.
- **On push, the server applies `COALESCE(:incoming, existing)`.** A `null` from a client never
  overwrites a known course. This covers two windows that would otherwise wipe the backfill
  (Decision 4):
  1. Builds without the field push full rows with no `course`.
  2. On a new build, a recipe that was `PENDING` at upgrade time still has `course = null` locally,
     and push runs before pull (ADR-006).

  The cost: a user can change a recipe's course but not reset it to "unknown". The editor
  (Decision 5) offers no "unknown" choice, so this matches the UI.
- Otherwise `course` follows the recipe's ordinary last-writer-wins on `updatedAt`. It is
  user-editable data, unlike the server-owned `imageBlobId`.

## Decision 3: Generation eligibility is an allowlist, with the #267 heuristic only for unknowns

Both generators, `MealPlanScheduler` on-device and the backend's, apply the same rule before any
preference filter:

| `course` | Eligible for an auto-generated lunch/dinner slot |
|---|---|
| `MAIN`, `SALAD` | Yes |
| any other value | No |
| `null` (unknown) | Yes, **unless** the #267 dessert heuristic matches its tags/labels |

- **Unknown stays eligible.** Excluding it would empty the plan for every user whose collection
  predates this ADR, and user-created recipes stay `null` until someone sets them (Decision 4).
- **No fallback to the unfiltered pool**, as in #267. Diet and prep-time filters fall back when
  they'd leave nothing; this one doesn't, because a week of desserts is worse than no plan.
- **Manual planning ignores `course` entirely.** Anything a user picks by hand goes in.
- The eligible set (`MAIN`, `SALAD`) is a constant in each generator for now. `SOUP` is the obvious
  next addition. Making it a `MealPlanPreferences` option is deferred until someone asks for it.

## Decision 4: The catalog is classified once, into a reviewed mapping file

The public seed catalog is classified by an LLM agent, but **the agent never writes to a database**.
It produces a file that a human reviews and a deterministic migration applies:

```
# ktor-chefai: seed/recipe-courses.csv  (path indicative)
recipe_id,course,confidence,rationale
dead0000-cafe-4000-8000-000000000112,DESSERT,0.98,"Labelled Dessert; sugar/butter/flour; baked cookies"
…
```

1. **Input per recipe:** title, description, tags, labels, ingredient names, and the first few
   steps. **Output:** one row with a course, a 0–1 confidence and a one-line rationale.
2. **Confidence below 0.8 → `course` is left empty** and the row is listed for manual review. A
   wrong label costs more than an unknown, because unknown falls back to the heuristic.
3. **A backend migration applies the file.** The seed fixtures are regenerated from the same file,
   so a fresh database and a migrated one agree. Re-running is idempotent; correcting a
   classification is a one-line diff to the CSV.
4. **Scope: `PUBLIC` seed and catalog recipes only.** Users' private recipes are never sent to an
   LLM as part of this backfill. They stay `null` and rely on the heuristic until the user sets a
   course or re-imports.

**Getting the backfill onto devices.** Backfilled rows get no `updated_at` bump: falsifying the
timestamp would make the server beat any genuinely newer local edit under LWW. Instead, the
client's `MIGRATION_10_11` deletes the `sync_metadata` cursor row for recipes, so the next sync
re-pulls the full recipe set. `applyPulledRecipe` overwrites `SYNCED` rows and applies the normal
LWW check to `PENDING` ones, so local edits are safe. Anonymous sessions never pull, so their
local copies stay `null`. That's acceptable: their `INCLUDE_PUBLIC` plans are generated
server-side, and `COLLECTION_ONLY` falls back to the heuristic.

## Decision 5: New recipes get a course at the edge, from the source when possible

- **URL import.** `:recipe-scraper` keeps feeding `recipeCategory` into `keywords` (tag behaviour
  is unchanged) and also exposes the raw values as `ScrapedRecipe.recipeCategories: List<String>`.
  This **changes the module's public API**, additively. A pure `RecipeCourseClassifier` in `:app`
  maps source vocabulary (`Main Course`, `Main Dish`, `Entrée`, `Dinner`, `Salad`, `Dessert`,
  `Breakfast`, `Beverage`, …) onto `RecipeCourse`. No match leaves `null`. The classifier lives in
  `:app`, not the scraper, because the enum is our domain vocabulary, not schema.org's.
- **Editor.** A course picker, pre-filled from import. It's optional, so saving without one keeps
  `null`. `recipe_drafts` carries the value too, so an in-progress import doesn't lose it.
- **Server-side classification of new user recipes** (rules or an LLM, on push) is deferred. The
  heuristic covers unknowns, and the privacy question in Decision 4 applies again.

## Decision 6: Client-side shape

- `RecipeEntity.course: String?` → domain `Recipe.course` / `RecipePreview.course: RecipeCourse?`,
  mapped in `data/mapper/` like every other boundary. Room stores the enum name; no `TypeConverter`
  is needed beyond `fromWire`.
- Room **v10 → v11**: `ALTER TABLE recipes ADD COLUMN course TEXT` and the same on `recipe_drafts`,
  plus the `sync_metadata` cursor reset (Decision 4). Migration test with an in-memory DB.
- `MealPlanScheduler` replaces `filterNot { it.isDessert() }` with
  `filter { it.isEligibleForAutoPlan() }`, which implements Decision 3's table. The #267 keyword set
  stays, now only for the `null` branch.

---

## Consequences

**Positive**
- One enum answers the generation question on both client and server, and the server's answer can
  be a `WHERE course IN ('MAIN','SALAD') OR course IS NULL` rather than string matching.
- The rule becomes the allowlist the product actually wants. Breakfasts, drinks and sauces drop out
  without growing a denylist.
- A signal we already parse (`recipeCategory`) finally gets used for something other than tags.
- The backfill is auditable: a CSV in git, reviewed before it touches a database.

**Negative / accepted**
- A schema change in two repos plus a sync DTO field. Rollout order is fixed: backend first
  (column + `COALESCE` + backfill), then client.
- Users' private recipes stay unclassified until they set a course themselves. Generation keeps the
  #267 heuristic indefinitely for them.
- A course can be changed but not cleared (Decision 2).
- The one-off full re-pull on upgrade costs bandwidth proportional to the visible catalog. That's
  fine at today's size, but worth knowing if the catalog grows a lot before this lands.
- Single-valued course flattens real ambiguity (a salad can be a main or a side). The classifier's
  confidence threshold and the editor are the escape hatches.

**Follow-ups (not part of this ADR)**
- A course filter on Search and course chips on recipe cards.
- Making the eligible course set a meal-plan preference, e.g. allowing `SOUP` or `BREAKFAST` for a
  lunch slot.
- Server-side classification of newly pushed user recipes.
