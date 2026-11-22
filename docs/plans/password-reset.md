# Password reset by email — plan + execution prompts

Status: draft, Nov 2026. Email provider set up 2026-11-22 (see "Human prerequisites"). Three repos: backend `jmuci/Chef.ai_Backend` (`~/Projects/ktor-chefai`),
Android (this repo), web `jmuci/chefai-web` (`~/Projects/chefai-web`).

## 1. Shape of the feature

```
User taps "Forgot password?" (Android or web)
  → POST /auth/password-reset/request {email}            ← always 202, no account enumeration
  → backend emails https://pocket-chef.app/reset-password?token=<raw>   (30 min, single use)
User taps the link
  → phone with the app: App Link opens Android ResetPasswordScreen
  → anywhere else:      web /reset-password page
  → POST /auth/password-reset/validate {token}            ← 200 / 410, so the UI can say "expired" up front
  → POST /auth/password-reset/confirm  {token, newPassword}
       backend: new bcrypt hash, token marked used, every refresh token revoked,
                access tokens issued before the change rejected, "password changed" email sent
  → client sends the user to sign-in (no auto-login)
```

One link, two clients: the backend doesn't need to know which client the user has. The web
already serves `public/.well-known/assetlinks.json` for `pocket-chef.app`, and verification is per
host, so adding a `/reset-password` path to Android's existing `autoVerify` filter needs no
change to that file.

## 2. Decisions to confirm before starting

Each has a recommendation; the prompts below assume it. Change the prompt if you choose otherwise.

| # | Decision | Recommendation | Tradeoff |
|---|----------|----------------|----------|
| D1 | **Email provider** — **decided: Resend**, domain `mail.pocket-chef.app` (us-east-1) | Resend's REST API, called with the backend's existing `ktor-client` — **no new Gradle dependency** (Resend's Java SDK was considered and not used for that reason). Behind a domain `EmailSender` interface so the provider is swappable. Email copy stays in Kotlin (versioned, tested), not in Resend-hosted templates. | SMTP via Jakarta Mail would be provider-neutral but adds a dependency and connection handling. AWS SES is cheapest at scale but needs IAM setup we don't have. |
| D2 | Token TTL / reuse | 30 min, single use; issuing a new one invalidates older unused ones | Shorter = safer, more "expired" support pain |
| D3 | Auto-login after reset | **No** — redirect to sign-in with the email prefilled where known | Auto-login saves a step but turns the emailed link into a login credential |
| D4 | Kill existing sessions | **Yes** — revoke all refresh tokens (new reason `PASSWORD_RESET`) and reject access tokens with `iat` before `users.password_changed_at` | `JwtConfig` already loads the user per request, so the `iat` check costs no extra query |
| D5 | Token in query string | Keep `?token=`, and mitigate: `Referrer-Policy: no-referrer`, `Cache-Control: no-store`, `noindex`, no third-party resources on the page, Caddy access log doesn't record query strings on that path | A `#fragment` never reaches servers but breaks the server-rendered web page and App Link parsing gets fiddlier |
| D6 | Per-email throttle | Max 3 requests / email / hour, counted from the tokens table (stateless) on top of the existing per-IP `auth` bucket | Throttled requests still return 202 so the throttle can't be used to probe accounts |
| D7 | Email language | English only for now (ADR-016 covers recipe language, not UI) | Localized emails later need a `locale` on the request |

## 3. API contract (shared by all three prompts)

All under the existing public, IP-rate-limited `/auth` route (`AUTH_RATE_LIMIT_NAME`).
Error body is the backend's existing `ErrorResponse`, plus a machine-readable `code`.

| Endpoint | Body | Responses |
|----------|------|-----------|
| `POST /auth/password-reset/request` | `{ "email": "a@b.com" }` | `202` always for a well-formed email (unknown email, throttled, and send failures included). `400 VALIDATION_ERROR` for malformed body/email. `429` from the IP bucket. |
| `POST /auth/password-reset/validate` | `{ "token": "..." }` | `204` valid. `410 RESET_TOKEN_INVALID` for unknown, expired or used (one code: don't tell an attacker which). `400` malformed. |
| `POST /auth/password-reset/confirm` | `{ "token": "...", "newPassword": "..." }` | `204` done. `400 VALIDATION_ERROR` (password rules from `InputValidator.validatePassword`, min 8 chars, message is user-presentable). `410 RESET_TOKEN_INVALID`. |

Token: 32 random bytes from `SecureRandom`, base64url without padding (43 chars). Only the
SHA-256 hash is stored. The raw token never appears in logs on any of the three sides.

## 4. Sequencing and who runs what

```
Human prereqs (H1–H4) ──┐
BE-1 → BE-2 → BE-3 → BE-4 ──► deploy BE ──► ship Android + web
                │
                └── contract frozen after BE-2 merges
                         ├── AND-1 → AND-2 → AND-3 → AND-4   (against fakes, parallel)
                         └── WEB-1 → WEB-2 → WEB-3           (against fake-ktor, parallel)
XS-1 cross-stack QA once all three are on staging/local
```

Model column: **Sonnet 5.5** for everything mechanical, since the decisions are already made
above. **Opus** only where judgment on security edge cases matters (BE-3) and for the final
review passes. Each task is one PR.

| Task | Repo | Model | Depends on |
|------|------|-------|-----------|
| H1–H4 human setup | — | you | — |
| BE-1 email sender + templates | backend | Sonnet 5.5 | — |
| BE-2 token storage + request/validate endpoints | backend | Sonnet 5.5 | BE-1 |
| BE-3 confirm + session invalidation | backend | **Opus** | BE-2 |
| BE-4 docs, smoke test, security review | backend | Sonnet 5.5 (+ `/security-review`) | BE-3 |
| AND-1 data layer | Android | Sonnet 5.5 | contract |
| AND-2 Forgot password screen | Android | Sonnet 5.5 | AND-1 |
| AND-3 App Link + Reset password screen | Android | Sonnet 5.5 | AND-1 |
| AND-4 Maestro flow + docs | Android | Sonnet 5.5 | AND-2, AND-3, BE deployed locally |
| WEB-1 fake-ktor + API client | web | Sonnet 5.5 | contract |
| WEB-2 pages + Server Actions | web | Sonnet 5.5 | WEB-1 |
| WEB-3 e2e + hardening | web | Sonnet 5.5 | WEB-2 |
| XS-1 cross-stack QA + review | all | Opus (review) / you (device) | everything |

### Human prerequisites (not delegable)

- **H1 — done 2026-11-22.** Resend account; domain `mail.pocket-chef.app` (id
  `8d703283-cae9-4b4e-a321-5f90d31868ed`, us-east-1, TLS enforced, click/open tracking off)
  verified. Cloudflare records: DKIM TXT `resend._domainkey.mail`, MX + SPF TXT `send.mail`
  (return path), CNAME `rsend.mail` (DNS only), and DMARC TXT `_dmarc` = `v=DMARC1; p=none;` on
  the root. Tighten DMARC to `p=quarantine` after a few weeks of clean reports.
  Sender: `Pocket Chef <no-reply@mail.pocket-chef.app>`.
- **H2 — key created, server write pending (you).** Sending-only key `pocket-chef-backend-prod`
  restricted to that domain. Goes in the VPS's `/home/ubuntu/chefai/.env` (created by
  `deploy/deploy.sh`; there is no `.env` in the repo) as `EMAIL_API_KEY`, plus
  `EMAIL_FROM="Pocket Chef <no-reply@mail.pocket-chef.app>"`. `PASSWORD_RESET_BASE_URL` can stay
  unset (the default is right). Never put this key on a non-prod host; dev uses
  `LoggingEmailSender`.
- **H3** Confirm `https://pocket-chef.app/.well-known/assetlinks.json` is live with the release
  signing fingerprint (it is also what makes `/invite` App Links work).
- **H4** Check the Caddy access log format doesn't record query strings for `/reset-password`
  (D5).

---

## 5. Prompt — Backend (`~/Projects/ktor-chefai`)

> Run as four sessions/PRs (BE-1…BE-4). Paste the shared part plus one task section each time.

```text
Repo: ~/Projects/ktor-chefai (jmuci/Chef.ai_Backend). Read CLAUDE.md, docs/auth-architecture.md
and docs/exception-handling.md first. Branch off origin/main.

We're adding "forgot password" by email. The design is settled; implement it, don't redesign it.
If something below conflicts with the code, stop and tell me rather than improvising.

API contract (public, under route("/auth"), inside rateLimit(AUTH_RATE_LIMIT_NAME)):
- POST /auth/password-reset/request {email} → 202 always for a well-formed email (unknown email,
  per-email throttle hit and email-send failure all still 202). 400 for malformed input.
- POST /auth/password-reset/validate {token} → 204 valid; 410 code RESET_TOKEN_INVALID for
  unknown/expired/used (same code for all three); 400 malformed.
- POST /auth/password-reset/confirm {token, newPassword} → 204; 400 VALIDATION_ERROR using
  InputValidator.validatePassword's message; 410 RESET_TOKEN_INVALID.
Error bodies use the existing ErrorResponse; add a `code` field if it doesn't have one, without
breaking existing callers (default value).

Rules that apply to every task:
- Clean Architecture layers as in CLAUDE.md; domain has no Ktor/Exposed imports.
- No new Gradle dependencies. The email provider is called with the existing ktor-client-cio.
- Never log a raw reset token, a password, or a full email address (mask: j***@gmail.com).
- Normalize the email exactly as AuthService.login does before looking the user up.
- Inject a Clock (kotlinx.datetime) wherever time matters, so tests can control it.
- Tests: unit tests with fakes in src/test/kotlin/domain/service/, route integration tests in
  src/test/kotlin/infrastructure/auth/ with testApplication + fake repos, as the existing
  AuthIntegrationTest does. Run ./gradlew test and report pass/fail counts. Don't delete tests.
```

### BE-1 — Email sender (Sonnet 5.5)

```text
Task BE-1: an outbound email capability, nothing reset-specific beyond the templates.

1. domain: `interface EmailSender { suspend fun send(message: EmailMessage) }`,
   `data class EmailMessage(to, subject, textBody, htmlBody)`, and a sealed
   `EmailSendException` for failures (never generic Exception).
2. infrastructure: `ResendEmailSender` (HTTPS POST https://api.resend.com/emails, bearer
   EMAIL_API_KEY, from EMAIL_FROM) using a dedicated HttpClient with a 10 s request timeout.
   Send an `Idempotency-Key` header (EmailMessage carries one; callers pass a stable id, e.g.
   the reset token's row uuid). Retry up to 3 times with exponential backoff on IO errors, 5xx
   and 429 (honour Retry-After); never retry other 4xx. Log the Resend email id from the 2xx
   response. Map final failures to EmailSendException; log status code only, never the body.
   `LoggingEmailSender` for development: logs recipient (masked) and subject, NOT the body,
   and keeps the last N messages in memory behind a dev-only GET /dev/outbox route that is
   registered only when ktor development mode is on — local Android/web testing reads the reset
   link from there.
3. Config in application.yaml: `email: { apiKey: "$EMAIL_API_KEY:", from: "$EMAIL_FROM:Pocket Chef <no-reply@mail.pocket-chef.app>" }`.
   Startup: in production mode a blank apiKey fails startup (same pattern as resolveJwtSecret);
   in development mode it selects LoggingEmailSender. Add both vars to deploy/.env.example, and
   pass them into the backend container in deploy/docker-compose.prod.yaml's `environment:`
   block next to JWT_SECRET (`EMAIL_API_KEY: ${EMAIL_API_KEY:?...}` like the other required
   secrets; `EMAIL_FROM: ${EMAIL_FROM:-}`). The production .env on the VPS already has both.
4. Templates: `PasswordResetEmail.build(link, ttlMinutes)` and `PasswordChangedEmail.build()`
   as plain Kotlin string templates (text + minimal inline-styled HTML, no external images).
   Copy: link, "expires in 30 minutes", "if you didn't ask for this, ignore this email — your
   password hasn't changed". Escape anything interpolated into HTML.
5. Tests: ResendEmailSender with Ktor MockEngine (request shape, auth + Idempotency-Key
   headers, 2xx, 4xx not retried, 5xx/429 retried then succeeding, retries exhausted and
   timeout → EmailSendException); template tests (link present in both bodies, HTML-escaped);
   /dev/outbox absent when development mode is off.
```

### BE-2 — Tokens + request/validate (Sonnet 5.5)

```text
Task BE-2: storage and the first two endpoints. BE-1 is merged.

1. Table `password_reset_tokens` (snake_case): uuid (UUIDv7, as other tables), user_id FK →
   users ON DELETE CASCADE, token_hash (unique, SHA-256 hex), created_at, expires_at, used_at
   (nullable), invalidated_at (nullable). Index on (user_id, created_at). Add it to
   DatabaseInit's createMissingTablesAndColumns list and mirror it by hand in
   sql/create_tables.sql, as the file's comments describe.
2. domain: `PasswordResetTokenRepository` (create, findByHash, countCreatedSince(userId, since),
   invalidateUnusedForUser, markUsed(id, now): Boolean — atomic, true only for the call that
   flips it). Exposed implementation in infrastructure + a Fake for tests.
3. `PasswordResetService` (domain/service):
   - requestReset(email): validate format (InputValidator) → find user. Unknown user: return.
     If ≥3 tokens created for this user in the last hour: return. Otherwise invalidate older
     unused tokens, create a new one (TTL 30 min, from config `passwordReset.tokenTtlMinutes`),
     build the link `${passwordReset.baseUrl}?token=<raw>` (config
     `"$PASSWORD_RESET_BASE_URL:https://pocket-chef.app/reset-password"`) and send the email.
   - Timing: the route must not take observably longer for a real account. Launch the
     lookup+send on an application-owned CoroutineScope (SupervisorJob, cancelled on
     ApplicationStopping — document the owner in KDoc) and respond 202 immediately. An email
     failure is logged and swallowed there, never surfaced to the caller.
   - validate(token): hash, look up; unknown / expired / used / invalidated → throw
     ResetTokenInvalidException.
4. Routes in AuthRoutes.kt, try/catch shape matching the existing routes. Malformed JSON → 400.
5. docs/exception-handling.md: RESET_TOKEN_INVALID.
6. Tests:
   - service unit: unknown email sends nothing; known email sends one email whose link carries
     a token whose hash is stored; 4th request in an hour sends nothing; new request invalidates
     the previous token; expired / used / invalidated / unknown all throw the same exception;
     email-send failure doesn't propagate.
   - integration: request returns 202 for known and unknown email with identical bodies;
     400 for "not-an-email"; validate 204 / 410; IP rate limit still applies to the new routes;
     raw token not in captured logs (use a log appender as other tests do, if any; otherwise
     assert on the masked-logging helper).
```

### BE-3 — Confirm + session invalidation (Opus)

```text
Task BE-3: the confirm endpoint and making the reset actually end other sessions. BE-2 merged.

1. users.password_changed_at (timestamp, nullable, default null). DatabaseInit + create_tables.sql.
2. PasswordResetService.confirm(token, newPassword):
   validate password first (InputValidator.validatePassword → ValidationException), then in ONE
   transaction: look up token, check not expired/used/invalidated, markUsed (atomic — if it
   returns false a concurrent confirm won: throw ResetTokenInvalidException), set the new bcrypt
   hash (same cost as registration), set password_changed_at = now, invalidate the user's other
   unused reset tokens, revoke every live refresh token with a new
   TokenRevocationReason.PASSWORD_RESET. After commit, send PasswordChangedEmail on the
   app-owned scope (failure logged, not surfaced).
3. JwtConfig: it already loads the user per request. Reject (401) an access token whose `iat`
   is before password_changed_at (second precision; make sure tokens issued in the same second
   as the change, i.e. a login right after reset, still pass — compare against the truncated
   value and test it). Confirm JwtService sets `iat`; add it if it doesn't.
4. AuthService.refreshToken: a token revoked with PASSWORD_RESET must NOT get the rotation
   grace path and must NOT trigger reuse detection's "revoke everything" side effect (already
   revoked) — return the normal invalid-refresh-token 401.
5. Route POST /auth/password-reset/confirm.
6. Tests (unit + integration):
   - happy path: old password login fails (401), new password login works.
   - every refresh token issued before the reset → 401; access token issued before → 401 on
     /api/v1/me; access token from a login after the reset → 200.
   - token reuse after success → 410; two concurrent confirms with the same token → exactly one
     204 (use the fake's atomic markUsed, and one Postgres-backed test if Testcontainers is
     already set up in this repo).
   - weak password → 400 and the token is still usable afterwards.
   - expired token (advance the injected Clock past 30 min) → 410.
   - PasswordChangedEmail sent once on success, not on failure.
   - deleted account between request and confirm → 410 (FK cascade removes the token).
```

### BE-4 — Docs, smoke test, review (Sonnet 5.5, then `/security-review`)

```text
Task BE-4: BE-1..3 are merged.
1. docs/auth-architecture.md: a "Password reset" section with a sequence diagram like the
   existing ones, the token lifecycle, the session-invalidation rules (refresh revoke + iat
   check), the anti-enumeration and throttle behaviour, and the D5 query-string mitigations.
   docs/auth-quick-start.md: the three endpoints with curl examples.
2. smoke-test.sh: request a reset for the smoke user against the local dev server, read the
   link from /dev/outbox, validate, confirm, log in with the new password.
3. README: EMAIL_API_KEY / EMAIL_FROM / PASSWORD_RESET_BASE_URL.
4. Run ./gradlew test and ./smoke-test.sh, report results. Then run /security-review on the
   combined diff of BE-1..3 and list findings; don't fix them without asking me.
```

---

## 6. Prompt — Android (this repo)

```text
Repo: ChefAI Android (this repo). Read CLAUDE.md, docs/authentication.md and the skills
.claude/skills/compose-component.md and .claude/skills/viewmodel.md first.

We're adding "forgot password" by email. Backend contract (may not be deployed yet — build
against fakes; local backend in dev mode exposes GET /dev/outbox with the last emails):
- POST /auth/password-reset/request {email} → 202 always for a well-formed email; 400; 429.
- POST /auth/password-reset/validate {token} → 204 valid; 410 {code: RESET_TOKEN_INVALID}.
- POST /auth/password-reset/confirm {token, newPassword} → 204; 400 (message is
  user-presentable, password min 8 chars); 410 RESET_TOKEN_INVALID.
The email links to https://pocket-chef.app/reset-password?token=<43-char base64url>.
After a reset the server has revoked every refresh token, so a signed-in device is signed out on
its next refresh by the existing refresh-rejection handling — don't add anything for that.

Rules: MVI (sealed interface UiState / UiAction, single onAction lambda), Hilt, StateFlow,
stateless composables with light + dark previews, no `!!`, strings in strings.xml, never log or
Timber the token or the password, no new dependencies. Run ./gradlew :app:testDebugUnitTest at
the end of each task and report counts.
```

### AND-1 — Data layer (Sonnet 5.5)

```text
Task AND-1:
1. auth/domain/repository/PasswordResetRepository: requestReset(email), validateToken(token),
   confirmReset(token, newPassword), each returning a sealed result
   (e.g. PasswordResetResult: Success | InvalidToken | Validation(message) | RateLimited |
   Network | Unknown). No exceptions cross into the domain.
2. auth/data/network: add the three calls to the existing AuthApiService / AuthNetworkDataSource
   style (same Ktor client and base URL as /auth/login; these are unauthenticated so make sure
   AuthInterceptor's behaviour doesn't matter either way). DTOs in auth/data/network/dto.
3. DefaultPasswordResetRepository in auth/data/repository, Hilt binding next to the existing
   auth bindings.
4. Tests: data source with Ktor MockEngine for every status in the contract plus timeout and
   malformed body; repository mapping tests; a FakePasswordResetRepository in test/ (and a copy
   in androidTest/ if AND-2/3's UI tests need it).
```

### AND-2 — Forgot password screen (Sonnet 5.5)

```text
Task AND-2 (AND-1 merged):
1. LoginScreen: a "Forgot password?" text button under the password field →
   LoginUiEvent/Action → navigate to ForgotPassword, passing the email typed so far.
2. auth/ui/forgot/ForgotPasswordScreen + ViewModel. States: Editing(email, error?) →
   Submitting → Sent(email) (copy: "If an account exists for <email>, we've sent a link. It
   expires in 30 minutes. Check spam.") with a "Resend" button disabled for 60 s (countdown
   driven by an injectable time source, not delay() in the composable) and "Back to sign in".
   Client-side email format check before calling. 429 → "Too many attempts, try again later".
   Network → retryable error. Never reveal whether the account exists.
3. Nav route in ChefAINavGraph next to Login/Register.
4. Tests: ViewModel (Turbine) for each transition, the resend cooldown with a test clock, email
   prefill; Compose UI test: type email → submit → sent state visible → resend disabled.
```

### AND-3 — App Link + Reset password screen (Sonnet 5.5)

```text
Task AND-3 (AND-1 merged):
1. AndroidManifest: add `<data android:pathPrefix="/reset-password" />` to the existing
   autoVerify https://pocket-chef.app intent filter (same host as /invite; assetlinks.json is
   per host so it already covers this). Keep the existing comment accurate.
2. core/util: ResetPasswordLinkParser modelled on InviteLinkParser — accepts only https,
   host pocket-chef.app, path exactly /reset-password, a token matching ^[A-Za-z0-9_-]{43}$;
   everything else → null. MainActivity routes a parsed link (cold start and onNewIntent) to the
   ResetPassword destination, the same way invite links are routed. Don't persist the token
   anywhere except the destination's SavedStateHandle.
3. auth/ui/reset/ResetPasswordScreen + ViewModel. On entry: Validating → (410) Expired with
   "Request a new link" → ForgotPassword, or → Editing(new, confirm, show/hide, inline errors:
   min 8 chars, must match) → Submitting → Done ("Password updated. Sign in with your new
   password.") → Login. Server 400 shows its message; 410 on confirm → Expired.
   Works whether the user is anonymous or signed in; if signed in, Done also says other devices
   were signed out.
4. Tests: parser table test (valid, wrong host, http, extra path, short/long/invalid-char
   token, missing token); ViewModel tests for every state transition; Compose UI tests for
   Expired and Editing → Done; an instrumented test that launches MainActivity with a
   reset-password VIEW intent and asserts the screen shows (fake repository via the androidTest
   TestAuthModule — remember to add the new binding there, see the saved-plans gotcha in
   .claude/session-context.md).
```

### AND-4 — Maestro + docs (Sonnet 5.5)

```text
Task AND-4 (AND-2/3 merged, local backend running in development mode):
1. .maestro/auth/password-reset.yaml: register a throwaway user, sign out, Forgot password,
   submit, fetch the link from http://10.0.2.2:8080/dev/outbox (Maestro runScript/http), open it
   with `openLink`, set a new password, sign in with it. Follow the existing .maestro conventions.
   Check the build's API target first (debug defaults to local; a persisted Developer-options
   REMOTE switch overrides it).
2. docs/authentication.md: a "Password reset" section; docs/feature-status.md entry; Current Gaps
   row in CLAUDE.md. Update .claude/session-context.md.
3. Report unit, instrumented and Maestro results.
```

---

## 7. Prompt — Web (`~/Projects/chefai-web`)

```text
Repo: ~/Projects/chefai-web (Next.js 16 App Router, React 19, Tailwind 4). Read AGENTS.md,
README.md and src/lib/auth/ first. Branch off origin/main.

We're adding "forgot password" by email. Backend contract:
- POST /auth/password-reset/request {email} → 202 always for a well-formed email; 400; 429.
- POST /auth/password-reset/validate {token} → 204; 410 {code: RESET_TOKEN_INVALID}.
- POST /auth/password-reset/confirm {token, newPassword} → 204; 400 (user-presentable
  message, min 8 chars); 410.
The backend emails https://pocket-chef.app/reset-password?token=<43-char base64url>. On a phone
with the Android app the link opens the app; everywhere else it opens this site.

Rules: all Ktor calls go through src/lib/api/client.ts server-side (so CHEFAI_PROXY_SECRET
forwarding and per-visitor rate limits apply); Server Actions for forms, as signIn/signUp do;
no new npm dependencies; never log the token or password; reuse AuthForm/AuthPage styling and
the design tokens in globals.css. Run npm test, npm run lint, npm run typecheck and
npm run test:e2e at the end of each task and report results.
```

### WEB-1 — Fake backend + API layer (Sonnet 5.5)

```text
Task WEB-1:
1. e2e/fake-ktor.mts: implement the three endpoints with the real semantics (token TTL, single
   use, older tokens invalidated, refresh tokens of that user revoked on confirm, 202 for unknown
   emails) plus a test-only GET /__test/outbox returning the last reset link per email, and a
   way to expire a token (e.g. POST /__test/expire-reset-tokens).
2. src/lib/auth (or src/lib/api/password-reset.ts): requestPasswordReset, validateResetToken,
   confirmPasswordReset returning discriminated unions, never throwing on expected statuses.
3. Vitest for the mapping of every status (mock fetch the way existing *.test.ts files do).
```

### WEB-2 — Pages + Server Actions (Sonnet 5.5)

```text
Task WEB-2 (WEB-1 merged):
1. Login page: "Forgot password?" link → /forgot-password?email=<typed email> (only if it's
   already in the form; don't add a query param otherwise).
2. /forgot-password: form → Server Action → always shows the same "If an account exists…"
   state (30-minute expiry, check spam), with a resend button. 429 → friendly message.
3. /reset-password?token=…: server component validates first. Missing/malformed token
   (regex ^[A-Za-z0-9_-]{43}$) or 410 → "This link has expired or was already used" + link to
   /forgot-password. Valid → new password + confirm form (token in a hidden input), client-side
   min-length/match hints, Server Action re-checks and calls confirm. On 204: clear this
   browser's session cookies (the backend revoked them anyway) and redirect to
   /login?reset=1, which shows "Password updated, sign in with your new password".
4. Hardening for both pages: `Referrer-Policy: no-referrer`, `Cache-Control: no-store`,
   `robots: noindex` metadata, no third-party scripts/images on /reset-password. Make sure
   src/proxy.ts doesn't try to refresh a session on /reset-password in a way that would fail
   the page for a signed-in user whose tokens are being revoked.
5. Vitest for the Server Actions (validation, each backend result → UI state, cookies cleared
   only on success).
```

### WEB-3 — End-to-end (Sonnet 5.5)

```text
Task WEB-3 (WEB-2 merged). Playwright specs in e2e/password-reset.spec.ts against fake-ktor:
- happy path: register → sign out → forgot password → read link from /__test/outbox → reset →
  old password fails → new password signs in.
- signed-in browser resets its own password → ends up signed out → signs in again.
- unknown email shows the identical confirmation as a known one.
- expired token, reused token, malformed token → expired page with working "request new link".
- mismatched / short password → inline errors, nothing sent.
- response headers on /reset-password include no-referrer and no-store.
Tag the happy path @smoke. Update README's test table and docs/roadmap.md.
```

---

## 8. XS-1 — Cross-stack verification

Run once BE is deployed locally (development mode) and both clients point at it. Opus does the
review half; the device half is yours.

1. **Review (Opus):** read the three merged diffs side by side against §3. Check the status
   codes and error codes match, the token regex is identical everywhere, no client logs the
   token, and every "expired" path leads back to request-a-new-link.
2. **Manual matrix (you):**

   | Start | Link opened on | Expected |
   |-------|----------------|----------|
   | Android app | same phone, app installed | App Link → app Reset screen (no chooser dialog) |
   | Android app | desktop browser | web reset page, then sign in on the app |
   | Web | phone with app | app Reset screen |
   | Web | phone without app | web reset page |
   | Any | link opened twice | second time: expired page |
   | Signed in on 2 devices | reset on one | the other is signed out within one refresh |
   | Any | after 30 min | expired page |

3. **Email deliverability (you, staging/prod key):** arrives in Gmail and Outlook inbox, not
   spam; SPF/DKIM/DMARC pass in the headers; the link isn't rewritten by click tracking (already
   off on the Resend domain; keep it off, it would route the token through their redirector).
4. Ship order: backend → web → Android release.

## 9. Out of scope / follow-ups

- Change password while signed in (needs current password; reuses BE-3's invalidation).
- Email verification at registration — right now anyone can register someone else's address,
  which then receives reset emails meant for an account it didn't create.
- Localized email copy (D7).
- Account-recovery for users who lost access to their email.
