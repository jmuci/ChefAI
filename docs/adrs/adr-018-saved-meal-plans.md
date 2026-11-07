# ADR 018 – Saved Meal Plans

**Date:** November 2026
**Status:** Accepted
**Author:** Claude (Opus 5.5), on behalf of Jose Mucientes
**Related:** [ADR-006](adr-006-sync-protocol.md), [ADR-012](adr-012-recipe-image-upload.md),
[ADR-015](adr-015-meal-plans-week-view.md), [ADR-017](adr-017-cook-history-sync.md),
Android [#322](https://github.com/jmuci/ChefAI/pull/322)–[#326](https://github.com/jmuci/ChefAI/pull/326),
backend [Chef.ai_Backend#118](https://github.com/jmuci/Chef.ai_Backend/pull/118)

---

## Context

Every meal plan is dated: it starts on a day and fills the week view from there. Users who find a
week that works want to keep it as a named menu, give it a photo, and run it again on some later
week. That is not a schedule. Repeating it should not resurrect last month's cooked marks or
reshuffle the recipes.

The entry point is the wizard's Preferences step. A toggle under "Where should recipes come from"
expands into a required title (≤ 60 chars) and an optional photo. The Plans view gains
**Scheduled / Saved** tabs, shown only when at least one saved plan exists, and a saved card offers
**Repeat** (pick a start date → a new dated plan) and **Delete**.

## Decision 1: A saved plan is a template snapshot, not a flag on the plan

Saving writes its own `saved_meal_plans` row: title, preferences, and the recipe lineup copied from
the freshly generated plan, minus cooked marks. The dated plan it came from is created as before,
named after the title, and stays independent.

Alternative rejected: *`isSaved`/`title`/`photo` on `meal_plans`.* It means less schema, but the
saved menu would be the dated plan itself. Deleting that plan would delete the menu. Swapping or
dragging a meal, or a server regeneration on the next pull, would silently rewrite it. Repeating
it would mean cloning a plan that also carries history (cooked marks, shopping-list checks).

Consequence: **the template does not follow later edits** to its source plan. The snapshot is taken
once, right after generation and the "+ Add meal" recipe assignment. If generation filled no days
(only possible with an empty collection), no template is written. The dated plan still stands, and
the user is told after navigating (`savedPlanFailed` on the wizard's navigation events).

## Decision 2: The lineup is a JSON column with no foreign keys

`saved_meal_plans` (Room v15→v16, `MIGRATION_15_16`) stores the lineup as `daysJson`, a list of
`{dayIndex, lunchRecipeId, dinnerRecipeId}`, beside `preferencesJson`. `sourceMealPlanId` is
informational only. Nothing references `recipes` or `meal_plans`.

- The lineup is replaced whole, never edited slot by slot, so a child table buys nothing.
- No FK, for the same reason as ADR-017: deleting a recipe or the source plan must not cascade into
  the template. **Repeat** (`RepeatSavedMealPlanUseCase`) drops slots whose recipe is soft-deleted
  locally (`RecipesRepository.getSoftDeletedRecipeIds`), but keeps unknown ids, which are
  recipes not yet on this device, for `getOrFetchRecipe` to resolve.
- It syncs as one atomic document.

Repeat never calls the generator. It creates a DRAFT plan and writes the copied days with fresh
day ids through `saveLocallyGeneratedDays`, which moves the plan to READY. If that write fails, the
new plan is deleted, so no empty draft is left behind.

## Decision 3: The photo is device-local for now

The picked photo (`PickVisualMedia`) is copied at pick time into `filesDir/saved_plan_images/<uuid>`
by `SavedPlanImageStore`. It shares its copy/downsample/atomic-write code with `RecipeImageStore`
(`core/data/local/PickedImageStorage.kt`). Its path, `heroImagePath`, is never synced, and a pull
keeps the local value. A saved plan without a photo, or on another device, shows the
first recipe in its lineup that has an image. The files are wiped on an account switch, and moved
when a push re-keys the row.

Uploading the photo through the ADR-012 blob pipeline is deferred.

## Decision 4: Last-writer-wins documents on push/pull

`savedMealPlans` rides on `POST /sync/push` and `GET /sync/pull`, with an empty default in every
direction, so older clients and servers are unaffected.

- **Conflicts.** Last-writer-wins on `updatedAt` against the server's `server_updated_at`. A
  tombstone applies only when strictly newer. A conflict leaves the row `PENDING` for the next pull
  to overwrite, as for meal plans.
- **Errors.** `SAVED_PLAN_ID_TAKEN` re-keys a live row with a new UUIDv7 (dropping a tombstone).
  Every other reason (`INVALID_SAVED_PLAN_TITLE`, `INVALID_SAVED_PLAN_DAYS`,
  `INVALID_SOURCE_MEAL_PLAN`, or an unknown one) parks the row `CANCELLED`.
- **Pull.** It is unpaginated and does not move the cursor. The server filters on
  `server_updated_at`, so a device with a skewed clock cannot hide a row from other devices. Pulled
  rows are skipped when no user is authenticated (the `userId` FK needs one). An anonymous →
  account upgrade reassigns saved plans and re-marks them `PENDING`.
- **Per user.** Saved plans are never household-shared (ADR-014 does not apply).

## Consequences

- **Two lists on one tab.** The Scheduled tab is exactly the old plans list. When nothing is saved
  the tabs disappear, so users who never save see no change.
- **No edit or rename yet.** A saved plan can only be repeated or deleted. Follow-ups: rename, update
  from a plan, a saved-plan detail screen, photo upload, and household-shared templates.
- **Deleting a saved plan** is a soft delete that syncs. The local photo file goes at once.
