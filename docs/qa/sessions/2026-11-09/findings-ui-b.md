# QA findings — UI-B (recipes, search, timer; emulator-5556 Pixel 9 Pro Fold), 2026-11-09

UI-B stopped early (see "Production contamination"). 8 findings. UIB-1 to 6 CONFIRMED; UIB-7 is code-read only;
UIB-8 CONFIRMED.

## Production contamination (needs a decision)
The QA brief said debug builds talk to the local backend. That was wrong: `ApiEnvironment.DEFAULT = REMOTE`
(`developeroptions/domain/model/ApiEnvironment.kt`) and `~/.gradle/gradle.properties` sets
`chefaiApiBaseUrl=https://api.pocket-chef.app`, so a fresh debug install talks to **production**.
- Account `qa+recipes1@example.test` (uuid 4b8df54c-3d9d-402d-8c6a-fcd165d4fdd7) was registered on production and
  a recipe "QA Stew" (a bookmark with star and go-to set, then cleared) synced there. It is absent from local `chefai_db`.
- "OfflineSoup" was created offline on the device and has not synced.
- Left as-is: airplane mode ON on emulator-5556 and the app force-stopped, so nothing reaches production.
- Production data was not cleaned up.

## Findings
- **UIB-1 (S3)** — offline search fallback doesn't escape LIKE wildcards: offline `%%%` and `___` return 20 results; online they return none (`RecipeDao.kt:294-303`).
- **UIB-2 (S3)** — the last step's timer button on recipe details is hidden under the Edit FAB; tapping where it should be opens Edit.
- **UIB-3 (S3)** — the editor accepts servings 0, cook time 0 and negative prep time with no validation message (`RecipeEditorReducer.kt:29-48`).
- **UIB-4 (S3)** — on the fold, the editor photo block fills the whole display and the details hero is ~1280px tall; the no-photo placeholder icon is huge; Recipes shows one card per row.
- **UIB-5 (S4)** — the finished-timer notification has no `contentIntent`, so tapping it doesn't open the app (`RecipeTimerNotifier.kt`); it reads "Step 3" with no recipe name.
- **UIB-6 (S4)** — ingredient autocomplete lists "Flour" twice; the popup is hidden behind the IME.
- **UIB-7 (S4, code-read only)** — the Qty field silently drops "/", so "1/2" becomes 12; an ingredient can't be added without a quantity (`RecipeEditorScreen.kt:626`).
- **UIB-8 (S4)** — the "scaling from N" note shows the current servings rather than the base.

## Coverage
Account creation, create and edit, filter chips, bookmark/star/go-to and removal, restart persistence, servings
scaler, metric/imperial (OK), timer start/pause/background/notification, search 3-character minimum and special
characters, offline search.

## Not covered
URL import, SSRF and share intent, rotation and multi-window, process-kill timer, photo picker, sync
verification against the backend (all still open; re-run against the local backend).
