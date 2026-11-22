# ADR 019 – Sign in with Google

**Date:** November 2026
**Status:** Proposed
**Author:** Claude (Opus 5.5), on behalf of Jose Mucientes
**Related:** [ADR-007](adr-007-anonymous-first.md) (anonymous-first),
[ADR-008](adr-008-data-handling-across-sessions.md) (data across sessions),
[docs/authentication.md](../authentication.md)

---

## Context

Accounts today are email + password only (`POST /auth/register`, `POST /auth/login`), and the
backend issues its own JWT access/refresh pair. Typing a password on a phone is the biggest
remaining friction in the anonymous → account upgrade that ADR-007 relies on, so we want a
one-tap "Continue with Google".

What already exists and must not change:

- **Everything after the token exchange.** `SessionManager.login()` persists auth data with the
  `pendingUpgradeFrom` marker, runs `AccountSwitchHandler`, re-parents anonymous rows
  (`AccountUpgradeUseCase`), persists the user, attaches household state and starts sync. The
  ordering is deliberate (see ADR-008 amendments) and Google sign-in must reuse it, not copy it.
- **Our own session.** Refresh, logout revocation, 401 handling and sync all work on the backend's
  tokens. None of them should learn about Google.

Gaps a Google-only account exposes:

- **Account deletion asks for the password** (`DeleteAccountUseCase`,
  `DeleteAccountRequest.password`). A Google-only user has none, and Play policy requires in-app
  deletion.
- **Registration requires a `username`**, which Google does not supply.

## Decision 1: Exchange a Google ID token for our own tokens

```
Android: Credential Manager → Google ID token (JWT signed by Google)
Android → POST /auth/google { idToken }
Backend: verify → find-or-create user → 200 AuthResponse (same shape as /auth/login)
Android: same post-login pipeline as email login
```

The Google ID token is used once, to prove identity, and is never stored or sent again. The
response is the existing `AuthResponse`, so refresh/logout/sync are unchanged.

Alternatives rejected:

- **Firebase Auth.** Another identity system next to ours, a heavier SDK, and Firebase UIDs to map
  onto our users — for one provider we can verify directly.
- **OAuth authorization-code flow with a server-held Google refresh token.** Only needed to call
  Google APIs on the user's behalf. We need identity, not Google data access.
- **Legacy Google Sign-In (`play-services-auth` `GoogleSignInClient`).** Deprecated in favour of
  Credential Manager.

## Decision 2: Backend verification and identity model

**Verification** uses a library (Google's `google-api-client` `GoogleIdTokenVerifier`, or Nimbus
JOSE + Google's JWKS), behind a small `GoogleIdTokenVerifier` interface so tests can sign tokens
with a local key. Checks: signature against Google's published keys, `iss` ∈
{`accounts.google.com`, `https://accounts.google.com`}, `aud` == our **Web** OAuth client ID,
`exp` in the future, `email_verified == true`. Failure → `401 INVALID_GOOGLE_TOKEN`.

**Schema** (Postgres):

- `users.password_hash` becomes nullable (Google-only accounts have none).
- New `user_identities(id uuid pk, user_id uuid fk → users, provider text, subject text,
  created_at)`, `unique(provider, subject)`. `provider = 'google'`, `subject` = the token's `sub`.

A table rather than a `users.google_sub` column, so a second provider (Sign in with Apple, if an
iOS client ever ships) is a row, not another migration.

The backend has no migration tool: `DatabaseInit` runs `SchemaUtils.createMissingTablesAndColumns`,
which creates the new table but **does not relax `NOT NULL` on an existing column**. The
`password_hash` change needs a hand-written idempotent `ALTER TABLE users ALTER COLUMN
password_hash DROP NOT NULL` in `DatabaseInit` (as the recipe `slug` and search-index changes do),
mirrored in `sql/create_tables.sql`.

**Lookup order** on `POST /auth/google`:

1. `user_identities` row for (`google`, `sub`) → sign that user in.
2. Else an existing user with the same email (emails are stored lower-cased by
   `InputValidator.sanitizeEmail`; compare the token's email the same way) →
   **`409 GOOGLE_EMAIL_EXISTS`** (Decision 3). Nothing is created or linked.
3. Else create a user (no password), insert the identity row, sign in.

Matching is on `sub`, never on email alone: a Google account's email can change, `sub` cannot.

**Username** for a new user: derived from the token's `given_name` (fallback: the email local
part), sanitised to `InputValidator`'s rules (`[a-zA-Z0-9_.-]`, 3–100 chars, not reserved), with a
numeric suffix until it is free. Editable later, if and when a profile edit screen exists.

**Usernames must be unique, and today they are not.** Verified against the backend at `bc1f3eb`:
`UserTable.user_name` is a plain `text` column with no index, `create_tables.sql` has
`user_name TEXT NOT NULL` without `UNIQUE`, and `AuthService.register` checks only the email. Only
`email` is unique. This is a **prerequisite**, best done with the registration work already in
progress:

- a unique index on `lower(user_name)` (case-insensitive, so `Jose` and `jose` collide), added by
  hand in `DatabaseInit` like the `password_hash` change;
- before creating it, find duplicates already in production (`GROUP BY lower(user_name) HAVING
  count(*) > 1`) and rename them, or the index creation fails at boot;
- `register` returns `409 USERNAME_TAKEN`, and the Android register screen shows it;
- add `deleted-user` (`DeletedUser.USERNAME`) to `InputValidator`'s reserved names, so nobody can
  register the deletion placeholder's name.

## Decision 3: An email that already has an account gets 409; linking is explicit and signed in

When step 2 matches an email, `POST /auth/google` returns `409 GOOGLE_EMAIL_EXISTS` and creates
nothing. The app says "An account with this email already exists — sign in with your password,
then connect Google from your profile."

**Linking** is a separate, authenticated call: `POST /auth/google/link { idToken }` with the
user's access token. The server verifies the token (Decision 2), requires its email to equal the
signed-in account's email, and inserts the `user_identities` row (`409` if that `sub` already
belongs to another user). After that, step 1 of the lookup signs them in with Google.

Why not auto-link on a matching verified email: registration does not verify email ownership yet,
so an attacker could register `victim@gmail.com` with a password first and keep that password
after the victim's Google sign-in linked to it. Auto-linking safely means dropping the password
and revoking sessions on link, which is more machinery and a surprise for honest users. With 409,
the account's own credentials are always needed to attach a new way in.

**Depends on password reset.** A user who hits the 409 and has forgotten their password is stuck
until password reset exists — today there is none. The same flow is the way out of the squatting
case above: the real owner of `victim@gmail.com` resets the password through their own mailbox,
which locks out whoever registered it. Email verification and password reset (via Resend) are
being built separately; **Google sign-in should not ship to users before password reset does.**

## Decision 4: Android integration

**Dependencies** (version catalog): `androidx.credentials:credentials`,
`androidx.credentials:credentials-play-services-auth`,
`com.google.android.libraries.identity.googleid:googleid`.

**Request.** The button uses `GetSignInWithGoogleOption(serverClientId = WEB_CLIENT_ID)` — the
explicit "Sign in with Google" flow, which always shows the account chooser. The Web client ID
comes from a Gradle property into `BuildConfig.GOOGLE_WEB_CLIENT_ID`, the same way
`chefaiApiBaseUrl` reaches `BuildConfig.API_BASE_URL`. It is not a secret.

**Where the Activity context lives.** `CredentialManager.getCredential()` needs an Activity
context, and ViewModels hold no Context. A small `GoogleIdTokenRequester` in `auth/ui` runs the
request from the composable and returns a sealed result:

```kotlin
sealed interface GoogleIdTokenResult {
    data class Success(val idToken: String) : GoogleIdTokenResult
    data object Cancelled : GoogleIdTokenResult
    data object NoGoogleAccount : GoogleIdTokenResult
    data class Failure(val cause: Throwable) : GoogleIdTokenResult
}
```

The screen forwards it as a UI event (`onAction(LoginUiEvent.GoogleResult(result))`); the
ViewModel stays Context-free and owns everything after that. `Cancelled` is silent; the others
map to an error message.

Alternative: a domain-level `GoogleCredentialSource` taking the Activity per call. It makes the
picker step unit-testable, but the step is pure UI plumbing with nothing to decide, so the extra
layer is not worth it now.

**Session.** `SessionManager.loginWithGoogle(idToken: String): Result<User>`. Before adding it,
the shared body of `login()` and `register()` — from `saveAuthData` through
`schedulePeriodicSync()`, plus the `Loading`/anonymous-restore error handling — moves into one
private `completeAuthentication(response, anonymousUserId)`, so there is one copy of the ordering
ADR-008 depends on, not three. Google sign-in is a credential login
(`isCredentialLogin = true`: the pull restarts from 0).

**Logout** calls `CredentialManager.clearCredentialState()` (best effort), so a later
auto-select flow cannot silently sign the same Google account back in.

**Both Login and Register screens** show the button; for Google they are the same action.

## Decision 5: Account deletion for Google accounts

`DELETE` account accepts either `password` or `googleIdToken`. The server verifies the Google
token as in Decision 2, requires its `sub` to match one of the user's identities, and requires
`iat` within the last 5 minutes (re-authentication, not a token the client happened to keep). The
deletion screen asks for whichever the account has; the auth response (or `GET /account`) gains
`hasPassword` / `providers` so the client knows which.

## Google Cloud setup (not code, but part of the decision)

- One **Web** OAuth client: its ID is `serverClientId` on Android and the expected `aud` on the
  backend.
- One **Android** OAuth client per signing certificate SHA-1: debug, upload key, **and the Play App
  Signing key**. Missing the Play key means sign-in works in local builds and fails for every Play
  install.
- OAuth consent screen published ("In production"). Only `openid email profile` are requested,
  which need no Google verification review.

## Consequences

- **Public API (backend):** new `POST /auth/google` and `POST /auth/google/link`; the deletion
  request gains an optional `googleIdToken`; the auth response may gain `hasPassword`/`providers`;
  `register` gains a `409 USERNAME_TAKEN`. All additive except that last one, which turns a
  registration that succeeds today into an error.
- **Deploy order:** username uniqueness and password reset first, then the Google endpoints, then
  the client. A client calling `/auth/google` on an older backend gets 404, which surfaces as a
  sign-in error, not data loss.
- **Google-only accounts have no password.** Adding one later is a "set password" flow; the
  password-reset flow being built can probably serve it (reset on an account with no password).
- **Anonymous upgrade, account switch and sync are untouched:** they key off our user UUID, which
  Google sign-in returns like any login.
- **Tests:** Android — `loginWithGoogle` success/failure/upgrade with the existing fakes, and the
  ViewModel's handling of each `GoogleIdTokenResult`, including the 409 message. Backend —
  verifier rejections (bad `aud`, expired, unverified email, bad signature) with a locally signed
  token, the three lookup branches, `/link` (email mismatch, `sub` owned by someone else), and
  `USERNAME_TAKEN` case-insensitively.

## Resolved (Nov 2026)

- **Existing email → 409**, not auto-link (Decision 3).
- **Email verification** is being added to registration alongside password reset (Resend), as
  separate work.
- **Usernames must be unique** — they are not today; made a prerequisite (Decision 2).

## Open question: nonce

A Google ID token is valid for about an hour. Anyone who obtains one issued for our app — from a
log, a crash report, a compromised device — can post it to `/auth/google` within that hour and get
a session. A nonce closes that: the server issues a random value, the app puts it in the Google
request, Google signs it into the token, and the server accepts each nonce once.

Cost: an endpoint (`GET /auth/google/nonce`; it can be stateless — an HMAC of a random value and an
expiry — with only "used" nonces stored until expiry) and one extra round trip, which the screen
can make on open, before the button is tapped.

Today's exposure is small: the token only travels over TLS, and the app's Ktor logging is
`HEADERS` in debug and `NONE` in release, so request bodies are never logged. **Proposed: skip the
nonce in v1**; never log the token, and add the nonce if tokens ever pass through anything that
records request bodies.
