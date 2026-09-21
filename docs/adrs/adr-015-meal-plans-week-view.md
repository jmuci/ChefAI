# ADR 015 – The Meal Plans Week View

**Date:** September 2026
**Status:** Accepted
**Author:** Claude (Opus 5), on behalf of Jose Mucientes
**Related:** [ADR-005](adr-0005-feature-based-package-structure.md),
[ADR-006](adr-006-sync-protocol.md), [ADR-013](adr-013-measurement-unit-conversion.md),
[ADR-014](adr-014-households.md)

---

## Context

The Modernist redesign replaces the Meal Plans screen wholesale. Every other screen in that
redesign is a restyle — the same data, drawn differently. This one is not. Today
`mealplans/ui/MealPlansScreen.kt` is a `LazyColumn` of `MealPlanCard`s: **a list of plans**, each
one an opaque object you open to see inside. Screen 06 of the handoff is **a week**: seven dated
rows, Mon–Sun, each holding either a planned meal or a "+ Add meal" link, with a week navigator
above and a grocery summary below.

Those are different questions. The old screen answers *"what plans do I have?"*. The new one
answers *"what am I eating on Thursday?"*. The second question is the one a meal planner exists to
answer, and the data model was never built to answer it, because **`MealPlan` has no date**.

What it has:

- `MealPlan.createdAt` / `.updatedAt` — epoch millis, bookkeeping timestamps, not schedule.
- `MealPlan.preferences.planLengthDays` — 3, 5 or 7.
- `MealPlanDay.dayIndex` — a bare `Int` offset within the plan. Not a date. Not even a weekday.

There is exactly one place in the codebase that has already had to turn a `dayIndex` into a
calendar date, and it did so by assumption:

```kotlin
// mealplans/ui/detail/MealPlanBoard.kt
fun mealPlanDateFor(planCreatedAt: Long, dayIndex: Int): LocalDate =
    Instant.ofEpochMilli(planCreatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        .plusDays(dayIndex.toLong())
```

Day 0 is the day you created the plan. That assumption was invisible on the detail screen, which
renders one plan's own days in order and never has to reconcile them with a calendar. A week view
cannot avoid reconciling them, and so this ADR is largely about making that assumption explicit and
deciding what to do at its edges.

A second question arrives with the design: the header carries a **"Just me" / "Family" segmented
control**. The handoff's own note on it is *"switches Meal Plans between single-serving and
family-scaled plans/quantities — exact scaling logic not specified in this prototype; needs product
input."* ADR-014 gives us the membership half of that for free (`MealPlan.householdId` is null for a
personal plan, non-null for a shared one); the *quantities* half has no field anywhere.

---

## Decision 1: A week is a date window projected over plans, not a plan

The week view does **not** pick a plan and render it. It picks seven consecutive dates, then asks
every live plan which of its days land on them.

```
MealPlanWeekProjector.project(
    plans: List<MealPlan>,      // the user's plans; ARCHIVED ones are dropped inside
    weekStart: LocalDate,       // the week's first day, per the locale
    recipes: Map<UUID, RecipePreview>,
    zone: ZoneId,               // from the injected clock
): MealPlanWeek                 // exactly 7 WeekDay rows, some empty
```

This lives in `mealplans/domain/week/MealPlanWeek.kt` — pure Kotlin, no Android imports, no
repository — for the same reason `ShoppingListBuilder` and `MealPlanScheduler` do: the projection
rules are the part worth testing, and they test better without a `ViewModel` around them. It mirrors
the relationship `MealPlanBoard` already has to `MealPlanDetailViewModel`, which is the local
precedent for "a pure view-model-shaped object built by a pure function."

The inversion matters more than it looks. A plan-first screen can only ever show you one plan at a
time and has nothing to say about the days between plans. A date-first screen renders a coherent
week whether that week is covered by one plan, two, or none — the empty days are simply days no plan
claimed, and they render as the design's "+ Add meal".

## Decision 2: Day 0 is the plan's `createdAt` date. There is no `startDate` column

`MealPlanWeekProjector.project` anchors every plan with `planDateFor` — two lines duplicated
deliberately from `MealPlanBoard.mealPlanDateFor`, since domain may not import from `ui/` and the
pair should collapse when a `startDate` column lands. A plan occupies the absolute range
`[createdAt.toLocalDate(), + planLengthDays - 1]`, and nothing new is persisted.

The alternative was to add `MealPlan.startDate`: a Room column with a v10→v11 migration, a field on
`SyncMealPlanDto` and a matching backend agreement, and a date picker in the wizard. It was
rejected **for this PR**, not on the merits — it is the correct model — but because a redesign PR
that migrates the schema and changes the sync payload is no longer a redesign PR, and because the
wizard screens it would touch were being edited in parallel.

The cost is real and should be named plainly rather than discovered later:

> **A plan created mid-week straddles two weeks.** Create a 7-day plan on a Thursday and the week
> view shows four planned days this week and three the next, with the rest offering "+ Add meal".

That is *arithmetically correct* for a date-anchored week view and *wrong* for the user, who thinks
of the thing they just created as "my week". Anchoring on `createdAt` means the schedule is a
side-effect of when you happened to tap Create. This is the single largest known shortcoming of the
screen, and `startDate` is the fix; see Consequences.

## Decision 3: One meal per (date, slot), resolved by `updatedAt`

Nothing prevents two live plans from covering the same date — the wizard does not check, and a
household member's shared plan can overlap your personal one. The design has one row per day, so
the projection must choose.

The rule: **highest `updatedAt` wins**, tie-broken by `createdAt`, then by `uuid` so that the
outcome is total and stable rather than dependent on query order. `ARCHIVED` plans are excluded
before the contest.

This is not a new conflict policy; it is the one ADR-006 already applies to sync
(`last-writer-wins based on updatedAt`), reused for presentation. Using a *different* tiebreak here
would mean the screen and the sync layer could disagree about which of two plans is the live one,
and there is no version of that conversation that ends well.

A day may still legitimately hold two meals — `MealType.DINNER_AND_LUNCH` fills both slots — and in
that case both render, stacked under a single date column. The contest is per **slot**, not per day.
Dropping the lunch to preserve a literal one-line-per-day reading would be hiding planned data to
match a screenshot.

## Decision 4: The selected week lives in the ViewModel, over an injected `Clock`

`MealPlansViewModel` holds `MutableStateFlow<LocalDate>` (the week start) and combines it with the
plans, household and preference flows. Prev/next shift it by ±7 days.

It is deliberately **not** in `SavedStateHandle`. Surviving rotation is what a `ViewModel` is for;
surviving process death is not wanted here — a user returning to a cold app wants this week, not
whatever week they were browsing yesterday.

`java.time.Clock` is injected (`@Provides Clock.systemDefaultZone()`) rather than calling
`LocalDate.now()`. Both "which week do we open on" and "which row draws in accent as today" are
clock-dependent, and a test that can only pass on the right day of the week is not a test. This is
the same seam `SessionManager.uuidGenerator` and `RecipeTimerController.timeSource` already
establish.

The week's first day is `WeekFields.of(Locale.getDefault()).firstDayOfWeek`, not a hard Monday. The
handoff's Mon–Sun is one locale's rendering of "a week"; in `en-US` a week starts on Sunday, and a
planner that disagrees with the phone's own calendar app about where the week begins is a bug in
every locale but the one it was drawn in.

## Decision 5: The household toggle selects a *serving basis*, applied at display time only

`MealPlanServingBasis` is a two-value enum — `JUST_ME`, `FAMILY` — and it does two things:

1. **Filters** which plans the projection sees. `JUST_ME` → `householdId == null`; `FAMILY` →
   `householdId != null`. This half is free: ADR-014 already put that distinction in the model.
2. **Overrides the servings** used for grocery aggregation: `JUST_ME` → 1 serving per meal,
   `FAMILY` → the household's member count. It is passed as `ShoppingListBuilder`'s
   `plannedServings` in place of `plan.preferences.servingsPerMeal`.

Point 2 is a genuine product decision with a genuine cost, and it was taken deliberately: it means
the basis **silently overrides what the wizard was told**. A user who answered "4 servings per meal"
and then reads the week on "Just me" is shown quantities for one. The mitigation is that this is
strictly a *reading* of the plan, never a write — exactly the posture ADR-013 takes toward unit
conversion, down to the wording: it is a way of reading the plan, not an edit to it.
`meal_plans.preferences` is never touched, the plan is never dirtied, and the detail screen
continues to show the plan's own numbers.

When the user has no household, `FAMILY` is disabled at 45% opacity (§4.3 of the design system) and
the basis is forced to `JUST_ME` — there is nothing to scale to and nothing to filter for.

The basis is stored in the existing device-local `UserPreferencesRepository`
(`chefai_user_prefs` DataStore), alongside `measurementSystem`, and for the same three reasons that
preference gives: it describes how *this device* presents things, it needs no schema migration or
backend agreement, and it is readable by any screen without threading it through a nav argument.
That last one is load-bearing — the shopping list is reached by a route that carries only a plan id,
so a basis held in this screen's `ViewModel` could never reach the list it is supposed to scale.

## Decision 6: "Generate Grocery List" reuses the per-plan route, and disables itself otherwise

`ShoppingListScreen` is routed by `mealPlanId` and `ShoppingListBuilder.build` takes a single
`plannedServings`. When the visible week resolves to exactly one plan — the overwhelmingly common
case — the button navigates to that plan's existing shopping list and **no new path is built**.

When the week covers zero plans, or two or more, the button is disabled. The rejected alternative
was to route to the "dominant" plan (the one contributing the most meals), which is worse than
disabling: it produces a plausible-looking list that silently shops for only part of the week, and
a wrong quantity on a shopping list is discovered in the shop.

The summary line above it (`"4 meals planned · 24 ingredients"`) is computed for the **whole** week
regardless, by running `ShoppingListBuilder` over every meal the projection found. The builder is
pure and the ingredient rows come from one Room query, so this is cheap; and because Decision 5
makes servings a function of the basis rather than of the plan, a multi-plan week aggregates without
contradiction. The count is therefore honest even when the button next to it is not available —
which is what tells the user *why* it is not.

A genuinely week-scoped shopping list (a route carrying a date range) is the eventual answer. It was
not built here because it would add a second entry point into `ui/shoppinglist/`, which was under
concurrent edit.

---

## Consequences

**Good**

- The screen answers the question the feature exists for. "What am I eating Thursday?" is now one
  glance, not a list of plans to open.
- No schema change, no migration, no sync-payload field, no backend agreement. The entire week view
  is a projection over data that already exists.
- The projection, the overlap resolution and the grocery aggregation are all pure functions in
  `mealplans/domain/`, unit-tested without Robolectric or an emulator.
- The conflict rule matches ADR-006's, and the display-time-only posture matches ADR-013's. Nothing
  here invents a new principle.

**Bad, and known**

- **Mid-week plan creation straddles two weeks** (Decision 2). This is the one to fix, and
  `MealPlan.startDate` is how: a Room v10→v11 migration, a `SyncMealPlanDto` field, a start-date
  control in the wizard's Basics step, and `planDateFor` falling back to `createdAt` for rows
  written before the migration. `MealPlanWeekTest` pins the current behaviour with a test named
  "a plan created mid-week straddles two weeks", so the change announces itself. Tracked as a
  follow-up.
- **The serving basis overrides the plan's own `servingsPerMeal`** (Decision 5) without saying so in
  the UI. The design has no affordance for explaining it; a future revision should.
- **The basis does not yet reach the shopping list.** It is persisted where that screen can read it,
  but wiring `ShoppingListViewModel` to prefer it over `plan.preferences.servingsPerMeal` is a
  separate change in a file under concurrent edit. Until it lands, "family-scaled quantities" stops
  at this screen's summary line.
- **"+ Add meal" does not open a picker.** There is no `MealPlanRepository` write that assigns a
  recipe to a (day, slot), and inventing one — including deciding which plan an unclaimed date would
  write into — is a feature, not a restyle. For now the link routes to the meal-plan wizard, which
  also preserves the create-plan entry point that the redesigned header otherwise removes.
- **Deleting a meal plan has no UI any more.** The delete button lived on `MealPlanCard`, which the
  week view replaces; screen 06 draws no equivalent, and the plan detail screen has none either.
  `MealPlanRepository.deleteMealPlan` is untouched and still covered by its own repository tests, so
  re-wiring it is a call site, not a feature — most naturally an overflow action on plan detail.
- **Two plans covering one day means one of them is invisible on this screen.** With the plan list
  replaced, the losing plan is reachable only through the days it does win. This is acceptable while
  plans are mostly sequential and would not be if plan overlap became normal.
- **The plan detail screen loses its entry point.** Tapping a planned meal opens that meal's recipe
  (through `MEAL_PLAN_RECIPE_DETAIL`, carrying `dayId`/`slot` so the cooked toggle still works),
  which is both the more useful destination and the one consistent with the detail screen's own row
  behaviour. But it means `MEAL_PLAN_DETAIL` is now reachable *only* immediately after the wizard
  creates a plan: leave it, and you cannot get back. Screen 06 as drawn has no affordance for it —
  no plan name, no "Plan →" link — so rather than invent one, this is recorded as a gap for the next
  revision of the design. The nearest on-system fix is Home's pattern: a section label with a
  trailing accent link, which screen 06 already has the label half of ("GROCERY LIST").
