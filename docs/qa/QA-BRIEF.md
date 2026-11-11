# QA agent brief

Shared rules for every QA agent. Read `CLAUDE.md`, `docs/feature-status.md` and `docs/architecture.md`
first for what the app is *supposed* to do; the ADRs in `docs/adrs/` hold intended behaviour.

## Environment
- Worktree: this repo. App = debug build installed on the emulators (`com.tenmilelabs.chefai`) by
  `scripts/qa/prepare-qa-env.sh`, which builds with `-PchefaiApiBaseUrl=` (blank) so the app has no
  production URL and talks only to the **local backend** at `http://10.0.2.2:8080` (host `http://localhost:8080`,
  Docker `chefai_service` + `postgres_db`). A default debug build hits PRODUCTION — never reinstall or
  `pm clear` with a different APK (`pm clear` on the pinned build is safe). Before your first action, confirm with
  `adb -s <serial> logcat -d | grep "REQUEST:"` that requests go to `http://10.0.2.2:8080`.
- If the script warned that the backend image is stale, features newer than the image (cook events, saved
  plans, recipe `language`, ...) don't work server-side: report those as "backend stale", not app bugs.
- If the Write tool refuses to create your findings file, return the findings as text in your final reply.
- Emulators: use **only the one assigned to you**, always via `adb -s <serial>`. Shared devices are not yours.
- Screenshots: `adb -s <serial> exec-out screencap -p > <scratchpad>/name.png`, then Read the PNG.
  Dump UI: `adb -s <serial> shell uiautomator dump /sdcard/u.xml && adb -s <serial> pull /sdcard/u.xml`.
  Drive with `adb shell input tap/swipe/text/keyevent` (`input text` drops characters on a loaded host: send
  4-char chunks). Logs: `adb logcat -d | grep -i chefai` / `AndroidRuntime`.
- Existing Maestro flows in `.maestro/` are good references for stable selectors/text.

## Hard rules
- **Only use `emulator-*` serials.** Never touch a physical device (the user's real phone has real data).
- **Never call `api.pocket-chef.app`** (production). Local backend only. Do not change the app's API environment.
- **Do not edit production code, commit, push or stash.** You are finding bugs, not fixing them.
  Throwaway probe tests are allowed only where your assignment says so; delete them when done.
- Test accounts: use throwaway emails like `qa+<area><n>@example.test`, password `QaPassw0rd!23`.
- Don't spend the whole session on one bug; breadth first, then dig into suspicious spots.
- Be honest: only report something as a bug if you reproduced it or can point at the exact code that
  is wrong. Mark unreproduced suspicions as `SUSPECTED`. Note what you could NOT test and why.

## Bug write-up format (append to your findings file as you go, not at the end)
```
### <AREA>-<n>: <one-line title>
- Severity: S1 crash/data loss/security | S2 broken feature | S3 wrong/confusing behaviour | S4 cosmetic
- Status: CONFIRMED | SUSPECTED
- Steps: 1… 2… 3…
- Expected: …
- Actual: …
- Evidence: screenshot path / logcat excerpt / curl output
- Suspected cause: file:line (if known)
```
Findings go in `docs/qa/sessions/<date>/findings-<your-area>.md`. Final reply to the lead: ≤300 words — bug count by severity,
the top issues by ID + title, coverage done, coverage skipped. Don't paste the full write-ups.

## Exploratory-testing prompts (use in addition to your checklist)
Empty/very long/emoji/RTL/whitespace-only input; double-tap; rapid back-and-forth; rotate mid-operation;
background and return; kill the process mid-operation (`adb shell am force-stop`); airplane mode on/off
during operations; clock/timezone change; large font and dark mode; small and large screens; deep links;
repeat an action twice; do things in the "wrong" order; compare what the UI says vs. what the backend/DB holds.
