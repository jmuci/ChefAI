# QA findings - API (black-box, local backend http://localhost:8080)

Environment notes
- Backend under test is the running container `chefai_service` (image `ktor-chefai-app`, built 2026-10-20), NOT the
  current source. `~/Projects/ktor-chefai` is also 2 commits behind `origin/main` and has no `savedMealPlans`
  at all (see API-3), so source references below are "closest available" and may differ from what runs.
- Throwaway users: qa+api1@example.test (A), qa+api2@example.test (B), more later. Password QaPassw0rd!23.

### API-1: Pull cursor can never advance past the seed recipes - fresh install/new device never receives its own recipes
- Severity: S1 (data never restored on reinstall / second device; sync silently stalls)
- Status: CONFIRMED (local DB). SUSPECTED on production if its recipes table was seeded the same way.
- Steps:
  1. Register a brand new user, push one private recipe (or just use any user that has one).
  2. Page `GET /sync/pull?since=0&limit=100`, then repeatedly `since=<serverTimestamp>` while `hasMore=true`.
- Expected: pages walk through all visible recipes (898 in DB) and finally reach the user's own recipe.
- Actual: page 1 = 90 recipes, cursor 1787615284244, hasMore=true. Every following call with
  `since=1787615284244` returns the same 20 recipes, same cursor, hasMore=true, forever. The user's own recipe
  (server_updated_at 2026-11-09) is never delivered. The Android client detects the stall
  (`SyncOrchestrator.pull`, "cursor stalled ... stopping") and gives up each run, so a fresh install
  never gets past ~110 seed recipes and never restores the user's own recipes/bookmarks/plans' recipes.
- Evidence: `select date_trunc('milliseconds',server_updated_at), count(*), count(distinct server_updated_at) from recipes group by 1`
  -> 70 rows @ .235, 16 @ .243, 20 @ .244 (each its own microsecond value), 789 rows @ .334 (one identical µs value).
  Pull trace (script t3.py): `since 0 n 90 hasMore True cursor ...244`, then `since ...244 n 20 hasMore True cursor ...244` x N.
- Suspected cause: `PostgresSyncRepository.findDeltaRecipes` filters `server_updated_at > Instant.fromEpochMilliseconds(since)`
  but the column has microsecond precision (seed rows inserted with DB `now()`), while the cursor is the row's value truncated to ms.
  A row whose µs fraction is non-zero is therefore `> its own cursor` and is returned again on every page. In
  `SyncService.fetchStablePage` the "earlierGroups" branch then returns that re-sent group (it is "earlier" than the 789-row tie that follows)
  with hasMore=true, so progress is impossible. Even if reached, the 789-row tie at .33458 has the same problem. Fix: compare on
  `date_trunc('milliseconds', server_updated_at)` / store ms precision, or use a (timestamp,id) cursor.

### API-2: Local backend container is stale - cook events, saved meal plans, recipe language, meal-plan reads silently unsupported
- Severity: S2 (invalidates QA of ADR-017/018/016 against local backend; client rows may sit PENDING forever)
- Status: CONFIRMED
- Steps: `POST /sync/push` with `cookEvents:[...]` and with `savedMealPlans:[...]` (valid payloads) as A; `GET /sync/pull`;
  `GET /api/v1/me/meal-plans`; push recipe with `language:"es"` then pull it; `\dt` in `chefai_db`.
- Expected: 200 with `cookEvents.accepted` / `savedMealPlans.accepted` per ADR-017/018; pull carries `cookEvents`/`savedMealPlans`;
  `language` round-trips; `/api/v1/me/meal-plans` exists (slug reads).
- Actual: push returns 200 with empty results (no accepted, no errors) for both; pull response has no `cookEvents`/`savedMealPlans`
  keys; `language` is dropped; `/api/v1/me/meal-plans[...]` -> 404; DB has no `recipe_cook_events` / `saved_meal_plans` tables; plan push
  response carries no `slug`. Container `chefai_service` image built 2026-10-20; `~/Projects/ktor-chefai` checkout is also 2 commits behind
  origin/main and has no saved-plan code. Every unknown push key is silently dropped (`ignoreUnknownKeys=true`) - a client has no way to
  tell "not supported" from "nothing to do", so those rows stay dirty and are re-pushed every sync.
- Evidence: push -> `{"accepted":[],"conflicts":[],"errors":[],"serverTimestamp":...,"mealPlans":{},"groceryListItems":{}}` (no cookEvents/savedMealPlans objects).
- Suspected cause: environment (stale Docker image). Recommend rebuilding from current backend main before UI agents test cook history/saved plans.
  Also a robustness gap: server should not 200-ack payload sections it does not understand.

### API-3: Sync push with empty body or non-JSON Content-Type answers 500
- Severity: S2 (5xx on client input)
- Status: CONFIRMED
- Steps: `POST /sync/push` with valid bearer and (a) empty body, (b) `Content-Type: text/plain` + `{"recipes":[]}`, (c) `application/xml`.
- Expected: 400/415.
- Actual: 500 `{"message":"Sync push failed unexpectedly (traceId=...)"}` for all three. Server log: `CannotTransformContentToTypeException`.
- Suspected cause: `SyncRoutes.kt` catches `BadRequestException` but `CannotTransformContentToTypeException` / `UnsupportedMediaTypeException` fall through to `catch (ex: Exception)` -> 500.
  (Same wrong-Content-Type on /auth/register returns 400 "Invalid request format", fine.)

### API-4: One recipe containing U+0000 poisons the whole push batch (409, partial commit, permanent retry loop)
- Severity: S2
- Status: CONFIRMED
- Steps: `POST /sync/push {"recipes":[good1, {title:"a\u0000b"...}, good2]}`.
- Expected: per-recipe error (e.g. INVALID_TITLE) in `errors[]`, others accepted.
- Actual: 409 `Sync push database conflict (traceId=...)`; good1 was committed, good2 was not (non-atomic), nothing is reported
  in `accepted`. Because the bad recipe stays dirty client-side, every subsequent push of that user's batch fails the same way (poison pill).
  A 409 is also the wrong status for a malformed value.
- Suspected cause: Postgres rejects NUL in text; `ExposedSQLException` is mapped to 409 in `SyncRoutes.kt`; no input sanitising in `SyncService`.
  Also reproducible with NUL in other text fields (description/instruction/unit) - only title tried.

### API-5: No request-size / field-length limits on sync push
- Severity: S3
- Status: CONFIRMED
- Steps: push one recipe with a 30 MB `description` (accepted in 0.9 s); a 1 MB title; 3000 recipes in one request (accepted, 26 s).
- Expected: bounded body size (413) and sane per-field limits.
- Actual: all accepted with 200. The 30 MB row is then returned on every pull of that user (a pull page of 100 recipes was 1 MB for tiny
  recipes). Easy storage/bandwidth DoS from any anonymous-registered account; also 3000-recipe batch holds a request for ~27 s.

### API-6: Sync push validation gaps (accepts nonsense values)
- Severity: S3
- Status: CONFIRMED
- Accepted with 200/accepted=1: negative ingredient quantity (-1), quantity 1e308, negative `servings` / prep time (-1/-5), duplicate step `orderIndex`
  (0,0), negative `orderIndex` (-3), empty title, `language:"EN"` (moot, see API-2), `updatedAt` negative or Long.MAX_VALUE
  (echoed on pull), `javascript:alert(1)` in `recipeExternalUrl` and `imageUrl`, tag/label ids that do not exist (silently dropped, not reported).
  `servings: 2^40` -> 400 (fine). Impact: junk in the library; `javascript:` URLs are a stored-XSS risk for any web UI rendering them as links
  (`/recipes-ui` is served by this backend - not checked).

### API-7: Replaying an identical push is reported as a SERVER_NEWER conflict, not an idempotent accept
- Severity: S3
- Status: CONFIRMED
- Steps: push recipe R (updatedAt=T); push the exact same payload again (e.g. response lost / retry).
- Expected: idempotent (accepted again or silently ignored).
- Actual: `conflicts:[{reason:"SERVER_NEWER", serverVersion:{...}}]`, because LWW compares the client's `updatedAt` with the server's
  receive time (`server_updated_at`), which is always later than T. Same for meal plans (`mealPlans.conflicts:[uuid]`). Consequence: any
  client whose clock lags the server (or any retry) has edits and delete tombstones rejected as "server newer" and then overwritten by the pull
  (observed: delete with updatedAt = create time + 10 ms -> conflict; delete at +5 s -> accepted). Android treats conflict as "pull will overwrite", so a
  lost response can cost the user's pending local edit if it is also older than the server receive time.

(Correction to API-1 cause: current backend source already has `millisecondPrecisionNow()` (SyncClock.kt) so rows written by the
sync API after that fix are ms-precision. The stuck rows are the µs-precision seed rows already in the table (and in any DB seeded the same
way); they need a data fix such as `UPDATE recipes SET server_updated_at = date_trunc('milliseconds', server_updated_at)`, or a cursor
that compares on the truncated value. Check production's `recipes.server_updated_at` for sub-ms values.)

### API-8: Sync pull with huge `since` answers 500
- Severity: S2 (5xx on client input)
- Status: CONFIRMED
- Steps: `GET /sync/pull?since=9223372036854775807` (also `99999999999999999`).
- Expected: 400 (or empty page).
- Actual: 500 `{"message":"Sync pull database error (traceId=...)"}`. Log: `PSQLException: timestamp out of range: "292278994-08-17..."`.
- Suspected cause: `since` only checked `>= 0` in `SyncRoutes.kt`; `Instant.fromEpochMilliseconds(since)` overflows Postgres timestamp range in `PostgresSyncRepository.findDeltaRecipes`.
- Related minor: `limit=abc` silently becomes 100 (S4); `limit` above 500 silently capped (fine).

### API-9: `GET /recipes` (HTML view) returns 500 with empty body
- Severity: S3 (legacy web view; JSON variant works)
- Status: CONFIRMED
- Steps: `GET /recipes` with bearer and `Accept: text/html` (or no Accept header).
- Expected: HTML list. Actual: 500, `Content-Length: 0`. Log: Thymeleaf `Exception evaluating OGNL expression: "recipe.label"` /
  `NoSuchPropertyException: Recipe.label` in template `all-recipes` line 21.
- Suspected cause: `templates/thymeleaf/all-recipes.html` references a property removed from `domain.model.Recipe`. The route also returns the
  entire accessible library unpaginated (0.5 MB for a fresh user, 2.4 MB for a user with 3k recipes).

### API-10: Image upload validates magic bytes only - junk and polyglot files stored and served
- Severity: S3
- Status: CONFIRMED
- Steps: `PUT /recipes/{id}/image` with `Content-Type: image/png`, correct `X-Content-SHA256`, body = PNG signature + `<html><script>alert(1)</script></html>`
  (or a valid PNG with `<script>` appended; or `RIFF....WEBPxxxx` as image/webp). `GET /recipes/{id}/image`.
- Expected: reject undecodable images (422/415).
- Actual: 200, stored, and served back byte-for-byte with `Content-Type: image/png|webp`, no `X-Content-Type-Options: nosniff`.
  The app gets an undecodable "photo" (Coil failure). Not directly XSS (image content-type) but nosniff is missing on every response.
- Other upload checks that PASSED: SVG -> 415, missing `X-Content-SHA256` -> 422, wrong hash -> 422, 6 MB -> 413, empty -> 415, other user's recipe -> 404,
  unauthenticated -> 401, bad id -> 400, B cannot read/delete A's image (404). Header `X-Content-SHA256` is effectively mandatory although ADR-012 text calls it optional-looking.

### API-11: Authenticated-only image GET for anonymous callers
- Severity: S3
- Status: CONFIRMED on the running image; SUSPECTED mismatch with source
- Steps: `GET /recipes/{id}/image` with no Authorization header.
- Actual: 401 plain text. Source (`recipeImageServeRoutes`) is mounted under `authenticate("auth-jwt", optional = true)` and documents anonymous access to PUBLIC
  recipe photos; the running build does not allow it (likely the stale image, API-2). Not verified with a PUBLIC recipe that has an uploaded blob.

### API-12: Household / invite endpoints - information disclosure and missing limits
- Severity: S3
- Status: CONFIRMED
- (a) `POST /api/v1/households/{id}/invites {"inviteeEmail":"x"}` returns 404 `No user found for email x` vs 201 when the account exists -> any
  household owner can enumerate which emails have accounts (rate limit 20/h). Duplicate-registration 409 also echoes the email.
- (b) `POST /api/v1/households` accepts a 100,000-character name (no max length); PATCH likely the same (not tried).
- (c) Error messages echo internal ids ("Invite <uuid> is expired, revoked, or exhausted", user/household uuids) - not secrets but noisy.
- (d) Non-members get 403 "not an active member" for any household uuid, including random non-existent ones -> consistent, no existence leak (PASS).
- PASSED: one household per user (409), blank name 400, non-owner member cannot rename/delete/invite/remove (403), concurrent join of a single-use invite
  admits exactly one user (second gets 404), double join 409, bad token 404, blank/missing token 400, member sees shared plans/recipes, ex-member loses write access
  (MEAL_PLAN_NOT_ACCESSIBLE) and recipe visibility (404), owner leaving promotes the remaining member, pull emits tombstones for plans a user lost access to.

### API-13: Meal plan push accepts references to recipes the caller cannot see
- Severity: S3 (existence oracle for private recipe UUIDs; UUIDs are unguessable)
- Status: CONFIRMED on the running image
- Steps: B pushes a meal plan whose day `dinnerRecipeId` is A's PRIVATE recipe uuid (not shared via any household).
- Expected: `MEAL_PLAN_RECIPE_NOT_ACCESSIBLE` (the enum exists in source).
- Actual: accepted; with a random non-existent uuid the DB FK presumably fails differently (not tried). B's pull then returns the plan with the foreign recipe id but not the recipe.
  Android then cannot resolve the recipe (404 on detail) - a plan day with a dangling recipe.
- Suspected cause: stale image (validation present in source) - re-test after rebuilding.

### API-14: Logout / reuse semantics
- Severity: S3
- Status: CONFIRMED (behaviour), design question
- (a) Access token stays valid after `POST /auth/logout` until its 1 h expiry (`GET /api/v1/me` still 200).
- (b) Presenting a logged-out refresh token to `/auth/refresh` is treated as theft: 401 "Token reuse detected. All sessions have been terminated" and
  revokes every session of that user. Any stale client that refreshes once after the user logged out on that device (e.g. a retry racing the sign-out)
  signs the user out on all devices. (ADR/AuthService: only rotation within 30 s is graced; logout is explicitly ineligible.)
- Verified PASS: rotation + 30 s grace replay works, replay after grace revokes the whole chain (successor token dead too), tampered/alg=none/refresh-token-as-bearer all 401,
  `/auth/logout` is idempotent (204 for unknown tokens), missing field 400.

### API-15: Inconsistent error shapes / status codes
- Severity: S4
- 401 from the JWT challenge is `text/plain` "Token is not valid or has expired" (no JSON `{message}`, no `WWW-Authenticate`); 404 for unknown routes and
  wrong verbs (`GET /sync/push`, `PUT /sync/push`, `GET /auth/register`) is `text/plain` "404: Resource Not Found" instead of 405; 429 has an empty body
  (Retry-After is present, good); `/recipes/byId` 404 has an empty body; `/api/v1/recipes/search?q=<5000 chars>` -> 400 with empty body;
  wrong Content-Type on `/auth/*` -> 400 (should be 415); `/api/v1/meal-plans/generate` and search return 200 for `limit=0`.
- Duplicate usernames are allowed (`qaapi1` registered twice under different emails). Probably fine since email is the login key; flagging in case username is displayed as identity.
- No CORS headers and no OPTIONS handling (404) - fine for a native app, would block a browser client.

### API-16: Other users' email addresses are exposed to anyone who can see their public recipe - including unauthenticated callers
- Severity: S1 (security / PII)
- Status: CONFIRMED
- Steps: 1. User C (qa+api3@example.test) pushes a PUBLIC recipe. 2. With NO Authorization header:
  `curl localhost:8080/api/v1/recipes/<recipe uuid>` (uuids are enumerable via the unauthenticated `GET /api/v1/recipes/sitemap`).
  Same data in `/sync/pull` `creators[]` for any authenticated user whose pull includes the recipe.
- Expected: creators carry only public profile fields (displayName, avatarUrl), never `email`.
- Actual: `"creators":[{"uuid":"61205b35-...","displayName":"qaapi3","email":"qa+api3@example.test","avatarUrl":"",...}]`.
  `sitemap` (20 req/min per IP) + detail (30 req/10 s) lets an anonymous scraper harvest the email of every user that ever made a recipe public.
  `displayName` also defaults to the username, which is often a real name.
- Suspected cause: `SyncUser` DTO includes `email` (`SyncDtos.kt`), populated in `PostgresSyncRepository.collectCreators` (~line 476-484, falls back to email as displayName).
  The Android client needs only a stable `uuid` + display name to satisfy the `users` FK.

### API-17: PUBLIC -> PRIVATE change is never propagated to devices that already pulled the recipe
- Severity: S3 (privacy; offline-first copy persists)
- Status: CONFIRMED
- Steps: C pushes PUBLIC recipe; B pulls it (and bookmarks it); C pushes the same recipe with `privacy=PRIVATE` (newer updatedAt); B pulls again from its cursor.
- Expected: B receives a tombstone/removal (as it does when C soft-deletes: pull then returns the recipe with `deletedAt` set).
- Actual: B's pull returns nothing for that recipe; B keeps a stale full copy + bookmark forever, while detail/search/sitemap correctly 404/hide it.
- Suspected cause: delta query is `creator = me OR privacy = PUBLIC`, so a recipe that turned PRIVATE simply drops out of the result set with no tombstone.

### API-18: Concurrent duplicate registration returns 400 "Invalid request format"; password hash written to server log
- Severity: S3
- Status: CONFIRMED
- Steps: fire 8 parallel `POST /auth/register` with the same email. Result: 1x 201, 7x `400 {"message":"Invalid request format"}` (expected 409).
- Cause: the existence pre-check races; the unique-index violation (`users_email_key`) surfaces as `ExposedSQLException`, caught by the generic `catch (ex: Exception)` -> 400
  in `AuthRoutes.kt` (`/register`, and `/login`, `/refresh` have the same catch-all -> 400 for server faults).
- Also: Exposed's `Transaction attempt #0 failed` WARN logs the full failing SQL, which includes the bcrypt `password_hash` and the e-mail address
  (`INSERT INTO users (... password_hash ...) VALUES (... '$2a$12$...' ...)`). Hashes + PII in logs; same would apply to any failed user insert.

## Coverage notes
Done (black-box, local backend): register/login/refresh/logout, JWT tamper/alg=none/wrong-type, refresh rotation + grace + reuse, auth + search + generate + join rate limits (429 shape,
X-Forwarded-For spoof does not bypass), password/username/e-mail validation incl. unicode/case/whitespace, recipe push (LWW, tombstone, resurrect, replay, validation matrix, oversize, bad
JSON, wrong CT, concurrent same-uuid pushes), pull edge cases, cross-user write/read/delete attempts on recipes, plans, grocery checks, bookmarks, images (all rejected correctly except noted),
households (create/invite/join/leave/remove/roles/race), anonymous endpoints (home layout + ETag, search, detail, sitemap, preferences), image upload matrix, SQL-ish/special-char search input.
PASS highlights: no IDOR found on recipes/plans/grocery/bookmarks/images; creatorId cannot be spoofed; no stack traces or SQL in responses (only traceIds); concurrent same-recipe pushes stay consistent
(children never mixed between versions, no 5xx); single-use invite race admits exactly one.
NOT tested / limits: cook events (ADR-017) and saved meal plans (ADR-018) - unsupported by the running image (API-2); JWT expiry (cannot mint/age tokens without the secret); account upgrade
(anonymous -> account) is client-only (server has no anonymous sessions; pushing a recipe with a foreign `creatorId` is CREATOR_MISMATCH, as designed); `/recipes-ui` static web UI; brute-force
beyond the 20/min limit; production-scale pull timing; recipe `PATCH`-style endpoints (none exist). Throwaway data left in the local DB: users qa+api1..7@example.test, ~3,000 bulk recipes
for qa+api1, one household "QA Home".
