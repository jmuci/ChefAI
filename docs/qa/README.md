# Running a QA session

A QA session = one lead (you or a Claude session) + several agents, each with one area, one device (if UI)
and one findings file. The first session (2026-11-09, ~130 bugs) is archived in
[sessions/2026-11-09/](sessions/2026-11-09/2026-11-09-qa-report.md).

Files here: [QA-BRIEF.md](QA-BRIEF.md) (rules every agent reads), [test-cases.md](test-cases.md) (what to cover),
[`scripts/qa/prepare-qa-env.sh`](../../scripts/qa/prepare-qa-env.sh) (safe environment).

## 1. Prepare the environment (the lead does this, once)
1. Boot the emulators (one per UI agent; a second instance needs a *different* AVD, e.g. Pixel 6 + Pixel 9 Pro Fold).
2. Start the backend: `docker compose up -d` in the backend checkout (`CHEFAI_BACKEND_DIR`, default `~/Projects/ktor-chefai`).
3. Run:
   ```bash
   scripts/qa/prepare-qa-env.sh --rebuild-backend
   ```
   It rebuilds the backend image if asked, builds the debug APK **pinned to the local backend**, installs it on
   every emulator (never a physical phone), and checks in logcat that requests go to `10.0.2.2:8080` and never to
   `pocket-chef.app`. It exits non-zero if production is contacted. `--skip-build` reuses an APK the script built.
4. Create `docs/qa/sessions/<date>/` for findings.

**Why this matters:** a default debug build talks to production (`ApiEnvironment.DEFAULT = REMOTE`), and `pm clear`
re-arms that. In the first session, two agents created accounts and data on production before it was noticed.

## 2. Pick agents and models
Cap at **4 concurrent agents** (7 at once hit the usage limit; a resumed agent continues via `SendMessage`).

| Agent | Needs | Model |
|---|---|---|
| UI-A..D (one emulator each) | adb, screenshots, long tool loops | **Sonnet 5.5** — screenshot-driven loops are cheap and it follows the brief well |
| API (curl) | local backend | **Sonnet 5.5** |
| LOGIC / SCRAPER (JVM probes) | reading code, writing throwaway tests | **Opus 5.5** when you want root causes for subtle bugs (concurrency, parsing); Sonnet 5.5 for a quick sweep |
| Lead / triage / consolidating the report | judgement, dedupe, severity | **Opus 5.5** |
| Fixing S1/S2 | — | **Opus 5.5** for sync, auth/session, concurrency, data-loss or security; **Sonnet 5.5** for mechanical, local fixes (input validation, layout) |

Run fix agents in separate worktrees, one PR per theme (3 PRs at a time worked well), never two agents on the same files.

## 3. Prompts
**Per-agent prompt** (fill the `<>`; the brief carries the rules):
```
You are QA agent <UI-A> for Pocket Chef. Read docs/qa/QA-BRIEF.md, CLAUDE.md, docs/feature-status.md first.
Your area: <auth, sessions, account>. Your device: <emulator-5554> only. Findings file:
docs/qa/sessions/<date>/findings-<ui-a>.md. Cover the "<UI-A>" section of docs/qa/test-cases.md, then explore
freely using the exploratory prompts in the brief. Before your first action, verify via logcat that requests
go to 10.0.2.2:8080. Report only what you reproduced; mark the rest SUSPECTED. Append each bug as you find it.
Final reply: <=300 words (counts by severity, top issues by ID, coverage done and skipped).
```
**Lead kickoff:**
```
Run a QA session on Pocket Chef. Follow docs/qa/README.md: prepare the environment with
scripts/qa/prepare-qa-env.sh, then launch <4> background agents (areas: UI-A, UI-B, API, LOGIC) with the
per-agent prompt, Sonnet 5.5 for UI/API and Opus 5.5 for LOGIC. Start with the priority backlog in
docs/qa/test-cases.md. Don't fix anything. When they finish, consolidate into
docs/qa/sessions/<date>/<date>-qa-report.md (table by severity, top issues, cross-agent duplicates, coverage caveats).
```
**Triage and filing:**
```
Read docs/qa/sessions/<date>/. File one GitHub issue per S1/S2 finding (merge duplicates across agents), label by
priority (P0-P3, see issue-priorities), link finding IDs, and propose 3 PR-sized batches ordered by risk.
```
**Fix agent:**
```
Fix <issue #>. Work in a new worktree off main. Add a failing test first, then the fix. Run
./gradlew :app:testDebugUnitTest. Don't change public API or add dependencies without asking. Report the diff summary.
```

## 4. During the session
- Watch for agents calling production (`grep pocket-chef.app` in their logs); stop and fix the environment first.
- If an agent's Write is refused, it should return findings as text; the lead writes the file.
- Slow host → many ANR dialogs; treat them as environmental and say so. `adb input text` drops characters unless sent in 4-char chunks.
- Backend-stale symptoms are not app bugs: re-run the script with `--rebuild-backend`.

## 5. After
Consolidate the report, file issues, then update [test-cases.md](test-cases.md)'s priority backlog with what
was *not* covered. Clean up any test data you created outside local (there should be none).
