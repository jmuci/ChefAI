# Sign in with Google — implementation plan

**Design:** [ADR-019](../../adrs/adr-019-sign-in-with-google.md). It is the source of truth; if this
plan and the ADR disagree, stop and ask.
**Repos:** Android `jmuci/ChefAI` (this repo), backend `jmuci/Chef.ai_Backend`.
**Written against:** Android `7775d9b9`, backend `bc1f3eb` (Nov 2026).
**Executor:** one Sonnet sub-agent per task below. Each task is self-contained: it names the files,
the steps, the tests and what "done" means.

---

## How to run this plan

- **One task = one sub-agent = one branch = one PR.** Branch names are given per task.
- **Order** (arrows are hard dependencies; anything on the same line can run in parallel):

  ```
  G0 (human, Google Cloud)          ─────────────────────────────────────────────┐
  B1 ──► B2 ──► B3 ──► B4 ──► B5                                                  │
  A1 ──► A2 ──► A3 ──► A4 ──► A5 ──► A6                                           ├─► V (verify on device)
          (A3+ code against the API contract below, with fakes — no backend needed) │
  R  (password reset, separate work) ──────────────────────────────────────────────┘ ─► release
  F  (nonce) — follow-up, NOT started without the user's sign-off
  ```

  Backend and Android tracks run in parallel. A1 can start immediately; it is a pure refactor.
- **Release gate:** Google sign-in does not reach users before password reset (R, being built
  separately with Resend) is live. ADR-019 Decision 3 explains why.

### Rules every sub-agent follows

1. Read the repo's `CLAUDE.md` first, then ADR-019, then only the files your task names.
2. **Dependencies:** the only new artifacts allowed are the three Android ones in A2. If you
   believe you need anything else, stop and report — do not add it.
3. **No public API changes** beyond the contract below. A field or endpoint not in the contract →
   stop and report.
4. Kotlin rules: no `!!`, explicit return types on public functions, sealed interfaces for
   state/results, no `GlobalScope`, no Context in ViewModels.
5. Follow the surrounding code's existing patterns (e.g. `LoginViewModel` uses one function per
   user action, not a single `onAction`; keep it that way in this feature).
6. **Tests:** write them as part of the task, Given/When/Then, fakes over mocks. Run:
   - Android: `./gradlew :app:testDebugUnitTest`
   - Backend: `./gradlew test` (Testcontainers tests need Docker; if it's unavailable, say so in
     the report rather than skipping silently)
7. **Never log** a Google ID token, a refresh token or a password, at any level.
8. **Report back** with: branch, files changed, tests added, test results (counts), anything you
   could not do or verify, and any deviation from this plan with the reason.

---

## API contract (both tracks code against this)

All error bodies use the backend's existing `ErrorResponse { message, code? }`.

### `POST /auth/google` — public, in the `AUTH_RATE_LIMIT_NAME` bucket

```json
// request
{ "idToken": "<Google ID token JWT>" }
```

| Status | Body | When |
|--------|------|------|
| 200 | `AuthResponse` (same as `/auth/login`) | known Google identity, or new user created |
| 400 | `ErrorResponse` | missing/blank `idToken`, malformed body |
| 401 | `ErrorResponse(code = "INVALID_GOOGLE_TOKEN")` | signature, `iss`, `aud`, `exp` or `email_verified` check fails |
| 409 | `ErrorResponse(code = "GOOGLE_EMAIL_EXISTS")` | email belongs to an account with no Google identity |

### `POST /auth/google/link` — authenticated (JWT), in the auth rate-limit bucket

```json
{ "idToken": "<Google ID token JWT>" }
```

| Status | Body | When |
|--------|------|------|
| 204 | — | identity linked, or already linked to this same user (idempotent) |
| 401 | `ErrorResponse(code = "INVALID_GOOGLE_TOKEN")` | token fails verification |
| 409 | `ErrorResponse(code = "GOOGLE_EMAIL_MISMATCH")` | token email ≠ signed-in account's email |
| 409 | `ErrorResponse(code = "GOOGLE_IDENTITY_TAKEN")` | that Google `sub` already belongs to another user |

### `POST /api/v1/me/delete` — gains an alternative proof

```json
{ "password": "…", "publicRecipes": "…" }          // existing
{ "googleIdToken": "…", "publicRecipes": "…" }     // new
```

Exactly one of `password` / `googleIdToken` must be present (400 otherwise). A Google token must
pass verification, its `sub` must be one of the user's identities, and its `iat` must be within the
last 5 minutes; failure → 401 (`code = "INVALID_GOOGLE_TOKEN"`).

### Auth response — sign-in methods

`AuthResponse` gains `"hasPassword": Boolean` and `"providers": ["google", …]`, both with
defaults so older clients ignore them. Android uses them to choose the deletion confirmation and to
show "Connect Google".

---

## G0 — Google Cloud setup (human, not an agent)

Needed before V (device test); not needed for any code task.

1. Google Cloud project → **OAuth consent screen**: External, scopes `openid`, `email`, `profile`
   only; publish to **In production** (in Testing, only listed test users can sign in).
2. **Credentials → OAuth client ID → Web application.** Its client ID is the Android
   `serverClientId` *and* the backend's expected `aud`.
3. **Credentials → OAuth client ID → Android**, one per signing certificate, package
   `com.tenmilelabs.chefai`:
   - debug keystore SHA-1 (`./gradlew :app:signingReport`)
   - upload key SHA-1
   - **Play App Signing key SHA-1** (Play Console → App integrity). Missing this one = works locally,
     fails for every Play install.
4. Put the Web client ID in `~/.gradle/gradle.properties` as `chefaiGoogleWebClientId=…` and in the
   backend's deploy config as `google.webClientId`.

---

## Backend track (`jmuci/Chef.ai_Backend`)

### B1 — Schema: nullable password, `user_identities` table, null-password hardening

Branch `google-signin/b1-schema`.

**Why first:** once any user has no password, every place that reads `password_hash` must cope.

Steps:
1. `UserTable.password_hash` → `.nullable()`. `createMissingTablesAndColumns` will **not** drop the
   `NOT NULL` on an existing column: add an idempotent
   `ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL` in `DatabaseInit` (follow how the
   recipe `slug` / search-index changes are applied there), and mirror it in
   `src/main/resources/sql/create_tables.sql`.
2. New `UserIdentityTable` (`infrastructure/database/tables/`): `user_identities`, UUID id,
   `user_id` FK → `users` `ON DELETE CASCADE`, `provider text`, `subject text`, `created_at`,
   unique index on (`provider`, `subject`). Register it with `createMissingTablesAndColumns`;
   mirror in `create_tables.sql`.
3. Domain: `UserWithPassword.passwordHash` → `String?`. New `UserIdentityRepository` interface in
   `domain/repository/`: `findUserIdBy(provider, subject): UUID?`,
   `link(userId, provider, subject)` (throws a typed exception on unique violation),
   `providersFor(userId): List<String>`. Postgres impl in `infrastructure/database/repositoryImpl/`.
   `UserRepository` gains `createUserWithoutPassword(email, username): User?`.
4. **Hardening:** `AuthService.login` — a user whose hash is null gets the same
   `InvalidCredentialsException` as a wrong password (no different message, no timing shortcut:
   still run a BCrypt verify against a fixed dummy hash). `AccountDeletionService.verifyPassword`
   likewise rejects null.
5. Account deletion must remove the user's `user_identities` rows (FK cascade covers a hard delete;
   check `PostgresAccountDeletionRepository` — if it anonymises instead of deleting the user row,
   delete identities explicitly).
6. Fakes: extend `FakeUserRepository`; add `FakeUserIdentityRepository` in
   `src/test/kotlin/infrastructure/database/`.

Tests: Testcontainers integration test for the Postgres identity repo (link, duplicate → typed
exception, cascade on user delete); `AuthServiceTest` login with null hash → invalid credentials;
`AccountDeletionServiceTest` null hash → invalid credentials; a `DatabaseInit` test (or existing
schema test) that the `ALTER` is idempotent.

Done when: all tests pass, no behaviour change for existing password users.

### B2 — Google ID token verifier

Branch `google-signin/b2-verifier`.

**No new dependency.** `ktor-server-auth-jwt` already brings `com.auth0:java-jwt` and
`com.auth0:jwks-rsa`. Use them (if they are only transitive today, declare them in
`libs.versions.toml` at the **same version** Ktor resolves — same artifact, nothing new shipped).

Steps:
1. Domain interface `domain/service/GoogleIdTokenVerifier`:
   `fun verify(idToken: String): GoogleIdentity` where
   `data class GoogleIdentity(val subject: String, val email: String, val name: String?,
   val givenName: String?, val issuedAt: Instant)`; throws `InvalidGoogleTokenException`
   (new, in `AuthExceptions.kt`).
2. Impl `infrastructure/auth/Auth0GoogleIdTokenVerifier`:
   - `JwkProviderBuilder(URL("https://www.googleapis.com/oauth2/v3/certs")).cached(10, 24, HOURS)
     .rateLimited(10, 1, MINUTES).build()`
   - `Algorithm.RSA256(RSAKeyProvider)` resolving the key by the token's `kid`
   - `JWT.require(alg).withIssuer("accounts.google.com", "https://accounts.google.com")
     .withAudience(webClientId).acceptLeeway(60).build()`
   - then require `email_verified == true` and a non-blank `email`; lower-case the email the same
     way `InputValidator.sanitizeEmail` does.
   - Any exception from the library → `InvalidGoogleTokenException` (log the reason category, never
     the token).
3. Config: `google.webClientId` in `application.yaml` read via `environment.config`, as
   `jwt.*` is. If blank, the Google routes respond 503 rather than accepting any audience.
4. Wire it in `Application.kt` next to `AuthService`.

Tests (`infrastructure/auth/`): generate an RSA key pair in the test, serve it through a fake
`JwkProvider`, sign tokens with `java-jwt`, and cover: valid; wrong `aud`; wrong `iss`; expired;
`email_verified=false`; missing email; bad signature (signed with another key); unknown `kid`.

### B3 — `POST /auth/google`

Branch `google-signin/b3-endpoint`. Needs B1, B2.

Steps:
1. DTO `GoogleSignInRequest(idToken: String)` in `application/dto/AuthDtos.kt`.
2. `AuthService.signInWithGoogle(idToken): AuthResponse`:
   1. `verifier.verify` → `GoogleIdentity`.
   2. `identityRepo.findUserIdBy("google", sub)` → found: issue tokens for that user
      (`generateAndStoreTokens`).
   3. Else `userRepository.findUserByEmail(email)` → found: throw
      `GoogleEmailExistsException` (new) → 409 `GOOGLE_EMAIL_EXISTS`.
   4. Else create: `user_name` from `givenName` ?: email local part, reduced to
      `[a-zA-Z0-9_.-]`, padded/truncated to `InputValidator`'s 3–100 chars, and if it is a reserved
      name append digits; `display_name` = `name` (as-is, accents and spaces kept). Create the user
      and the identity row **in one transaction**; if the identity insert hits the unique index
      (two concurrent first sign-ins), re-read and sign in the existing user.
3. Route in `AuthRoutes.kt` under `/auth`, mapping exceptions to the contract table. Keep the
   existing route style (explicit `try/catch` per exception).
4. Add `hasPassword` / `providers` to `AuthResponse` with defaults, filled for all three auth
   endpoints (login, register, google).

Tests: `AuthServiceTest` for the three branches, username derivation (accents, spaces, all
non-ASCII → email local part, reserved name, too short), concurrent-create race via the fake;
`AuthIntegrationTest` for every status in the contract table, using a fake verifier.

### B4 — `POST /auth/google/link`

Branch `google-signin/b4-link`. Needs B3.

Authenticated route (inside `authenticate("auth-jwt")`, auth rate-limit bucket). Service method
`linkGoogle(userId, idToken)`: verify; token email must equal the account email
(`GOOGLE_EMAIL_MISMATCH`); `sub` already linked to this user → 204; to another user →
`GOOGLE_IDENTITY_TAKEN`; else insert → 204.

Tests: service + integration for each row of the contract table, plus "after linking,
`/auth/google` with that token signs in the same user".

### B5 — Account deletion with a Google token

Branch `google-signin/b5-deletion`. Needs B2 (and B1 for identities).

`DeleteAccountRequest.password` → `String?`, add `googleIdToken: String?`. Exactly one required.
`AccountDeletionService.deleteAccount` takes a sealed `DeletionProof { Password(value) |
GoogleToken(value) }`. Google: verify, `sub` must be in the user's identities, `iat` ≥ now − 5 min
(use the injected `Clock`, see `support/MutableClock.kt`).

Tests: existing password tests unchanged; Google happy path; stale `iat`; `sub` not linked;
both/neither fields → 400.

---

## Android track (this repo)

### A1 — Refactor: one `completeAuthentication` in `SessionManager` (no behaviour change)

Branch `google-signin/a1-session-refactor`. Can start now.

`SessionManager.login()` and `register()` duplicate the post-response sequence: `saveAuthData`
(with `pendingUpgradeFrom`) → `accountSwitchHandler.handleLogin(isCredentialLogin = true)` →
`AccountUpgradeUseCase` → `UserSession.Authenticated` → `persistAuthenticatedUser` →
`attachHouseholdState` → `requestImmediateSync` + `schedulePeriodicSync`, plus the
`CancellationException` / failure handling that restores the anonymous session.

Extract one `private suspend fun completeAuthentication(response: AuthResponse,
anonymousUserId: UUID?): User`, and keep each public method's own pieces (the
`registerOrRecoverLostResponse` path, log lines) in the public method. **Keep the order exactly**:
the comment in `login()` explains why `saveAuthData` runs before the switch and upgrade.

Tests: the existing `SessionManagerTest` must pass unchanged. Add no new behaviour. If a test needs
changing, stop and report why.

### A2 — Dependencies, config, and the Credential Manager wrapper

Branch `google-signin/a2-credential-manager`. Needs A1 only for a clean base.

1. `gradle/libs.versions.toml` + `app/build.gradle.kts` — **these three only**, latest stable
   versions, and state the versions in the report:
   - `androidx.credentials:credentials`
   - `androidx.credentials:credentials-play-services-auth`
   - `com.google.android.libraries.identity.googleid:googleid`
2. `BuildConfig.GOOGLE_WEB_CLIENT_ID` from Gradle property `chefaiGoogleWebClientId` (same
   mechanism as `chefaiApiBaseUrl` at the top of `app/build.gradle.kts`), default `""`. Add
   `GOOGLE_SIGN_IN_CONFIGURED = GOOGLE_WEB_CLIENT_ID.isNotBlank()`; the button is hidden when false.
3. `auth/ui/google/GoogleIdTokenRequester.kt`:

   ```kotlin
   sealed interface GoogleIdTokenResult {
       data class Success(val idToken: String) : GoogleIdTokenResult
       data object Cancelled : GoogleIdTokenResult
       data object NoGoogleAccount : GoogleIdTokenResult
       data class Failure(val cause: Throwable) : GoogleIdTokenResult
   }
   ```

   A plain class (not Hilt-injected, no stored Context) with
   `suspend fun request(activityContext: Context): GoogleIdTokenResult` using
   `GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()` and
   `CredentialManager.create(activityContext).getCredential(...)`. Map
   `GetCredentialCancellationException` → `Cancelled`, `NoCredentialException` →
   `NoGoogleAccount`, a `CustomCredential` of type `GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL`
   → `Success`, anything else → `Failure`. Rethrow `CancellationException`.
4. A `rememberGoogleIdTokenRequester()` composable helper.

Tests: the mapping function from exceptions/credentials to `GoogleIdTokenResult` is pure — extract
it and unit-test it (Robolectric only if `GoogleIdTokenCredential.createFrom` needs a `Bundle`).

### A3 — Network + `SessionManager.loginWithGoogle`

Branch `google-signin/a3-session`. Needs A1. Codes against the contract; no backend needed.

1. `AuthResponse.kt`: `GoogleSignInRequest(idToken)`; `ApiErrorResponse` gains
   `code: String? = null`; `AuthResponse` gains `hasPassword: Boolean = true`,
   `providers: List<String> = emptyList()`.
2. `AuthNetworkDataSource.googleSignIn(request): AuthResponse` and `linkGoogle(idToken)`;
   implement in `AuthApiService` (`GOOGLE_ENDPOINT`, `GOOGLE_LINK_ENDPOINT`), same error style:
   `AuthHttpException` — add `code: String? = null` to it.
3. `SessionManager.loginWithGoogle(idToken: String): Result<User>` — the same shape as `login()`
   but calling `googleSignIn`, then `completeAuthentication` (A1).
4. Persist `hasPassword` / `providers` with the user (`SecurePreferences`, alongside display
   name/email) and expose them on the domain `User` or a small `SignInMethods` value; needed by A5
   and A6.
5. Extend `FakeAuthNetworkDataSource`.

Tests (`SessionManagerTest`): Google success from anonymous (upgrade runs, `pendingUpgradeFrom`
cleared); success with a different previous account (switch handler path); 409 →
`Result.failure` with `AuthHttpException(code = "GOOGLE_EMAIL_EXISTS")` and the session back to
anonymous; cancellation restores anonymous. `AuthApiServiceTest`-style MockEngine tests for both
new calls.

### A4 — Login and Register UI

Branch `google-signin/a4-ui`. Needs A2, A3.

1. `LoginViewModel` / `RegisterViewModel`: `fun onGoogleResult(result: GoogleIdTokenResult)`.
   `Success` → loading, `sessionManager.loginWithGoogle`, then the existing success navigation
   (including the pending-invite path). `Cancelled` → nothing. `NoGoogleAccount` / `Failure` /
   `INVALID_GOOGLE_TOKEN` → snackbar. `GOOGLE_EMAIL_EXISTS` → a message telling the user to sign in
   with their password and connect Google from their profile; on Register, also navigate to Login
   with the email prefilled. Reuse the existing offline/unreachable-backend handling.
2. Screens: a "Continue with Google" button (Google's branding guidelines: the "G" logo,
   "Continue with Google" label) under the primary button on both screens, hidden when
   `!BuildConfig.GOOGLE_SIGN_IN_CONFIGURED`. The click launches
   `requester.request(LocalContext.current)` in a `rememberCoroutineScope()` and passes the result
   to the ViewModel. Stateless `*Content` composables take `onGoogleClick: () -> Unit`.
3. Strings in `strings.xml` (and any existing translations' files, English as placeholder if the
   project has a translation process — check).
4. Previews: light + dark for the updated content composables (the existing previews already have
   both — keep them compiling and showing the button).

Tests: `LoginViewModelTest` / `RegisterViewModelTest` for each `GoogleIdTokenResult` and each error
code. Run the unit tests and also `./gradlew :app:compileDebugAndroidTestKotlin` so the
instrumented Hilt graph still compiles.

### A5 — Logout clears Credential Manager state; "Connect Google" in profile

Branch `google-signin/a5-logout-link`. Needs A4 (and B4 for a live test).

1. Logout: after `LogoutUseCase` completes, the UI calls
   `CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())`, best
   effort (catch and log `ClearCredentialException`). Keep it in the UI layer where the Context is;
   `LogoutUseCase` stays Context-free.
2. Profile/Settings: when signed in and `"google" !in providers`, show "Connect Google". It runs
   the requester, then `linkGoogle`; map 204 → success snackbar and refresh `providers`;
   `GOOGLE_EMAIL_MISMATCH` / `GOOGLE_IDENTITY_TAKEN` → specific messages.

Tests: `UserProfileViewModelTest` (or the Settings ViewModel, whichever owns the row) for each
outcome.

### A6 — Account deletion for Google accounts

Branch `google-signin/a6-deletion`. Needs A3 (and B5 for a live test).

`DeleteAccountUseCase` / `AccountRepository.deleteAccount` take a sealed
`DeletionProof { Password(value) | GoogleToken(value) }`. `SettingsScreen`'s delete flow asks for
the password when `hasPassword`, otherwise shows "Confirm with Google", which runs the requester
and sends the fresh token. Map the new 401 to the existing wrong-credentials state with a
Google-specific message.

Tests: `DeleteAccountUseCaseTest`, `DefaultAccountRepositoryTest`, `AccountApiServiceTest`,
Settings ViewModel tests for both proofs.

### Android docs (with the last Android task)

Update `docs/authentication.md` (Google flow, linking, logout), `docs/feature-status.md` or the
CLAUDE.md gaps table, and set ADR-019 to **Accepted** with any deviations recorded.

---

## V — End-to-end verification (human + agent, needs G0 and all tasks)

On a device with a real Google account, against a backend with B1–B5 deployed:

1. Fresh install, anonymous, create a recipe → Continue with Google → signed in, recipe kept
   (upgrade ran), syncs.
2. Log out → Continue with Google → account chooser appears (not silently auto-signed in).
3. Email already registered with a password → Continue with Google → 409 message; sign in with
   password → Connect Google → log out → Continue with Google signs in to the same account.
4. Google-only account → Settings → delete → "Confirm with Google" → deleted.
5. A release build installed from Play internal testing (checks the Play App Signing SHA-1).

---

## R — Password reset (separate work, release gate)

Being built separately with Resend. This plan does not implement it. Google sign-in ships only
after it: the 409 path tells users to sign in with their password, and a forgotten password needs
a way out.

---

## F — Follow-up: nonce (do not start without the user's sign-off)

**The question.** A Google ID token is a signed statement "this is Google user X, issued for
Pocket Chef", valid for about an hour. The backend checks *who* the token is for, but not *which
sign-in attempt* produced it. Anyone who gets hold of a token within that hour can post it to
`/auth/google` and receive a Pocket Chef session for that user. A **nonce** binds the token to one
attempt: the server hands out a random one-time value, the app passes it to Google, Google signs it
into the token, and the server accepts each value once.

**How a token could leak:** logs or crash reports that capture request bodies, a proxy or
analytics SDK that records traffic, malware on a rooted device. Not by network sniffing (TLS), and
not from another app (tokens are issued for our client ID only).

**Trade-offs**

| | Skip (v1 as planned) | Add the nonce |
|---|---|---|
| Replay of a leaked token | Possible for ~1 h after issue | Blocked |
| Backend work | none | `GET /auth/google/nonce` (public, rate-limited); a stateless nonce (HMAC of random bytes + expiry, keyed by a server secret) plus a store of *used* nonces until expiry; `/auth/google`, `/link` and deletion check the `nonce` claim |
| Android work | none | fetch a nonce before the picker (prefetch on screen open to hide the latency), pass it via `setNonce`; handle "nonce expired" by refetching once |
| Extra latency | — | one request, ~100–300 ms, hidden if prefetched |
| Failure modes added | — | nonce endpoint down → Google sign-in down; clock skew on expiry; multi-instance backend needs the used-nonce store shared (Postgres table, not memory) |
| Today's exposure | Low: Ktor client logging is `HEADERS` in debug and `NONE` in release, so bodies are never logged; backend never logs the token (rule 7) | — |

**Recommendation:** skip in v1; add it if any of these becomes true — a crash/analytics SDK or
proxy that captures request bodies is added, the backend runs behind something that logs bodies,
or a security review asks for it. If approved, it is roughly one backend task (endpoint + used-nonce
table + checks, ~1 day) and one Android task (~½ day), slotted after B5 and A6.
