# QA findings — UI-D (import, rotation, timer, households, photos, sync; emulator-5556), 2026-11-09

Round 2, app pinned to local backend (verified: no request to api.pocket-chef.app). Account qa+import1@example.test,
second user qa+hh2@example.test. 3 live external requests (example.com, bbcgoodfood 404, bbcgoodfood easy-pancakes).
Environment note: host load average ~15–17, emulator swapping, many ANR dialogs (main thread idle in a bugreport
thread dump; treated as environmental); `adb input text` dropped characters unless sent in 4-char chunks.
Counts: 1 × S2, 7 × S3, 8 × S4 (IDs have gaps).

## S2
### UID-10: a user recipe that references a "home sidecar" ingredient never syncs, and the UI says "Synced"
- Status: CONFIRMED, root cause identified; reproduced after `pm clear` on a clean DB.
- Steps: fresh account → import https://www.bbcgoodfood.com/recipes/easy-pancakes → save the draft.
- Actual: every push is rejected: server log `Sync push processed ... accepted=0 errors=1`, `reason=INGREDIENT_NOT_FOUND` (recipeId 01a1c19c-3d37-736f-9ed0-55290773edc3). Recipe stays PENDING; header cloud shows "Synced".
- Suspected cause: `SidecarMapper.kt:64-75` creates ingredients from the home sidecar with `UUID.nameUUIDFromBytes(name)` and `syncState=SYNCED`; the server doesn't have those ids (98 local SYNCED ingredients not among the server's 1079). Name matching ("Plain flour" → local 51e4421d…; server has "Plain Flour" 2ded662b…) picks the local one, and the `SyncOrchestrator.kt:231-245` filter trusts `syncState==SYNCED`, so the id is pushed and the whole recipe is rejected. Also explains UIB-6 (duplicate "Flour" in autocomplete). Production exposure depends on whether production's catalogue matches the sidecar ids.

## S3
- **UID-11** (CONFIRMED) — delete tombstone for a never-synced recipe errors forever. Import the same URL twice, delete the second (unsynced) copy → row stays DELETED; every sync pushes it with ingredient refs and gets INGREDIENT_NOT_FOUND (recipeId 01a1c1a0…); its bookmark stays PENDING and logs `RECIPE_NOT_FOUND` each push.
- **UID-12** (CONFIRMED; code carries a TODO for #101) — ingredients new to the server are silently dropped from the server copy. Create a recipe offline with ingredient "zorkroot" + a step, go online: server row accepted but has 0 ingredient rows and 1 step; UI says Synced; a reinstall would lose the ingredients. Cause: `SyncOrchestrator.kt:231`.
- **UID-1** (CONFIRMED) — offline or any DNS failure on import shows "That doesn't look like a valid link" (also for `.invalid` and `localtest.me`, which resolves to loopback). Cause: `SystemHostResolver.kt:13` returns empty on UnknownHostException → `DefaultRecipeImporter.kt:63` returns InvalidUrl.
- **UID-2** (CONFIRMED on device; = SCRAPER-25) — a 404 page shows "Couldn't reach that site – check your connection" with no "Enter it manually" option (only offered for NoRecipeFound).
- **UID-15** (CONFIRMED; known limitation) — timer notification arrives late: 2-minute timer, Home pressed; due 12:03:06, "Timer done / Step 1" posted 12:05:09 when the app was woken (process frozen; countdown ticks in-process only). `contentIntent=null` (= UIB-5), no recipe name. Fix: schedule an AlarmManager alarm.
- **UID-14** (CONFIRMED) — with notifications denied the timer finishes silently, no hint anywhere; on return a "0:00 x" pill floats over every screen (Recipes list, FAB area, household screen) until x is tapped.
- **UID-18** (CONFIRMED) — household members show blank names: new accounts have empty `display_name` (register API only sets `user_name`) and the Household screen renders `displayName`. Owner shows "?" avatar + "you"; second member "?" + "Member"; Remove confirmation reads " will lose access to the shared meal plan and shopping list." "1 member · sharing one plan" is hard-coded in `household_members_count` even with one member and no plan.
- **UID-6** (CONFIRMED) — landscape editor nearly unusable: only ~300 px of 1080 px left for the form between top bar and Save bar, hero photo fills it; slow swipes don't scroll, fast flings do. Edited text survives rotation; nav rail does appear in landscape.

## S4
- **UID-16** — floating timer pill covers content on recipe details (an ingredient quantity, step text).
- **UID-19** — Leave-household dialog for a sole owner says only "You'll lose access to the shared meal plan…" although the household is deleted; after leaving, app goes to Home rather than the "No household yet" screen.
- **UID-4** — Image URL label, field and "or" divider in the editor run edge-to-edge without the margins other fields have (new, edit and imported editors). On the fold the photo block fills the screen (= UIB-4).
- **UID-5** — imported ingredients can have blank quantity and unit ("lemon wedges to serve (optional)") while the manual editor refuses an ingredient without a quantity (UIB-7).
- **UID-7** — importing the same URL twice (second via share intent) silently creates a second identical recipe, no duplicate warning; after saving a share-opened draft the user lands on the previously open recipe, not the new one.
- **UID-20 (SUSPECTED, flapping emulator network)** — header sync icon alternated "Offline"/"Synced" while connected; one "Offline" period lasted >3 minutes while push/pull succeeded. `NetworkMonitor.kt` registers a callback for all networks and sets the flag per callback, so last writer wins. logcat: "Network lost, isOnline=false" 12:01:50 with no later "Network available".
- **UID-13** — Save disabled with no hint which field is missing; the servings field shows "4" as a placeholder that looks like a value.

## Verified OK
- SSRF refusals in the UI: `file:`, localhost, 10.0.2.2, 127.0.0.1, [::1], 169.254.169.254, 192.168.1.1, `ftp:`, `javascript:`, `notaurl` — no request logged for any.
- BBC easy-pancakes import: title, description, times, servings 12, calories/protein, image shown and cached, English detected, 6 ingredients, 5 steps; scraped keywords incl. author name appear as tags. Non-recipe page correctly offers manual entry.
- Share intent: no-link text gives a toast; URL with trailing punctuation auto-imports.
- Rotation: editor text retained, timer survives rotation; fold/unfold retains state, nav rail on the large display.
- Timer: force-stop loses it (documented); replacing shows a "Replaced the timer for Step 2" toast.
- Households: whitespace-only name keeps Create disabled; invite/accept (via API), member list, remove member (server shows 404 "no household"), invite deep link as existing member ("You're already in a household"), leave household.
- Photos: picker, attach, upload (server blob present), delete recipe with photo (server tombstone applied, blob kept).
- Sync vs. local Postgres: offline create synced on reconnect; edit and photo updated server row; delete tombstone applied.
- API-1 observed on device: after multiple pulls local DB held 93 recipes (91 synced) vs 880 public on the server; pulls returned 20 with hasMore=true then stopped.
- Not a bug: local-backend invite URLs use chefai.app while the manifest host is pocket-chef.app (probably stale/misconfigured local server).

## Not covered
Photo upload failure/offline/progress; split-screen; landscape of Meal Plans, shopping list and household screens (screenshots lagged), large font and dark mode; second member's view of meal plan/shopping list (stale backend); invite-by-email through the UI (ANRs blocked typing); notification tap-through (no contentIntent).
