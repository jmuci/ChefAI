# Recipe Delete — Implementation Plan

**Branch**: `claude/recipe-delete-feature-02fac1`
**Goal**: Users can delete a recipe they created or imported, via a button on the recipe details screen.
**Author**: plan drafted 2026-08-12

---

## Context: what already exists

Most of the delete machinery is already in the codebase. Read this before writing code.

| Piece | Location | Status |
|---|---|---|
| `RecipesRepository.softDeleteRecipe(UUID)` | `recipes/domain/repository/RecipesRepository.kt:28` | Exists |
| `DefaultRecipeRepository.softDeleteRecipe` — sets `deletedAt`, requests sync | `recipes/data/repository/DefaultRecipeRepository.kt:267` | Exists |
| `RecipeDao.softDelete(uuid, deletedAt)` — sets `deletedAt`, `syncState='DELETED'` | `core/data/local/room/dao/RecipeDao.kt:108` | Exists |
| `DeleteConfirmationDialog` composable | `recipes/ui/editor/components/EditorDialogs.kt:37` | Exists |
| Strings: `delete_recipe_button`, `delete_recipe_confirmation_title`, `delete_recipe_confirmation_message` | `res/values/strings.xml:130-132` | Exists |
| Delete flow in the **editor** screen (action → reducer → VM → effect → pop) | `recipes/ui/editor/*` | Exists |
| Sync push of `DELETED` rows (`getAllDirty()`), tombstone kept after accept | `core/data/sync/SyncOrchestrator.kt:101,193` | Exists |
| Sync pull applies remote deletes | `SyncOrchestrator.applyPulledRecipe` (`:315`) | Exists |

### The gap

**No `RecipeDao` read query filters `deletedAt IS NULL`.** Verified by
`grep -rn "deletedAt IS NULL" app/src/main/java/` — only `MealPlanDao` and `BookmarkedRecipeDao`
do it. Consequence: the editor's existing delete marks the row deleted, pops back, and the recipe is
**still visible** in the Recipes list and details screen. Fixing this is the core of the work; the
details-screen button is the smaller half.

### Design decisions (already made — don't re-litigate)

- **Soft delete, not hard delete.** Required by the sync protocol ([ADR-006](../adrs/adr-006-sync-protocol.md)):
  the tombstone row is what gets pushed so the backend learns about the delete. `deleteRecipe()`
  (hard) stays on the interface but is not what the UI calls.
- **Confirm dialog, not undo-snackbar.** Matches the editor flow and the existing
  "This action cannot be undone" copy. Undo is out of scope.
- **Details screen only.** No swipe-to-delete on the list, no multi-select. Out of scope.
- **No delete from the meal-plan recipe detail route.** Gate it the same way `onEditClick` is
  gated — pass the callback only from `RECIPE_DETAILS`, not `MEAL_PLAN_RECIPE_DETAIL`.
- **`DeleteConfirmationDialog` moves to a shared location.** It currently lives in
  `recipes/ui/editor/components/EditorDialogs.kt` and is used only by the editor. Since the details
  screen now needs it too, it moves to `recipes/ui/components/DeleteConfirmationDialog.kt` (new file,
  sibling to `editor/` and `urlimport/` within the `recipes` feature package — not `core/ui/`, per
  [ADR-005](../adrs/adr-0005-feature-based-package-structure.md): stay in the feature package until a
  second *feature* needs it, not just a second screen). `UnsavedChangesDialog` stays put — it's
  editor-only. This is T2 below, and both editor and details screens update their imports.
- **Filed as a follow-up, not fixed here**: [#128](https://github.com/jmuci/ChefAI/issues/128) —
  `Recipe.toRoomEntity()` hardcodes `deletedAt = null`, so `updateRecipe()` on a tombstone would
  resurrect it. Unreachable through the UI after T1 (a deleted recipe can no longer be opened for
  editing), but worth tracking.

---

## Tasks

Each task is independently reviewable. T1–T3 are the data layer, T4–T6 the UI, T7–T9 tests and
cleanup. Do them in order — T4 depends on T1 for correct behavior.

---

### T1 — Filter soft-deleted recipes out of UI-facing DAO reads

**File**: `app/src/main/java/com/tenmilelabs/chefai/core/data/local/room/dao/RecipeDao.kt`

Add a `deletedAt IS NULL` predicate to the queries that feed the UI:

- `observeAllRecipesForUser` (`:22`)
- `getRecipeWithDetails` (`:49`)
- `observeRecipeWithDetails` (`:53`)
- `observeRecipesWithDetails` (`:57`)
- `observeRecipesWithDetailsForUser` (`:61`)
- `observePublicRecipesWithDetails` (`:65`)
- `getRecipeWithTags` (`:69`), `observeRecipesWithTags` (`:73`)
- `getRecipeWithLabels` (`:77`), `observeRecipesWithLabels` (`:81`)
- `countRecipesForUser` (`:119`)

Example: `@Query("SELECT * FROM recipes WHERE uuid = :uuid AND deletedAt IS NULL")`

**Do NOT filter these — filtering them breaks sync:**

| Query | Why it must still see tombstones |
|---|---|
| `getRecipeById` (`:42`) | `SyncOrchestrator.applyPulledRecipe:312` and `DefaultHomeRecipeSidecarRepository:54` use it as an existence check. Filtering makes a deleted recipe look "new" and it gets re-inserted. |
| `getDirty` / `getAllDirty` (`:99,:102`) | `getAllDirty` is exactly how the delete reaches the backend. |
| `getRecipeIdsForUser` (`:116`) | Account upgrade must reassign tombstones too. |
| `reassignCreatorAndMarkPending`, `updateSyncState`, `softDelete`, all `DELETE FROM` | Writes. |

`observeIngredientsForRecipe` (`:25`) needs no change — it is already scoped to one recipe id, and
the caller (`getRecipeStream`) returns null once the recipe row is filtered out.

**Notes**
- Query-only change → **no Room schema version bump and no migration.** The schema hash covers
  entities, not queries.
- This automatically fixes downstream surfaces: bookmarks and meal-plan days resolve previews
  through `getRecipePreviewsByIds` → `getRecipesPreviewStream()`, so a deleted recipe drops out of
  the Collections tab and out of meal plan days with no extra work.

**Done when**: file compiles, `./gradlew :app:assembleDebug` passes.

---

### T2 — Mirror the filter in `FakeRecipeDao`

**File**: `app/src/test/java/com/tenmilelabs/chefai/core/data/local/room/dao/FakeRecipeDao.kt`

The JVM fake backs most repository/VM tests. Apply the same `deletedAt == null` filter to exactly the
same set of read methods listed in T1, and leave the sync-facing ones (`getRecipeById`, `getDirty`,
`getAllDirty`, `getRecipeIdsForUser`) unfiltered. If the fake and the real DAO disagree, tests will
pass while the app is broken.

**Done when**: `./gradlew :app:testDebugUnitTest` still passes (T3 adds the new coverage).

---

### T3 — DAO tests for the filter

**Files**:
- `app/src/androidTest/java/com/tenmilelabs/chefai/data/source/local/RecipeDaoTest.kt` (in-memory Room)
- optionally a small JVM test alongside `FakeRecipeDraftDaoTest.kt` for the fake

Given/When/Then cases:
1. `softDelete` on a recipe → `observeRecipesWithDetails()` no longer emits it.
2. `softDelete` → `observeRecipeWithDetails(uuid)` emits `null`.
3. `softDelete` → `getRecipeById(uuid)` **still returns the row** with `deletedAt != null` and
   `syncState == DELETED` (guards the sync path against a future over-eager filter).
4. `softDelete` → `getAllDirty()` **still contains** the recipe (guards push).
5. `countRecipesForUser` drops by one after a soft delete.

**Done when**: new tests pass. The androidTest suite needs a device/emulator; if none is available,
say so and make sure cases 3–5 are covered by JVM tests against `FakeRecipeDao` instead.

---

### T4 — Delete action + one-shot navigation effect in `RecipeDetailsViewModel`

**File**: `app/src/main/java/com/tenmilelabs/chefai/recipes/ui/details/RecipeDetailsViewModel.kt`

Add to `RecipesDetailsUiState`:
```kotlin
val showDeleteConfirmation: Boolean = false,
val isDeleting: Boolean = false,
```

Add a one-shot effect channel (mirror `RecipeEditorViewModel`'s `_effects` — same pattern, don't
invent a new one):
```kotlin
sealed interface RecipeDetailsEffect {
    data object RecipeDeleted : RecipeDetailsEffect
}
```
exposed as `val effects: Flow<RecipeDetailsEffect>` from a `Channel(Channel.BUFFERED)` via
`receiveAsFlow()`.

Add three functions:
- `fun onDeleteClick()` → sets `showDeleteConfirmation = true`
- `fun dismissDeleteDialog()` → sets it false
- `fun confirmDelete()` → `viewModelScope.launch { ... }`: set `isDeleting = true`,
  `showDeleteConfirmation = false`, call `recipesRepository.softDeleteRecipe(recipeUuid)`, send
  `RecipeDeleted`. On failure, log with Timber, clear `isDeleting`, set
  `_userMessage` to a new `delete_recipe_error` string.

**The race to handle — don't skip this.** After T1, `getRecipeStream(recipeUuid)` emits `null` the
moment the delete lands, which the existing `_recipeAsync` mapping turns into
`Async.Error(R.string.loading_recipe_details_error)`. Without a guard the user sees a "couldn't load
recipe" snackbar and the not-found empty state flash before the pop. Fix: track a
`_isDeleting`/`_isDeleted` `MutableStateFlow` and, in the `combine`, suppress the error branch (keep
the last-known recipe rendered, or show a plain loading state) while it is true. Verify this
visually, not just in tests.

`recipesRepository` is currently an unnamed constructor param (not a `private val`) — promote it to
`private val` so `confirmDelete` can use it.

**Done when**: compiles; T8 covers it with tests.

---

### T5 — Delete button + confirmation dialog on the details screen

**Files**:
- `app/src/main/java/com/tenmilelabs/chefai/recipes/ui/components/DeleteConfirmationDialog.kt` (new)
- `app/src/main/java/com/tenmilelabs/chefai/recipes/ui/editor/components/EditorDialogs.kt` (remove `DeleteConfirmationDialog`, keep `UnsavedChangesDialog`)
- `app/src/main/java/com/tenmilelabs/chefai/recipes/ui/editor/RecipeEditorScreen.kt` (update import)
- `app/src/main/java/com/tenmilelabs/chefai/recipes/ui/details/RecipeDetailsScreen.kt`

First, relocate `DeleteConfirmationDialog` per the design decision above: cut it out of
`EditorDialogs.kt` into a new `recipes/ui/components/DeleteConfirmationDialog.kt`, fix the one import
in `RecipeEditorScreen.kt`. Verify with
`grep -rn "editor.components.DeleteConfirmationDialog" app/src/main/java/` — should return nothing
after the move.

`RecipeDetailsContent` stays stateless. Add parameters:
```kotlin
onDeleteClick: (() -> Unit)? = null,
showDeleteConfirmation: Boolean = false,
onConfirmDelete: () -> Unit = {},
onDismissDeleteDialog: () -> Unit = {},
isDeleting: Boolean = false,
```

Placement: an `IconButton` with `Icons.Default.Delete` (same icon the editor's delete button already
uses — `material-icons-extended` isn't an actual dependency of `:app`, only declared unused in the
version catalog, so `DeleteOutline` isn't available), tinted `MaterialTheme.colorScheme.error`, in the
existing title `Row` **next to the bookmark button** (`:141`). Shown only when `onDeleteClick !=
null`. Disabled while `isDeleting`, showing a small `CircularProgressIndicator` in its place (mirror
`EditorActionBar`'s delete button). Needs a `contentDescription` — reuse `R.string.delete_recipe_button`.

Render `DeleteConfirmationDialog(onConfirm = onConfirmDelete, onDismiss = onDismissDeleteDialog)`
when `showDeleteConfirmation` is true. Import it from
`com.tenmilelabs.chefai.recipes.ui.editor.components` — same feature package, zero-risk reuse. Do
**not** move the file; if the cross-package import bothers a reviewer, relocating it to a shared
`recipes/ui/components/` is a separate follow-up.

In the stateful `RecipeDetailsScreen`, thread the new params from `uiState` and the VM, and add an
`onNavigateBack: (() -> Unit)? = null` param (see T6) — **nullable**, mirroring `onEditClick`. Its
presence is what gates the delete button (`onDeleteClick = onNavigateBack?.let { { viewModel.onDeleteClick() } }`),
the same way `onEditClick`'s presence gates the edit FAB. `MEAL_PLAN_RECIPE_DETAIL` doesn't pass it,
so it stays `null` there and the delete button never renders — no separate boolean flag needed.

Add a `@Preview` with the dialog visible. Existing previews are light-only; follow the file's
existing convention rather than adding dark variants here.

**Done when**: previews render; button appears in the details screen.

---

### T6 — Nav wiring: pop back and confirm to the user

**File**: `app/src/main/java/com/tenmilelabs/chefai/core/ui/navigation/ChefAINavGraph.kt`

At the `AppDestinations.RECIPE_DETAILS` composable (`:184`), pass
`onNavigateBack = { navController.popBackStack() }`. Leave the `MEAL_PLAN_RECIPE_DETAIL` composable
(`:111`) alone — no delete there, same as it has no edit.

In `RecipeDetailsScreen`, collect the effect with a plain `LaunchedEffect(Unit) { viewModel.effects.collect { ... } }`
— match `RecipeEditorScreen:81` exactly, no `repeatOnLifecycle` (the existing editor screen doesn't
use it either). On `RecipeDeleted`: `snackbarHostState.showSnackbar(recipeDeletedText, duration =
SnackbarDuration.Short)` **then** `onNavigateBack?.invoke()` — sequential, matching how
`RecipeEditorScreen:84-91` already sequences `ShowError` before `NavigateBack` in the same collector.
`showSnackbar` suspends until the snackbar is dismissed or times out, so the pop happens a couple
seconds after the delete completes, with the screen showing its neutral "deleting" state in the
meantime (see T4's `_isDeleted` guard). Don't try to fire-and-forget the snackbar to pop instantly —
the snackbar's coroutine would be a child of this screen's own `LaunchedEffect`, which gets cancelled
the moment the screen leaves composition, so it would never actually show.

**New strings** (`res/values/strings.xml`): `recipe_deleted`, `delete_recipe_error`
("Couldn't delete recipe").

**Done when**: manual run — open a recipe → Delete → confirm → back on the list, recipe gone,
snackbar shown.

---

### T7 — Repository-level test: deleted recipe hidden but still pushable

**File**: `app/src/test/java/com/tenmilelabs/chefai/recipes/data/repository/DefaultRecipeRepositoryTest.kt`

1. `softDeleteRecipe(id)` → `getRecipesPreviewStream()` (Turbine) no longer contains the recipe.
2. `softDeleteRecipe(id)` → `getRecipeStream(id)` emits `null`.
3. `softDeleteRecipe(id)` → `syncManager.requestMutationSync()` was called (the existing fake/mock
   in that test file already tracks this — check before adding a new one).

---

### T8 — ViewModel tests for delete

**File**: `app/src/test/java/com/tenmilelabs/chefai/recipes/ui/details/RecipeDetailsViewModelTest.kt`

Extend `FakeRecipesRepository` (`recipes/data/repository/FakeRecipesRepository.kt:149`) if needed so
`softDeleteRecipe` records the call and makes the recipe flow emit `null`.

Cases:
1. `onDeleteClick()` → `showDeleteConfirmation == true`.
2. `dismissDeleteDialog()` → false, repository not called.
3. `confirmDelete()` → repository `softDeleteRecipe(recipeUuid)` called once, and
   `RecipeDetailsEffect.RecipeDeleted` emitted (Turbine on `effects`).
4. `confirmDelete()` when the repository throws → no effect emitted, `isDeleting == false`,
   `userMessage == R.string.delete_recipe_error`.
5. **Regression for the T4 race**: after `confirmDelete()` and the recipe flow emitting `null`, the
   state does **not** carry `R.string.loading_recipe_details_error`.

---

### T10 — Compose UI tests for the delete button and dialog

**File**: `app/src/androidTest/java/com/tenmilelabs/chefai/recipes/ui/details/RecipeDetailsScreenTest.kt` (new)

The DAO/repository/ViewModel tests (T3, T7, T8) cover the data and state layers, but nothing exercises
`RecipeDetailsContent`'s actual conditional rendering and click wiring — whether the delete button
really is absent when `onDeleteClick == null`, whether it's really disabled while `isDeleting`,
whether the dialog's buttons really call the right callbacks. Since `RecipeDetailsContent` is
stateless (plain parameters, no ViewModel/Hilt), test it directly with `createComposeRule()` — no
`createAndroidComposeRule<MainActivity>()`, no `@HiltAndroidTest` needed (that heavier pattern is for
`AuthE2ETest`-style full-navigation tests, not a single composable). Reuse the `RecipeData.recipe`
preview fixture already used by this file's own `@Preview`s.

Added `Modifier.testTag("DeleteRecipeButton")` to the delete `IconButton` in
`RecipeDetailsScreen.kt`, matching the plain-string `testTag` convention already used in
`LoginScreen.kt`/`RegisterScreen.kt`/`AuthE2ETest.kt`. Necessary because the button's `Icon` (which
carries the only other identifying signal, its `contentDescription`) is swapped for a
`CircularProgressIndicator` while `isDeleting`, so content-description lookup can't find the button in
that state — the tag is the only stable way to locate it regardless of which child is showing.

Cases:
1. `onDeleteClick == null` → button doesn't exist.
2. `onDeleteClick` provided → button displayed, click invokes it.
3. `isDeleting == true` → button disabled (`assertIsNotEnabled()`), click does **not** invoke the
   callback (Compose's `clickable`/`IconButton` `enabled` gate blocks the click at the gesture level,
   verified rather than assumed).
4. `showDeleteConfirmation == false` → dialog title text doesn't exist.
5. `showDeleteConfirmation == true` → dialog title and message both displayed.
6. Tapping the dialog's "Delete" → `onConfirmDelete` fires, `onDismissDeleteDialog` doesn't.
7. Tapping the dialog's "Cancel" → `onDismissDeleteDialog` fires, `onConfirmDelete` doesn't.

Assertion library: plain JUnit (`junit.framework.TestCase.assertTrue`/`assertFalse`), matching
`RecipeDaoTest.kt` — Truth is `testImplementation` only (JVM tests), not wired into
`androidTestImplementation`.

**Done when**: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tenmilelabs.chefai.recipes.ui.details.RecipeDetailsScreenTest`
passes on-device.

---

### T9 — Verify and document

1. `./gradlew :app:testDebugUnitTest` — report pass/fail counts, fix anything red.
2. `./gradlew :app:assembleDebug`.
3. Manual E2E on emulator: create a recipe → open details → Delete → confirm → gone from the list;
   reopen the app to confirm it stays gone; if signed in, confirm the delete pushes on next sync.
4. Update `.claude/session-context.md` (recent changes, status row for recipe delete).
5. Update the CLAUDE.md "Current Gaps" table if a row applies.

---

## Out of scope (call these out in the PR, don't build them)

- Undo / trash / restore.
- Swipe-to-delete or multi-select on the recipes list.
- Deleting from the meal-plan recipe detail route.
- Permission checks (can only the creator delete?) — today every local recipe is the user's.
  Worth an issue once real user wiring lands.
- **Pre-existing, do not fix here**: `Recipe.toRoomEntity()`
  (`recipes/data/mapper/RoomDomainMap.kt:148`) hardcodes `deletedAt = null`, so an `updateRecipe()`
  on a tombstone would resurrect it. Unreachable through the UI after T1 (deleted recipes can't be
  opened), but it is a real latent bug — file an issue.
- **Pre-existing**: `DefaultRecipeRepository.createRecipe` carries a `@Transaction` annotation that
  is a no-op outside a DAO class.
