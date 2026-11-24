# Play Store release checklist

_Created November 2026 for the first Play release._

The code half of release readiness is in the repo (R8, signing hooks, versioning, release lint
clean). This file tracks the steps that can only be done by hand: keys, consoles, hosting. Tick each
one off here as it is done.

## 1. Upload key and signing

- [ ] Create an upload keystore, kept **outside** the repo (`*.jks` is gitignored anyway):
  ```bash
  keytool -genkeypair -v -keystore ~/keys/pocket-chef-upload.jks -alias upload \
    -keyalg RSA -keysize 4096 -validity 10000
  ```
- [ ] Back up the keystore and both passwords somewhere other than this machine (a password
  manager). Losing the upload key means a key-reset request to Google.
- [ ] Add to `~/.gradle/gradle.properties` (or the matching `CHEFAI_UPLOAD_*` env vars in CI):
  ```properties
  chefaiUploadStoreFile=/Users/<you>/keys/pocket-chef-upload.jks
  chefaiUploadStorePassword=…
  chefaiUploadKeyAlias=upload
  chefaiUploadKeyPassword=…
  ```
  Without all four, `bundleRelease` still builds but leaves the bundle unsigned.
- [ ] Build: `./gradlew :app:bundleRelease -PchefaiVersionName=1.0.0`
  → `app/build/outputs/bundle/release/app-release.aab`. Keep
  `app/build/outputs/mapping/release/mapping.txt` with it (Play takes it on upload for
  de-obfuscated crash traces; it uploads automatically with the bundle).
- [ ] Enrol in **Play App Signing** on first upload (default for new apps).

## 2. Versioning

`versionCode` comes from `-PchefaiVersionCode` / `CHEFAI_VERSION_CODE`, falling back to
`git rev-list --count HEAD`. `versionName` comes from `-PchefaiVersionName`, default `1.0.0`.

- [ ] Decide how releases are cut: by hand from `main` (the commit count is fine), or in CI (pass
  the run number, since a shallow checkout counts 1 commit).
- [ ] Never upload two builds with the same `versionCode`; Play rejects it.

## 3. App Links (`assetlinks.json`)

Invite and password-reset links verify against
`https://pocket-chef.app/.well-known/assetlinks.json`, served by **chefai-web**
(`public/.well-known/assetlinks.json`). It lists only the debug fingerprint today (security
finding A12).

- [ ] After the first upload, copy the **app signing key** SHA-256 from Play Console → Test and
  release → App integrity → App signing (not the upload key's).
- [ ] Add it to `sha256_cert_fingerprints` in chefai-web and deploy. Keep the debug one if debug
  builds should keep opening links.
- [ ] Verify on a device with the Play build installed:
  `adb shell pm get-app-links com.tenmilelabs.chefai` shows `pocket-chef.app: verified`.

## 4. Sign in with Google

The button only shows when `chefaiGoogleWebClientId` is set at build time.

- [ ] Decide: ship v1 with Google sign-in, or without (leave the property unset).
- If shipping it:
  - [ ] Backend endpoints deployed (jmuci/Chef.ai_Backend#10) and `GOOGLE_WEB_CLIENT_ID` set there.
  - [ ] In Google Cloud, create an **Android** OAuth client for `com.tenmilelabs.chefai` with the
    **app signing key SHA-1** from Play Console (and one for the upload key / debug key if you
    test sideloaded builds).
  - [ ] OAuth consent screen published *In production*.
  - [ ] Set `chefaiGoogleWebClientId=<web client id>` in `~/.gradle/gradle.properties`.
  - [ ] Test sign-in, link and Google-only account deletion on an installed Play build (internal
    testing track).

## 5. Backend and hosting

- [ ] `https://api.pocket-chef.app` is up with the production database, and the password-reset
  routes are deployed (CLAUDE.md gaps table: "Needs backend routes + assetlinks live").
- [ ] A privacy policy page is live on `pocket-chef.app` (Play requires its URL).
- [ ] An account-deletion web page or URL exists (Play's Data safety form asks for one in addition
  to the in-app Settings → Delete account).

## 6. Play Console

- [ ] Developer account set up (personal accounts created after Nov 2023 must run a **closed test
  with at least 12 testers for 14 days** before production access).
- [ ] App created: name **Pocket Chef**, default language, free.
- [ ] Store listing: short and full description, 512×512 icon, 1024×500 feature graphic, at least
  2 phone screenshots (plus 7"/10" tablet ones if you want the tablet badge).
- [ ] Data safety form. What the app collects: email and name (account), user content (recipes,
  meal plans, photos), app interactions (sync), device IP to the backend; and that recipe import
  fetches third-party sites from the device. Encrypted in transit: yes. Deletion: yes, in-app.
- [ ] Content rating questionnaire (IARC).
- [ ] Target audience (not designed for children) and ads declaration (no ads).
- [ ] Notification permission: no declaration needed for `POST_NOTIFICATIONS`.
- [ ] Upload the bundle to **Internal testing** first; install from Play and smoke-test before
  promoting to closed testing.

## 7. Before promoting to production

- [ ] Smoke-test the Play-installed build: anonymous use, sign up, sign in, sync on a second device,
  import a recipe from the share sheet, generate a meal plan, shopping list, timer notification,
  invite link, password reset link, delete account.
- [ ] Check Play Console → Android vitals and the pre-launch report for crashes on the test track.
