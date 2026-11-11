# QA coverage checklist

What a QA session should cover, grouped by agent assignment. Tick off per session in that session's findings
file; don't edit this list for one-off results. Each line is a *behaviour to try*, not a script: for every one,
also run the exploratory prompts in [QA-BRIEF.md](QA-BRIEF.md). Intended behaviour lives in
[feature-status.md](../feature-status.md) and the ADRs.

**Priority backlog** — never covered so far (see the 2026-11-09 report): real TalkBack, emoji/RTL input,
split-screen, landscape of meal-plan/shopping/household screens, large font + dark mode everywhere, a second
household member's view of shared plans/lists, photo upload failure/progress, invite-by-email through the UI,
overwriting a cooked slot via "Add to meal plan", server sync of saved plans and cook events (needs a fresh
backend image), offline logout dialog / push-before-logout (`LogoutUseCase`).

## UI-A — auth, sessions, account (emulator)
- [ ] Fresh install → anonymous session works with no account; data persists across process kill.
- [ ] Register (valid, duplicate email, weak/blank/very long password, whitespace in email); login (wrong password, unknown user, offline).
- [ ] Anonymous → account upgrade keeps recipes, stars, go-tos, meal plans, shopping checks, cook history.
- [ ] Logout (online, offline → warning dialog, with unsynced changes) then login as a different user: no data from the previous user visible.
- [ ] Token refresh: expire/clear token mid-session; two screens refreshing at once; logout during a refresh.
- [ ] Developer options: environment switch, blank base URL refused, effect after restart.
- [ ] Landscape and small/large screens for Login, Register, Settings.

## UI-B — recipes: browse, create, edit, delete (emulator)
- [ ] Library list, search (empty, whitespace, `%`/`_`, emoji, very long, accents), tags/labels, starred, go-to.
- [ ] Create: required fields, quantity edge cases (0, negative, `1e307`, `NaN`, `1/2`, `½`, `1,000`), units, steps ordering, image URL, photo.
- [ ] Edit: change every field, reorder/delete steps and ingredients, discard dialog, rotate mid-edit.
- [ ] Delete from details: confirm, gone from lists/plans/shopping lists, sync of tombstone, no undo expectation.
- [ ] Details: servings scaling, unit system Metric/Imperial, cook button, step timer (start, replace, rotate, background, notification denied).
- [ ] Offline create/edit/delete then reconnect: header sync state matches reality; compare Room vs backend row.

## UI-C — meal plans and shopping lists (emulator)
- [ ] Generate a week plan (go-tos first, no same recipe lunch+dinner, dietary restrictions honoured, empty library, 1 recipe).
- [ ] Week view: swap, remove, add via "Add to meal plan", cooked marks, multi-plan week, delete a recipe that is in a plan.
- [ ] Saved plans: save, schedule, repeat, delete; photo is device-local.
- [ ] Shopping list: scaling, merge duplicates, unit conversion, check/uncheck offline and sync, per-plan lists.
- [ ] Household sharing of plan and list (both members, remove member, leave).

## UI-D — import, share, timers, households, photos, sync (emulator)
- [ ] URL import: JSON-LD site, microdata site, non-recipe page, 404, offline, DNS failure, redirect, huge page, non-English page.
- [ ] SSRF refusals: `file:`, `localhost`, `10.0.2.2`, `127.0.0.1`, `[::1]`, `169.254.169.254`, private ranges, `ftp:`, `javascript:`.
- [ ] Share-intent import (link with trailing punctuation, no link, two shares in a row); duplicate-import behaviour.
- [ ] Timers: one at a time, replace, process kill, doze/background notification, permission denied.
- [ ] Households: create, invite link (existing member, expired, own invite), accept, remove, leave as sole owner.
- [ ] Photos: pick, upload (offline, killed mid-upload), replace, delete; blob visible on the server.
- [ ] Sync: edit on two devices, conflict (LWW on `updatedAt`), reinstall pulls everything back, header cloud icon vs real state.

## API — backend over `curl` (no emulator)
- [ ] Auth: register/login/refresh/logout; malformed JSON, missing fields, huge bodies, wrong content-type, expired/forged JWT.
- [ ] Sync push: empty body, NUL (U+0000) in text, oversized batch, unknown ids, duplicate ids, stale `updatedAt`, tombstone of an unknown entity.
- [ ] Sync pull: `since` = 0 / negative / huge / non-numeric; cursor never stalls (page through a user with >1 page of data, assert every row arrives exactly once); `hasMore`.
- [ ] Authorization: another user's private recipe/plan/household by id; anonymous access to public data; what personal data (emails) public payloads expose.
- [ ] Public recipes: PUBLIC→PRIVATE reaches already-pulled devices; sitemap contents.
- [ ] Households and invites: roles, expired/reused invites, removing the owner.
- [ ] Images: size/type limits, path traversal in names, auth on download.

## LOGIC — JVM probes of `:app` domain/data code
- [ ] Quantity/unit parsing and formatting: non-finite, huge, tiny, fractions, unicode fractions, thousand separators, locale decimals.
- [ ] Unit conversion round trips; shopping-list merging across units.
- [ ] Mappers: Room↔domain↔DTO null/blank/over-long fields, sync state transitions.
- [ ] `SyncOrchestrator`: push/pull ordering, partial failure, retries, cursor handling, tombstones, account switch.
- [ ] `SessionManager`: refresh races, logout races, upgrade flow.
- [ ] Meal-plan generator: determinism, constraints, empty/small inputs.

## SCRAPER — `:recipe-scraper` JVM probes
- [ ] JSON-LD variants: `@graph`, arrays, nested `@type` lists, `HowToSection`/`HowToStep`, deep nesting (stack depth), huge documents.
- [ ] Microdata variants; malformed HTML; duplicate recipes on a page; missing fields.
- [ ] Ingredient line parsing: ranges, dual units, `1,000 g`, `NaN`, fractions, non-English.
- [ ] Durations (ISO-8601 edge cases), servings strings ("serves 4-6"), image URL schemes (`data:`, `file:`, `javascript:`).
