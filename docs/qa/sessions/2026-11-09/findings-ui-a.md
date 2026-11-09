# QA findings — UI-A (emulator-5554, Pixel 6)

Build under test: debug APK built 2026-11-09 06:53 from branch `claude/pocket-chef-qa-testing-188626` (HEAD 36bb2b1a).

### UIA-0: NOT TESTABLE — "Log out while offline?" dialog is not in the installed build
- Severity: n/a (test-coverage note)
- Status: CONFIRMED
- Detail: the logout-pushes-pending-changes work (`LogoutUseCase`, offline dialog, "Logging out…") lives only on branch
  `claude/elegant-grothendieck-706340` (commit 9e738f44), which is NOT an ancestor of the QA branch HEAD; the
  installed APK contains no `LogoutUseCase` class and `UserProfileViewModel.logout()` in this worktree calls
  `sessionManager.logout()` directly. Logout was therefore tested only in its current (plain) form, incl. offline.

### UIA-1: !! ENVIRONMENT — debug build defaults to PRODUCTION (ApiEnvironment.DEFAULT = REMOTE); `pm clear` / fresh install silently resets to prod
- Severity: S1 (process/safety; violated the brief's "local backend only" without the tester knowing)
- Status: CONFIRMED
- Steps: 1. Launch the installed debug build on emulator-5554 (first request at 07:06 was
  `https://api.pocket-chef.app/api/v1/home/layout`). 2. Login screen -> Developer options: "Use remote environment" toggle was ON
  (persisted DataStore state from an earlier session; no tester action had switched it).
- Expected: debug build on a fresh QA emulator talks to http://10.0.2.2:8080 (brief).
- Actual: ~28 logged requests (layout, sync pull, auth/login, auth/register x3) went to production before it was noticed
  (noticed ~07:14 via logcat `HttpClient: REQUEST`). Local docker DB has no `qa+uia*` users, confirming it was not local.
  Production side effects created by UI-A: accounts `qa+uia1@example.test` (username qauia1), `qa+uia2@example.test`
  (username qauia1 — a duplicate username was accepted), plus a login attempt for `qa+ghost@example.test`, bookmarks of
  recipes "Paella" (uia1) and "Grilled Chicken" (uia2) pushed. These need cleanup by the lead. Toggle was then switched OFF
  (local) on 5554 at ~07:15 and verified `http://10.0.2.2:8080` shown. Everything tested on 5554 BEFORE this point
  (units/language settings, login/register validation, upgrade-keeps-data, logout, dup email message) ran against PROD,
  not local. Other QA agents / emulator-5556 may be in the same state: CHECK `adb -s <serial> logcat -d | grep "HttpClient: REQUEST"`.
- Suspected cause: persisted env flag survives APK reinstall? (DataStore not cleared) — or the emulator image was
  snapshotted with it on. Worth considering: debug builds could reset to local on install / show a persistent banner when remote.


NOTE (lead messages): installed build predates LogoutUseCase, so no offline-logout dialog (UIA-0 is not a bug). Backend container is stale
(cook events / saved plans / recipe language sync not exercised). All results below UIA-1 were re-run against the LOCAL backend
(http://10.0.2.2:8080) after the environment toggle was switched off; client-only checks (settings, validation, layout) are env-independent.

### UIA-2: Login screen is unusable in landscape — Log In button / Remember me / "Create Account" clipped, "Developer options" + version drawn over the Password field
- Severity: S2 (login/registration impossible until rotated back; S3 if you consider rotation rare on phones)
- Status: CONFIRMED
- Steps: 1. Anonymous session -> profile menu -> Log in / Register. 2. Rotate device to landscape (`settings put system user_rotation 1`, accelerometer off).
- Expected: form scrolls so every control (incl. Log In) is reachable; footer does not overlap fields.
- Actual: with the keyboard closed the page does not scroll; accessibility tree has no "Log In", "Remember me" or "Don't have an account?" nodes;
  "Developer options" and "Version…" text is painted on top of the Password field (tapping there focuses the password field);
  the footer is anchored to the bottom while the form column overflows. With the keyboard open the screen only scrolls a few rows.
- Evidence: scratchpad/rt5.png (landscape login), rt7.png (keyboard up + overlap)
- Suspected cause: LoginScreen layout = Column with fillMaxSize + bottom-aligned footer, no verticalScroll / imePadding handling (auth/ui/LoginScreen.kt). Register screen not separately verified in landscape.

### UIA-3: Large font (2.0x): text wraps mid-word / is truncated in several core components
- Severity: S3
- Status: CONFIRMED
- Steps: `adb shell settings put system font_scale 2.0`; open Login, Meal Plans, profile menu, bottom nav.
- Actual: (a) bottom nav label "Meal Plans" shows as "Meal" (truncated, no ellipsis) on every tab; (b) Meal Plans week/plans toggle
  wraps "We/ek", "Pla/ns"; weekday labels wrap per letter ("SU/N", "MO/N", "TU/E"); (c) Login "Create Account" link breaks as "Accoun/t";
  (d) profile menu row "Log in / Register" is ellipsized "Log in / Regi…". At 1.3x everything fits. Icons/avatars/checkbox do not scale (cosmetic).
- Evidence: scratchpad/m_f.png, m_f2.png
- Suspected cause: fixed-width pill/toggle/day-label containers and single-line nav labels; no maxLines/minWidth handling.

### UIA-4: Logged-out relogin shows "Invalid email or password" twice (under Email and under Password); error under Email lingers after editing password
- Severity: S4
- Status: CONFIRMED
- Steps: Login with a wrong password. Edit only the password field.
- Actual: identical message is shown below both fields; after retyping the password the Email-field error stays until the email is edited.
  Related: layout jumps as the field errors appear/disappear, so a tap aimed at the Password field can land on the Email field (I typed a password into the email field several times because the form reflowed between taps).
- Evidence: scratchpad/l6.png, x2.png

### UIA-5: Login enforces "Password must be at least 8 characters" client-side
- Severity: S4
- Status: SUSPECTED (design question)
- Detail: a password shorter than 8 (e.g. legacy/seeded account) can never log in; the server is the right place to say "invalid".

### UIA-6: Registration accepts a duplicate username; duplicate email error reveals that the address is registered
- Severity: S4 (privacy note)
- Status: CONFIRMED (observed against PROD during UIA-1 window; username dup accepted; email dup message "User with email X already exists"; local backend reproduces the email message)
- Detail: account enumeration via register + login error is generic ("Invalid email or password") — only register leaks.

### UIA-7: Anonymous profile menu header reads "SIGNED IN AS / Guest"
- Severity: S4 (wording)
- Status: CONFIRMED
- Detail: an anonymous user is not signed in; the label is from the shared header design. Consider "Browsing as" for guests.

### UIA-8: Field validation errors use the same teal as the focus ring (no distinct error colour)
- Severity: S4 (accessibility, colour-only differentiation). Likely a design-system choice ("no error red", see UserProfileMenu KDoc) — flag only.
- Evidence: scratchpad/r2.png

#### UIA-1 addendum — full accounting of requests that reached api.pocket-chef.app from emulator-5554 (by UI-A)
Window 1, 07:06-07:15 (device clock; fresh install, default env = REMOTE, I did not know): home layout GETs; `POST /auth/login` x1
(qa+ghost@example.test, wrong password -> 401, nothing created); `POST /auth/register` x3: qa+uia1@example.test (username qauia1) CREATED,
qa+uia1@example.test again (username qadup) rejected as duplicate email, qa+uia2@example.test (username qauia1) CREATED;
sync push/pull for both accounts (bookmark Paella for uia1, Grilled Chicken for uia2). Toggle then switched to LOCAL (~07:15, verified `http://10.0.2.2:8080`).
Window 2, 07:26-07:34: I ran `pm clear` (to test first-launch offline Home), which reset the env flag to the DEFAULT = REMOTE again; I did not
re-check until the lead's message. Requests: home layout GETs and `POST /auth/register` x2 (07:32:05, 07:32:27) with qa+uia9@example.test
(300-char username / whitespace-only username) — BOTH answered 400, no account created. Switched back to LOCAL at ~07:34 and verified.
Prod accounts needing cleanup: qa+uia1@example.test, qa+uia2@example.test (+ their bookmarks). Nothing else was created. No requests went to prod after 07:34.
Local equivalents of these accounts were created later in the local DB (same emails).
Root cause: app/.../developeroptions/domain/model/ApiEnvironment.kt:20 `val DEFAULT: ApiEnvironment = REMOTE`, debug build included.
Suggest: DEFAULT = LOCAL for debug buildType (or a loud "PRODUCTION" banner in debug builds); Developer options shows the toggle only on the login screen, so it is hard to notice.

### UIA-9: Home carousel images all announce "Recipe Image" (no recipe name in the image's content description)
- Severity: S4 (a11y)
- Status: CONFIRMED (uiautomator: every card image has content-desc "Recipe Image", title is a separate text node)
- Detail: TalkBack reads "Recipe Image" then the title; harmless but noisy. Icon-only buttons (profile, back, show password, bookmark, week arrows) do have labels.

## Verified OK (UI-A)
- Anonymous first launch: Home SDUI loads; tabs Home/Search/Recipes/Meal Plans; back from any tab -> Home -> exits; relaunch after force-stop returns to Home.
- Settings (units As written/Metric/Imperial; recipe language) persist across force-stop; Imperial converts Paella (200 g -> 7 oz, "≈ ¾ cup" for rice). No theme/dark-mode toggle exists (system theme only; dark mode renders fine on Home/menu/login/register).
- Anonymous -> register upgrade keeps data (bookmarked Paella remained and was pushed; local DB bookmark row confirmed). Logout then login restores it.
- Account switch (local backend): uia1 sees only Paella, uia2 only Grilled Chicken after logging in alternately; anonymous view after logout is empty.
- Offline: Home falls back to a bundled layout with an offline cloud icon (also after `pm clear`); recipe detail opens; offline logout works instantly with no warning (build predates the dialog).
- Validation: empty fields, malformed email, short password, mismatched confirm, 250-char email ("Email is too long (max 254)"), 300-char username ("Username is too long (max 100)"), whitespace-only username ("Username is required"), duplicate email, wrong password; double-tapped Log In sends one request. Rotation keeps typed form input.
- Developer options: only reachable from login screen; shows one toggle "Use remote environment". Toggling OFF -> local. (Never toggled to remote by me.)

## NOT tested / limits
- "Log out while offline?" dialog / push-before-logout (not in build). Different-account login after offline logout (documented ADR-008 gap) not re-run.
- Cook history / saved plans / recipe language sync (backend stale). Pull-to-refresh on Home (none observed). Home loading/error state with a reachable-but-failing backend (offline fallback only).
- Emoji/RTL input (adb input cannot type them). TalkBack itself (only semantics via uiautomator). Register screen in landscape; dark mode at 2.0x font; clock/timezone change.
- Anything before ~07:15 ran against PROD (see UIA-1); the same flows were redone on local except wrong-password/duplicate-username.
