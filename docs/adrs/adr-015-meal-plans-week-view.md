# ADR 015 – The Meal Plans Week View

**Date:** September 2026 (rewritten November 2026 to fold in four amendments)
**Status:** Accepted
**Author:** Claude (Opus 5), on behalf of Jose Mucientes
**Related:** [ADR-005](adr-0005-feature-based-package-structure.md),
[ADR-006](adr-006-sync-protocol.md), [ADR-013](adr-013-measurement-unit-conversion.md),
[ADR-014](adr-014-households.md), [ADR-018](adr-018-saved-meal-plans.md)

> This ADR was rewritten in November 2026. The original September version, and the four amendments
> that overrode parts of it, are in git history; the **History** section at the end summarises what
> changed and why. Decision numbers are kept where code comments cite them.

---

## Context

The Modernist redesign replaced the Meal Plans screen wholesale. The old screen was a `LazyColumn`
of plan cards: **a list of plans**, each an opaque object you opened to see inside. Screen 06 of the
handoff is **a week**: seven dated rows, each holding planned meals or a "+ Add meal" link, with a
week navigator above and a grocery summary below.

Those answer different questions. The old screen answered *"what plans do I have?"*; the new one
answers *"what am I eating on Thursday?"* — the one a meal planner exists for. The data model was
not built to answer it: `MealPlan` had no date, only `createdAt` (bookkeeping), `planLengthDays`
(3, 5 or 7) and `MealPlanDay.dayIndex` (a bare offset within the plan).

---

## Decision 1: A week is a date window projected over plans, not a plan

The week view does not pick a plan and render it. It picks seven consecutive dates and asks every
live plan which of its days land on them:

```
MealPlanWeekProjector.project(
    plans: List<MealPlan>,      // ARCHIVED plans are dropped inside
    weekStart: LocalDate,
    recipes: Map<UUID, RecipePreview>,
    zone: ZoneId,               // from the injected clock
): MealPlanWeek                 // exactly 7 WeekDay rows, some empty
```

It lives in `mealplans/domain/week/MealPlanWeek.kt` — pure Kotlin, no repository — like
`ShoppingListBuilder` and `MealPlanScheduler`, because the projection rules are the part worth
testing. A date-first screen renders a coherent week whether it is covered by one plan, several, or
none; the days no plan claims render as "+ Add meal".

## Decision 2: Day 0 is the plan's start date, falling back to `createdAt`

The wizard's Basics step asks "Which day does the meal plan start?". `MealPlan.startDate`
(`LocalDate?`) is stored as `meal_plans.startEpochDay` (DB v13, `MIGRATION_12_13`, nullable, no
backfill) and syncs as an optional ISO date `SyncMealPlanDto.startDate` — a calendar date, not an
instant, so devices in different zones agree on the day. A pull that omits it keeps the local value
(the same carry-forward as cooked marks).

Every date derivation goes through `MealPlan.dateOf`, which falls back to `createdAt`'s local date
when `startDate` is null, so plans created before v13 do not move.

**Default start** (`defaultMealPlanStartDate`): the day after the last date that already has a
meal, never before today; gaps before that meal are skipped, since a plan is a run of consecutive
days. When the week view's "+" opens the wizard, the route carries that week's first day
(`weekStart` nav arg) and only meals inside that week count; an empty week defaults to its first
day, or today if the week is under way. From the plans list, every future meal counts.

## Decision 3: Overlapping plans all show

Nothing prevents two live plans from covering the same date — a household member's shared plan can
overlap a personal one, and the wizard allows it. The projector does **not** pick a winner: every
live plan's meal lands on its date, ordered by slot, then by plan `createdAt` (oldest first, `uuid`
as tiebreak). `updatedAt` is deliberately not used, because a regeneration or slot edit bumps it and
would reshuffle rows for no visible reason.

When two plans fill the same slot, both rows render, each tagged with its plan's name
(`WeekDay.sharedSlots`, `PlannedWeekMeal.planName`). Each row carries its own `dayId`/`slot`, so
the cooked toggle reached from it acts on that plan's slot only. `MealType.DINNER_AND_LUNCH` plans
stack both slots under one date.

The wizard warns, non-blockingly, when a new plan's dates overlap an existing one
(`MealPlanOverlap`, a snackbar after creation).

## Decision 4: The selected week lives in the ViewModel, over an injected `Clock`

`MealPlansViewModel` holds the week start in a `MutableStateFlow<LocalDate>`; prev/next shift it by
±7 days. It is **not** in `SavedStateHandle`: surviving rotation is wanted, surviving process death
is not — a user returning to a cold app wants this week.

`java.time.Clock` is injected rather than calling `LocalDate.now()`, so "which week opens" and
"which row is today" are testable on any day. The week's first day comes from
`WeekFields.of(Locale.getDefault()).firstDayOfWeek` (`weekStartFor`), not a hard Monday.

## Decision 5: Week | Plans, and no serving-basis toggle

The header toggle is **Week | Plans** (`MealPlansViewMode`, in-memory, opens on Week). *Plans* is a
list of hero cards (`MealPlanHeroCard`) built by the pure `MealPlanSummaryBuilder` from every
non-archived plan, newest first; a card opens the plan detail screen and offers delete. A plan with
no user-given title gets one generated from its most common recipe tag ("Italian week"). Once a
saved plan exists, *Plans* gains **Scheduled / Saved** tabs ([ADR-018](adr-018-saved-meal-plans.md)).

Nothing filters by `householdId`: a plan shared with a household is simply one of the user's plans.
Grocery servings are not a user choice — a week containing a shared plan shops for the household's
member count, otherwise for one.

## Decision 6: "Generate Grocery List" reuses the per-plan route, and disables itself otherwise

`ShoppingListScreen` is routed by `mealPlanId`. When the visible week resolves to exactly one plan
(`MealPlanWeek.singlePlanId`), the button opens that plan's existing list. For zero or several
plans it is disabled. Routing to a "dominant" plan was rejected: it produces a plausible-looking
list that silently shops for part of the week, and that is discovered in the shop.

The summary line above it is computed over **every** meal shown, by running `ShoppingListBuilder`
across them, and says when they come from several plans ("8 meals planned across 2 plans · 45
ingredients") — which is what tells the user why the button is unavailable.

## Decision 7: "+ Add meal" seeds the wizard instead of writing a meal

An empty day's "+ Add meal" opens `AddMealSheet`, a fixed ~80%-height bottom sheet (past M3's 50%
initial cap, so results fit above the keyboard). It searches the user's **saved** (bookmarked)
recipes in place — all of them before anything is typed, filtered by title as the user types.

A pick does not add a lone meal. It opens the meal-plan wizard with the recipe (`recipeUuid`), its
slot (`mealPlanSlot`) and the tapped date (`startDate`) as nav args; the wizard pre-selects that
date as the start (still editable) and places the recipe on day 0, overwriting what generation put
there. A lunch pick defaults the plan to "Dinners + Lunches"; switching back to dinners only moves
the recipe to day 0's dinner.

"Add a New Recipe" goes to the importer instead. `AddMealRequest` carries the date and slot through
the importer and editor, and `finishAddMeal` consumes it, pops back to the tab and opens the same
wizard. The request lives in the Meal Plans entry's **`NavBackStackEntry.savedStateHandle`**, read
and written from the nav graph — not `MealPlansViewModel`'s Hilt-injected handle, which is a
different object. Writing into the ViewModel's handle silently delivered nothing.

Trade-off: "+ Add meal" cannot add one meal to a plan that already covers the day. The new plan
overlaps it, and the overlap notice says so.

## Decision 8: Meals reorder by drag, within one plan

A planned meal can be dragged onto another meal of the **same** plan (`WeekMealDragState`,
`MealPlansViewModel.onMoveMeal`, `MealPlanRepository.moveMeal`). The pure `MealPlanReorder.move`
keeps the plan's occupied (day, slot) positions fixed and moves only which recipe sits where;
cooked meals are pinned, so `recipe_cook_events` rows stay valid. A drop onto another plan's meal
or an empty day is ignored, since it has no defined meaning.

---

## Consequences

**Good**

- The screen answers the question the feature exists for, in one glance.
- The projection, overlap ordering, reordering and grocery aggregation are pure functions in
  `mealplans/domain/`, unit-tested without Robolectric.
- No planned data is hidden: every live plan's meals and cooked marks show.

**Bad, and known**

- Shopping lists stay per plan. A week built from several plans has no single list; a
  date-range-scoped list is the eventual answer.
- Overlap is allowed and only warned about, so a user can build a crowded week by accident.
- A pull that omits `startDate` keeps the local value, so a backend that drops the field cannot
  move a plan — but it cannot deliver one set on another device either.

---

## History

- **Sep 2026 (original):** day 0 was the plan's `createdAt` date with no `startDate` column; one
  meal per (date, slot), highest `updatedAt` winning; a "Just me / Family" toggle filtered plans by
  `householdId` and overrode grocery servings; "+ Add meal" routed straight to the wizard.
- **Amendment — toggle removed:** filtering hid a user's own shared plans, and overriding servings
  silently contradicted what the wizard was told. Replaced by Week | Plans (Decision 5).
- **Amendment — overlap shows all:** last-writer-wins hid the older plan's meal and its cooked mark
  while the summary still counted only visible meals. The ADR-006 alignment argument did not hold:
  sync resolves two versions of one entity; overlapping plans are two live entities (Decision 3).
- **Amendment — "+ Add meal":** first a picker screen that wrote into a covering plan or created
  one-day "Added meals" plans (`AddRecipeToDateUseCase`); then an in-place sheet; finally the
  current wizard seeding (Decision 7), which removed the one-day plans and `planLengthDays = 1`.
- **Amendment — start date:** a plan created mid-week straddled two weeks; `startDate` (DB v13)
  replaced the `createdAt` anchor, which survives only as the fallback (Decision 2).
