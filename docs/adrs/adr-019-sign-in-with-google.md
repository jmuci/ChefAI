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

**Schema** (Postgres, one migration):

- `users.password_hash` becomes nullable (Google-only accounts have none).
- New `user_identities(id uuid pk, user_id uuid fk → users, provider text, subject text,
  created_at)`, `unique(provider, subject)`. `provider = 'google'`, `subject` = the token's `sub`.

A table rather than a `users.google_sub` column, so a second provider (Sign in with Apple, if an
iOS client ever ships) is a row, not another migration.

**Lookup order** on `POST /auth/google`:

1. `user_identities` row for (`google`, `sub`) → sign that user in.
2. Else an existing user with the same email → **link** (Decision 3), insert the identity row,
   sign in.
3. Else create a user (no password), insert the identity row, sign in.

Matching is on `sub`, never on email alone: a Google account's email can change, `sub` cannot.

**Username** for a new user: derived from the token's `given_name` (fallback: the email local
part), with a numeric suffix on collision if usernames are unique. Editable later, if and when a
profile edit screen exists.

## Decision 3: Linking an existing email + password account — auto-link, then drop the password if it was never proven

When step 2 matches an email, we link automatically: Google has verified the user owns that
mailbox, so this is the account they meant. Returning `409` and making them type a password first
is the safer-looking alternative but recreates the friction this ADR removes, and most users who
hit it have forgotten the password (there is no reset flow).

**Pre-account-takeover guard.** Registration does not verify email ownership today (to confirm on
the backend). So an attacker could register `victim@gmail.com` with a password before the victim
ever signs in with Google; plain auto-linking would leave the attacker a working password on the
victim's account. Therefore, when linking to an account whose email was never verified:

- set `password_hash = NULL`, and
- revoke all of that user's refresh tokens.

The legitimate owner loses nothing (they just signed in with Google); an attacker loses access.
If registration ever gains email verification, accounts with a verified email keep their password
on link.

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

- **Public API (backend):** a new `POST /auth/google`; the deletion request gains an optional
  `googleIdToken`; the auth response may gain `hasPassword`/`providers`. All additive.
- **Deploy order:** backend first. A client calling `/auth/google` on an older backend gets 404,
  which surfaces as a sign-in error, not data loss.
- **No password reset needed for Google-only users**, but an auto-linked account whose password was
  dropped (Decision 3) can only use Google from then on. Adding a password later needs a
  "set password" flow that does not exist.
- **Anonymous upgrade, account switch and sync are untouched:** they key off our user UUID, which
  Google sign-in returns like any login.
- **Tests:** Android — `loginWithGoogle` success/failure/upgrade with the existing fakes, and the
  ViewModel's handling of each `GoogleIdTokenResult`. Backend — verifier rejections (bad `aud`,
  expired, unverified email, bad signature) with a locally signed token, and the three lookup
  branches including the password-drop link.

## Open questions

1. **Auto-link vs 409** (Decision 3) — proposed auto-link with the password-drop guard.
2. **Does the backend verify email at registration?** Decides whether Decision 3 ever keeps a
   password on link.
3. **Nonce.** Credential Manager can embed a nonce in the ID token. Checking it means a
   server-issued nonce (`GET /auth/google/nonce`) and an extra round trip. Proposed: skip in v1 —
   tokens are short-lived, audience-bound and travel over TLS — and add it if the threat model
   changes.
4. **Are usernames unique** on the backend? Decides whether Decision 2 needs the collision suffix.
