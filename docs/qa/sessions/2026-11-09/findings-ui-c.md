# UI-C findings (emulator-5554) — meal plans, week view, shopping list, saved plans, offline

Account: `qa+plans1@example.test` (local backend http://10.0.2.2:8080, confirmed via logcat).
Seed: 10 recipes inserted directly into Room (run-as sqlite3, syncState PENDING, pushed fine) because driving the
recipe editor by adb was too slow; go-tos: Chicken Tikka Masala, Spaghetti Aglio e Olio, Simple Tomato Pasta; dessert:
Chocolate Brownies, Lemon Cake; servings 1 (Solo Omelette) and 12 (Brownies, Big Batch Lentil Soup); "Onion"/"Onions" both used.

Notes (not bugs): local backend DOES serve `POST /meal-plans/{id}/generate` (server-first generation happens; it ignores
go-tos, uses the public pool; that is backend#112 territory) so the on-device fallback is only exercised offline.

### PLANS-1: Wizard keeps its step after process death but resets every answer to defaults
- Severity: S3
- Status: CONFIRMED
- Steps: 1. Meal Plans > "+" . 2. Step 1: 3 days, Dinners + Lunches, 4 servings; Next. 3. Step 2: Vegan; Next. 4. Step 3: 30 min. 5. Home, `adb shell am kill com.tenmilelabs.chefai`, `am start -n com.tenmilelabs.chefai/.MainActivity` (task restored).
- Expected: either the wizard answers survive (SavedStateHandle) or the wizard restarts at step 1.
- Actual: lands on "STEP 3 OF 3" with max-prep back at "No limit"; Back shows step 2 with nothing selected and step 1 with 5 days / Dinners only / 2 servings / start date reset to the default. The user can press Create Plan believing their choices are kept and gets a plan with defaults.
- Evidence: uiautomator dumps after restore (K flags: 927,1237 "No limit" checked; step 1 shows 221,1780 "2").
- Suspected cause: `CreateMealPlanViewModel` keeps answers in a plain `MutableStateFlow` (`_uiState`, CreateMealPlanViewModel.kt:81); only nav args are in `SavedStateHandle`, while the step is a nav destination (ChefAINavGraph.kt:290/303) that the system restores. ADR-009 chose the nav-scoped VM; it does not cover process death.

### PLANS-2: Deleting a recipe that sits in a plan leaves an undeletable dead slot ("Recipe not available") that opens a "Recipe Not Found" error
- Severity: S3
- Status: CONFIRMED
- Steps: 1. Plan (Nov 15-21) contains Solo Omelette (Wed dinner). 2. Open it from the slot, trash icon > Delete (dialog says only "cannot be undone"; it doesn't mention the plan). 3. Return to plan detail / week view.
- Expected: warn that the recipe is in N meal plans, and/or clear the slot or offer replace/remove.
- Actual: slot becomes "Recipe not available" (detail and week view), still has an unchecked cooked checkbox and drag handle, still counts in "1 of 14 meals cooked" (denominator never reaches 100% unless the dead slot is ticked), no way to remove/replace it (no replace/remove action exists on a slot). Tapping it opens "Recipe Not Found! Oops! This was not supposed to happen" + snackbar "Failed to load recipe details" (no on-screen back arrow; system back needed). Shopping list correctly drops the omelette (37 -> 36 items) and keeps the 2 ticks.
- Evidence: screenshots scratchpad dl2.png, dl3.png.
- Suspected cause: plan day keeps the recipe id; soft-deleted recipe isn't resolved (Repeat of a saved plan drops such slots per ADR-018 but the live plan doesn't).

### PLANS-3: Sync indicator keeps saying "Synced" while offline until the app is restarted
- Severity: S4
- Status: CONFIRMED
- Steps: enable airplane mode with the app open on Meal Plans; create a plan and tick shopping items; observe the cloud icon.
- Expected: Offline / pending state.
- Actual: green "Synced" cloud (content-desc "Synced") through the whole offline session; only after force-stop + relaunch does it become "Offline" (slashed cloud). Data synced fine on reconnect (3 plans, no duplicates, 2 checks pushed).

### PLANS-4: Plans-list card has no progress bar (docs say it does) and plans with the default title are indistinguishable
- Severity: S4
- Status: CONFIRMED
- Actual: `docs/feature-status.md` says the segmented progress bar is "also shown on the list card"; `MealPlanHeroCard` shows only title/recipes/"Scheduled dates". Also every default plan is named "7-day meal plan", so the week view's per-row plan tag ("Lunch · 7-day meal plan") can't tell two overlapping plans apart.

### PLANS-5: "My collection only" counts/uses recipes the user *authored*, not the bookmarked "collection" the UI talks about
- Severity: S3
- Status: CONFIRMED (behaviour on device; cause in code)
- Steps: 1. Account with 10 authored recipes + 15 bookmarked public recipes (25 bookmarks). 2. Meal Plans > "+" > Next. 3. Step 2: "My collection only" is disabled with "Add at least 20 recipes to your collection...". 4. After seeding 20 authored recipes it becomes enabled (and the default).
- Expected: the Recipes-tab "collection" (bookmarks; its empty state says "bookmark the ones you like to populate your collection") drives both the threshold and the pool; the "+ Add meal" sheet also searches *saved/bookmarked* recipes.
- Actual: threshold = `getRecipeCountForUser` and pool = `getRecipesPreviewStreamForUser(userId)` = `WHERE creatorId = :creatorId` (RecipeDao.kt:97, LocalMealPlanGenerator.kt:44). Bookmarking 100 public recipes never enables it; with 20 authored recipes the plan ignores every bookmark (and go-to bookmarks of public recipes). Offline fallback also tells the user "we build plans from your collection".
- Evidence: co1.png; CreateMealPlanViewModel.kt:134-135.

### PLANS-6: Dietary restriction is silently dropped when no recipe carries a matching tag; recipes are matched on tags only
- Severity: S3 (safety-relevant for Gluten-Free/Dairy-Free/Vegan)
- Status: CONFIRMED (documented fallback, but no user warning)
- Steps: collection-only, Vegan, max prep 15, 7 days lunches+dinners, offline (on-device generator). Collection contains "Vegan Mushroom Fried Rice" (title says Vegan, tagged Quick/Healthy).
- Expected: a Vegan plan, or a visible warning that restrictions couldn't be honoured.
- Actual: plan contains Spaghetti Aglio e Olio (parmesan), Chicken Thighs Traybake, Lentil soup etc. No warning in the wizard, detail screen ("Plan preferences" lists Vegan) or toast. `satisfies()` only looks at tag/label display names, never title/ingredients, so a genuinely vegan recipe without the tag is excluded while a non-vegan one passes via the fallback.
- Suspected cause: MealPlanScheduler.kt:131-140 (`.ifEmpty { candidates }`), :168-173.
- Note: a max-prep filter that leaves nothing also falls back silently; prep filter counts `prepTimeMinutes` only (cook time ignored) while cards show prep+cook ("Chicken Thighs Traybake 60m" appears in a 15-minute plan).

### PLANS-7: "+ Add meal" on a past day starts a wizard whose start date the date picker itself rejects
- Severity: S4
- Status: CONFIRMED
- Steps: Week view on Mon Nov 9, tap "+ Add meal" on Sun Nov 8, pick a recipe. Wizard step 1 shows "Sunday, November 8, 2026". Open the date field: Nov 1-8 are greyed; typing 11/01/2026 gives "Date not allowed".
- Expected: either no "+ Add meal" on past days or an accepted past start date.
- Actual: a plan can be created in the past only via that entry point (the plan then shows in last week). Also the Repeat date dialog title "Start this plan on" sits flush against the dialog's top-left edge (cosmetic).

### PLANS-8: Large font (2.0x): day label wraps mid-word and bottom-nav label is cut
- Severity: S4
- Status: CONFIRMED
- Steps: Settings > font scale 2.0 (`adb shell settings put system font_scale 2.0`), Meal Plans week view.
- Actual: "SUN" renders as "SU" / "N" in the date gutter; bottom-nav shows "Meal" instead of "Meal Plans"; meta line "Lunch · 7-day meal plan · 87m · italian" wraps to 3 lines. Layout otherwise usable, no clipping of titles.
- Evidence: fs1.png

### PLANS-9: Recipe details "Add to meal plan" sheet cannot tell plans or days apart and gives no confirmation of what it changed
- Severity: S3
- Status: CONFIRMED (UI); overwrite-of-cooked-slot not tested
- Steps: Recipe details (Tom kha gai) > "Add to Meal Plan". "Choose a plan" lists 9 rows: "7-day meal plan" x4, "Own menud" x2, the long "Lorem ipsum..." x2, "5-day meal plan" (no dates, no counts; includes a plan far in the past/future and duplicates). Next screen "Choose a day and meal": "Day 1..Day N" with Lunch/Dinner buttons, no dates, no indication of what is currently in the slot. Tap Day 1 Dinner.
- Actual: snackbar "Added to your meal plan" (does not say which plan/day). It silently replaces whatever sat in that slot (no confirm/undo). Plans with identical default titles are indistinguishable (see PLANS-4).
- Evidence: dk3.png, at1.png, at2.png.

## Verified OK / observations (no bug)
- Wizard: state retained across Back/Next and rotation (rotation restored step 2 incl. Vegan/GF/save toggle); servings clamp 1..12; max prep/variety/batch toggles work; save toggle requires a non-blank title (whitespace-only rejected with "Give your saved plan a title"; title hard-capped at 60 chars incl. leading spaces, saved title trimmed).
- Offline on-device generator (7 days, lunches+dinners, public pool): 3 go-tos fill the earliest 6 slots, each exactly twice, never twice on one day, no same recipe lunch+dinner on a day; 5-day plan: 5 go-to slots (pro rata 1.43 -> rounds as designed). Pool of 1 recipe: that recipe fills all 14 slots (unavoidable). Zero local recipes: plan created as empty DRAFT with "No recipes yet / Build my plan" and snackbar "Save a few recipes first". Server-first: this local backend has POST /meal-plans/{id}/generate; its result ignores go-tos (backend#112 territory).
- Week view: overlapping plans all show with plan-name tag; "Open shopping list" disabled with "44 meals planned across 4 plans · 50 items / This week spans more than one plan"; Sunday-first week from locale; cooked slot is slot-specific (same recipe in another slot not marked), persisted across force-stop; drag reorder is list-move semantics and pins cooked meals.
- Shopping list: scaling correct (planned servings / recipe servings x slots, e.g. olive oil 3 tbsp x2 + 2 tbsp x... = 8 tbsp, parmesan 60 g, pasta 400 g); Metric ("≈ 280 g" for 1.5 cup basmati) and Imperial ("1⅛ oz + 10 pcs") conversions apply on reopen; checks (2 of 37) survive unit changes, reorder and recipe deletion (item removed, count 37 -> 36, checks kept). "Uncheck all" appears only once something is ticked. No share/copy exists. 101-item list (server-generated plan) scrolls fine.
- Saved plans: Scheduled/Saved tabs appear only when a saved plan exists and disappear after the last is deleted; Repeat picker defaults to the day after the last scheduled meal, blocks past days; Repeat after soft-deleting a source recipe drops that slot (Tikka lunch) as ADR-018 says; delete dialog text matches. saved_meal_plans rows stay PENDING (backend stale).
- Offline: two plans + 2 shopping checks created in airplane mode synced on reconnect: Postgres has exactly one row per plan (no duplicates), 7/7 and 5/5 days, checks present; cooked marks stay local (by design). `meal_plans` tombstone for the deleted empty draft arrived.
- Not reproduced: crash/ANR (none in logcat).

## Not tested / limits
- Seeded recipes via sqlite (not the editor); ingredient names "onion"/"onions" were stored as distinct catalogue rows (Onion, Onions) but never landed in the same plan, so aggregation of the two was not observed. "Tomato"/"Tomatoes" and "Chicken"/"Chicken Breast" stay separate lines by design (no plural folding).
- Cooked toggle on a slot that gets overwritten by "Add to meal plan" (PLANS-9), landscape layouts of wizard/week/list (rotation setting did not take effect in the last attempt; earlier landscape wizard step 2 showed only ~2 rows of content between header and Back/Next, SUSPECTED same class as UIA-2), households with a second member, share/print of a plan (Print icon present, not exercised), TalkBack, RTL/emoji titles, photo picker for saved plans, server sync of saved plans (backend stale).
- Local DB had stale rows from earlier QA (ingredients ids unknown to the server); INGREDIENT_NOT_FOUND pushes were an artefact of my first seed ids, not an app bug.
