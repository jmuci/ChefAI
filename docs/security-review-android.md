# Security review: Android client (Pocket Chef)

Reviewed 2026-11-16 against `4428ce0c`, from `docs/security-handover-android.md` plus 8 targeted
file reads (listed at the end). Line numbers are those of the handover's §9 unless a file is named
that the handover does not include.

## Status

All 13 findings are fixed on the client and merged to `main`. Two still depend on work outside this
repo.

| Finding | PR | Notes |
|---------|----|-------|
| A1 — logout leaves data readable | #352 | Backend half of household visibility: jmuci/Chef.ai_Backend#132 |
| A2 — refresh 4xx strands the user as anonymous | #351 | |
| A3 — zero-click fetch of synced `imageUrl` | #353 | |
| A4 — restore defeats switch detection; push not owner-filtered | #352 | |
| A5 — pending invite token planted, never expires | #352 | |
| A6 — share intent opens the in-app browser with no tap | #354 | |
| A7 — Coil loads any host and scheme | #353 | **Tracking-pixel half pending** jmuci/Chef.ai_Backend#131 (blob or proxy serving of other users' images) |
| A8 — failed logout resurrects the session | #351 | |
| A9 — rotated refresh token written in a separate edit | #351 | |
| A10 — process death after account deletion | #351 | |
| A11 — task hijacking on Android 9–10 | #354 | |
| A12 — invite App Link verification unconfirmed | #354 | **Pending a hosting change:** `assetlinks.json` lists only a debug fingerprint; the release (or Play App Signing) fingerprint must be added before release |
| A13 — debug test credentials in the release dex | #354 | |

Verified on an emulator against a local backend on 2026-11-17: the previously unrun instrumented
tests (`RecipeDaoTest`'s household visibility test, `AccountSwitchHandlerDatabaseTest`,
`WebViewHtmlFetcherTest.runsTheAcceptCheckOffTheMainThread`) pass, and manual checks of A1
(signed-out search, list and home), A6 (host-naming dialog, Cancel keeps the URL), A11 (share from
Chrome and the invite link land in the app's own task), A7 (no legitimate image blocked) and
household visibility all passed.

## 1. Verdict

The token and transport basics are sound: Keystore AES-GCM is used correctly, the bearer token is
attached only to the exact API origin, release builds deny cleartext and log nothing, and every
debug surface is behind a compile-time `BuildConfig.DEBUG`. The weak area is account isolation on
the device, which rests on a single cleanup step at the *next different login* and on read queries
that are not filtered by owner. The three issues that matter most:

1. **A1** — logout leaves the whole account dataset on disk and the recipe list and local search
   show it to whoever opens the app next.
2. **A2** — any 4xx from `/auth/refresh` at cold start (429 included) drops the user to an
   anonymous session with the tokens left on disk, and rows created meanwhile get the wrong owner.
3. **A3** — a background worker fetches the server-supplied `imageUrl` of every synced recipe
   through the HTTP-then-WebView ladder, so one PUBLIC recipe makes every syncing device load an
   attacker's page with no user action.

No Critical findings: nothing here yields another user's tokens or server-side account access.

## 2. Findings

### High

#### A1 — Logout leaves the account's data readable by the next person

- **Location:** `SessionManager.kt:525-567` (logout deletes no rows);
  `DefaultRecipeRepository.kt:57-60, 84` (`getRecipesPreviewStream` / `getRecipesStream` call the
  unfiltered `observeRecipesWithDetails()`, with a `TODO pass the userId`); `RecipeDao.kt:93, 311`
  (list and local search have no `creatorId` filter); `AccountSwitchHandler.kt:94`.
- **Failure:** A logs out on a shared or handed-over device. The session becomes Anonymous, but
  A's recipes (PRIVATE included), A's `UserEntity` (email), cached images and Coil cache stay.
  The next person sees A's recipes in the library and in search without signing in. Cleanup runs
  only if someone later logs in with a *different* account, and even then
  `deleteRecipesForUser(previousUserId)` removes only recipes A authored: non-PUBLIC rows A
  received from other creators survive into B's session. Meal plans are owner-filtered and are
  not exposed.
- **Confidence:** confirmed from code for the unfiltered queries and the missing delete. Which
  stream the library screen binds was not read (open question 4).
- **Fix:** purge the departing account at logout, reusing the switch cleanup. **Product decision:**
  this discards changes `LogoutUseCase` reported as `ChangesNotUploaded`, which today survive for a
  re-login. The lossless alternative is to filter every read by owner (the existing TODO); that
  hides the rows but leaves them on disk. New DAO queries only, no Room migration.

- **Decision (2026-11-16): filter reads by owner; do not purge at logout.** Implement instead of
  the `SessionManager.logout` hunk below:
  - Every recipe read that feeds UI or search takes the current user id
    (`sessionManager.getCurrentUserId()`, which is `localUserId` when anonymous) and returns rows
    where `creatorId = :userId OR privacy = 'PUBLIC'`. That covers `getRecipesPreviewStream`,
    `getRecipesStream`, `observeRecipesWithTags` / `WithLabels`, `searchRecipesWithDetails`, and
    the by-id reads (`observeRecipeWithDetails`, `getRecipeWithTags` / `WithLabels`).
  - The stream must re-query when the session changes (`flatMapLatest` on `userSession`), or a
    list collected before logout keeps showing A's rows.
  - If household members' non-PUBLIC recipes are meant to be visible (open question 6), the
    predicate needs a household clause; settle that before writing the query.
  - Keep the `AccountSwitchHandler` and `RecipeDao` hunks: they fix the switch-time residue,
    which filtering does not.
  - Known remainder: A's rows, images and Coil cache stay on disk after logout, and A10 is hidden
    rather than removed. The A1 test in §3 becomes a DAO test of the filtered query.

```diff
--- a/app/src/main/java/com/tenmilelabs/chefai/auth/domain/AccountSwitchHandler.kt
+++ b/app/src/main/java/com/tenmilelabs/chefai/auth/domain/AccountSwitchHandler.kt
@@ -91,25 +91,7 @@ suspend fun handleLogin(
-                val (departingRecipeIds, departingSavedPlanIds) = transactionRunner {
-                    val recipeIds = recipeDao.getRecipeIdsForUser(previousUserId)
-                    val savedPlanIds = savedMealPlanDao.getIdsNotOwnedBy(anonymousUserId)
-                    recipeDao.deleteRecipesForUser(previousUserId)
-                    recipeCookEventDao.deleteAllNotOnPlansOf(anonymousUserId)
-                    mealPlanDao.deleteAllNotOwnedBy(anonymousUserId)
-                    savedMealPlanDao.deleteAllNotOwnedBy(anonymousUserId)
-                    bookmarkedRecipeDao.deleteAllNotOwnedBy(anonymousUserId)
-                    syncMetadataDao.clear()
-                    recipeIds to savedPlanIds
-                }
-                departingRecipeIds.forEach { recipeImageStore.delete(it) }
-                departingSavedPlanIds.forEach { savedPlanImageStore.delete(it) }
-                householdDao.clearCache()
+                purgeAccountData(departingUserId = previousUserId, keepOwnedBy = anonymousUserId)
                 AccountSwitchOutcome.PRESERVED_ANONYMOUS_DATA
@@ -129,0 +112,20 @@
+    suspend fun purgeAccountData(departingUserId: UUID, keepOwnedBy: UUID) {
+        val (recipeIds, savedPlanIds) = transactionRunner {
+            val recipeIds = recipeDao.getPrivateRecipeIdsNotOwnedBy(keepOwnedBy)
+            val savedPlanIds = savedMealPlanDao.getIdsNotOwnedBy(keepOwnedBy)
+            recipeDao.deletePrivateNotOwnedBy(keepOwnedBy)
+            recipeCookEventDao.deleteAllNotOnPlansOf(keepOwnedBy)
+            mealPlanDao.deleteAllNotOwnedBy(keepOwnedBy)
+            savedMealPlanDao.deleteAllNotOwnedBy(keepOwnedBy)
+            bookmarkedRecipeDao.deleteAllNotOwnedBy(keepOwnedBy)
+            userDao.deleteUser(departingUserId)
+            syncMetadataDao.clear()
+            recipeIds to savedPlanIds
+        }
+        recipeIds.forEach { recipeImageStore.delete(it) }
+        savedPlanIds.forEach { savedPlanImageStore.delete(it) }
+        householdDao.clearCache()
+    }
--- a/app/src/main/java/com/tenmilelabs/chefai/core/data/local/room/dao/RecipeDao.kt
+++ b/app/src/main/java/com/tenmilelabs/chefai/core/data/local/room/dao/RecipeDao.kt
@@ -127,0 +128,6 @@
+    @Query("SELECT uuid FROM recipes WHERE creatorId != :keepOwnedBy AND privacy != 'PUBLIC'")
+    suspend fun getPrivateRecipeIdsNotOwnedBy(keepOwnedBy: UUID): List<UUID>
+
+    @Query("DELETE FROM recipes WHERE creatorId != :keepOwnedBy AND privacy != 'PUBLIC'")
+    suspend fun deletePrivateNotOwnedBy(keepOwnedBy: UUID)
--- a/app/src/main/java/com/tenmilelabs/chefai/auth/domain/SessionManager.kt
+++ b/app/src/main/java/com/tenmilelabs/chefai/auth/domain/SessionManager.kt
@@ -539,6 +539,12 @@ suspend fun logout(): Unit = sessionEndMutex.withLock {
             securePreferences.clearAuthData()
+            val localUserId = securePreferences.getLocalUserId().first()
+            when {
+                currentUser == null -> Unit
+                localUserId != null ->
+                    accountSwitchHandler.purgeAccountData(currentUser.uuid, keepOwnedBy = localUserId)
+                else -> accountSwitchHandler.clearAllLocalData()
+            }
             householdRepositoryProvider.get().clearLocalCache()
```

`userDao.deleteUser` cascades to A's recipes (the FK is noted at `SyncOrchestrator.kt:577`), so
A's PUBLIC recipes go too and are re-pulled. Also clear Coil's caches here
(`imageLoader.memoryCache?.clear()`, `imageLoader.diskCache?.clear()`), and decide whether
`last_login_email` should survive logout: it shows A's email to the next person.

#### A2 — A 4xx from `/auth/refresh` at cold start strands the user as anonymous

- **Location:** `SessionManager.kt:178-180` and `763-764`.
- **Failure:** `isRefreshRejection` is `statusCode in 400..499`, so 429, 408 and 426 count as a
  rejected token. A user who cold-starts with an expired access token during a rate-limit window
  becomes Anonymous for the life of the process, while the tokens stay on disk. Their library
  appears to belong to nobody, sync stops, and anything they create is owned by `localUserId`. On
  the next cold start the stored tokens load, the refresh succeeds, and those rows are now pushed
  under A's token with the anonymous `creatorId` (`recipeDao.getAllDirty()` has no owner filter)
  without ever passing through `AccountUpgradeUseCase`. With a real 401 the tokens are never
  cleared, so every cold start flashes Authenticated, re-sends the dead refresh token, and falls
  back again.
- **Confidence:** confirmed from code. Whether a 429 reaches this branch depends on its body
  (**needs verification**): a JSON `ApiErrorResponse` produces `AuthHttpException(429)` and
  strands the user; a plain-text proxy body makes `body()` throw a content-negotiation exception,
  which is treated as transient. Verify with a MockEngine test returning 429 as `text/plain`.
- **Fix:** treat only definitive rejections as rejections, and end the stored session when one
  occurs so the next login goes through the normal upgrade path.

```diff
--- a/app/src/main/java/com/tenmilelabs/chefai/auth/domain/SessionManager.kt
+++ b/app/src/main/java/com/tenmilelabs/chefai/auth/domain/SessionManager.kt
@@ -178,3 +178,7 @@ suspend fun loadSession() {
                     } else if (refreshResult.exceptionOrNull().isRefreshRejection()) {
-                        Timber.w("Refresh token rejected, falling back to anonymous")
-                        enterAnonymousSession()
+                        Timber.w("Refresh token rejected, ending the stored session")
+                        sessionEndMutex.withLock {
+                            householdRefreshJob?.cancel()
+                            securePreferences.clearAuthData()
+                            enterAnonymousSession()
+                        }
@@ -763,2 +767,4 @@
-private fun Throwable?.isRefreshRejection(): Boolean =
-    this is AuthHttpException && statusCode in 400..499
+private val REFRESH_REJECTION_STATUSES = setOf(401, 403)
+
+internal fun Throwable?.isRefreshRejection(): Boolean =
+    this is AuthHttpException && statusCode in REFRESH_REJECTION_STATUSES
```

`current_user_id` is kept, so a re-login as A is `NO_CHANGE` and pending rows are pushed. Add 400
to the set only if the backend uses it for an unknown or expired refresh token (open question 3).

#### A3 — Zero-click fetch and WebView load of any synced recipe's `imageUrl`

- **Location:** `RecipeImageBackfillWorker.kt:111-117`; `RecipeDao.kt:196-207`
  (`getImageBackfillCandidates` has no owner or privacy filter); `SyncMapper.kt:54` (pulled
  `imageUrl` stored unvalidated).
- **Failure:** any registered user publishes a PUBLIC recipe whose `imageUrl` points at their
  server. Every device that receives the row schedules a backfill after sync, and the worker runs
  `CacheRecipeImage` on it: a plain HTTP fetch, then a WebView when that is refused. The attacker
  collects the IP address and sync time of each device without the recipe ever being opened, and
  can force the WebView rung by refusing the HTTP client. That turns the scraper review's open
  items (service-worker and WebSocket requests bypassing `shouldInterceptRequest`, NAT64) from
  "user imports a hostile URL" into "remote, no interaction, all users".
- **Confidence:** confirmed from code for the worker and the candidate query. Reach depends on the
  backend delivering other users' PUBLIC recipes in `/sync/pull` (handover §5 says it does) and on
  whether it validates `imageUrl` on push (open question 1).
- **Fix:** use the URL ladder only for recipes the current user owns; other recipes get the
  backend blob or nothing. Needs `r.creatorId` added to the candidate query's projection and to
  `RecipeImageCandidate`, and `SessionManager` injected into the worker. No migration.
  **Product trade-off:** other users' recipes without a backend blob lose offline images.

```diff
--- a/app/src/main/java/com/tenmilelabs/chefai/recipes/data/worker/RecipeImageBackfillWorker.kt
+++ b/app/src/main/java/com/tenmilelabs/chefai/recipes/data/worker/RecipeImageBackfillWorker.kt
@@ -116,3 +116,8 @@ private suspend fun fetchImage(candidate: RecipeImageCandidate): String? {
-        if (candidate.imageUrl.isEmpty()) return null
+        val currentUserId = sessionManager.getCurrentUserId()
+        if (!mayFetchImageFromUrl(candidate.imageUrl, candidate.creatorId, currentUserId)) return null
         return cacheRecipeImage(candidate.recipeId, candidate.imageUrl)
     }
+
+internal fun mayFetchImageFromUrl(imageUrl: String, creatorId: UUID, currentUserId: UUID?): Boolean =
+    imageUrl.isNotEmpty() && creatorId == currentUserId
```

### Medium

#### A4 — Restore or reinstall defeats account-switch detection, and push is not owner-filtered

- **Location:** `SecurePreferences.kt` `setCurrentUserId` / `getStoredCurrentUserId` (encrypted,
  returns null when decryption fails); `AccountSwitchHandler.kt:61-72`;
  `SyncOrchestrator.kt:193-207` (only bookmarks are filtered by user);
  `data_extraction_rules.xml:7-14`.
- **Failure:** Auto Backup restores `ChefAI.db` and the DataStore after a reinstall or onto a new
  device, but the Keystore key is gone, so `current_user_id` reads as null. A's rows are in Room
  with an Anonymous session. If B then logs in, `handleLogin` sees "no switch" and deletes
  nothing: A's data sits in B's session, and A's `PENDING`/`DELETED` recipes, plans, grocery
  items, cook events and saved plans are pushed with B's token.
- **Confidence:** confirmed from code. The restore contents are **needs verification**:
  `adb shell bmgr backupnow com.tenmilelabs.chefai`, uninstall, reinstall, then check the session
  is Anonymous and `recipes` is non-empty. What the server does with a mismatched `creatorId`
  decides whether this is cross-account data on the server or only on the device (open
  question 2).
- **Fix:** store `current_user_id` in plaintext, as `local_user_id` already is; a UUID is not a
  secret. Filter the recipe push by owner. Configure `<device-transfer>`, which today copies
  everything, `recipe_images/` included.

```diff
--- a/app/src/main/java/com/tenmilelabs/chefai/auth/data/local/SecurePreferences.kt
+++ b/app/src/main/java/com/tenmilelabs/chefai/auth/data/local/SecurePreferences.kt
@@ setCurrentUserId
-                prefs[KEY_CURRENT_USER_ID] = encrypt(userId.toString())
+                prefs[KEY_CURRENT_USER_ID] = userId.toString()
@@ getStoredCurrentUserId
-        prefs[KEY_CURRENT_USER_ID]?.let { encrypted ->
-            try {
-                UUID.fromString(decrypt(encrypted))
-            } catch (e: Exception) {
-                Timber.e(e, "Failed to read current authenticated user ID from preferences")
-                null
-            }
-        }
+        prefs[KEY_CURRENT_USER_ID]?.let { stored ->
+            runCatching { UUID.fromString(stored) }.getOrElse {
+                try {
+                    UUID.fromString(decrypt(stored))
+                } catch (e: Exception) {
+                    Timber.e(e, "Failed to read current authenticated user ID from preferences")
+                    null
+                }
+            }
+        }
--- a/app/src/main/java/com/tenmilelabs/chefai/core/data/sync/SyncOrchestrator.kt
+++ b/app/src/main/java/com/tenmilelabs/chefai/core/data/sync/SyncOrchestrator.kt
@@ -193,2 +193,3 @@ private suspend fun push(): PushResult {
-        val dirtyRecipes = recipeDao.getAllDirty()
+        val ownerId = sessionManager.getCurrentUser()?.uuid ?: return PushResult(0, 0, 0)
+        val dirtyRecipes = recipeDao.getAllDirty().filter { it.creatorId == ownerId }
--- a/app/src/main/res/xml/data_extraction_rules.xml
+++ b/app/src/main/res/xml/data_extraction_rules.xml
@@ -13,2 +13,6 @@
         <exclude domain="file" path="recipe_images/"/>
+        <exclude domain="root" path="app_webview/"/>
     </cloud-backup>
+    <device-transfer>
+        <exclude domain="root" path="app_webview/"/>
+    </device-transfer>
```

Existing installs migrate on the next cold start (`handleLogin` rewrites the key at L127).
Backups taken before that still carry ciphertext. Apply the same filter in
`countPendingChanges`. Meal plans can't be filtered by `userId`, because household members push
plans they don't own, so the backend must enforce those. The `app_webview/` path is **needs
verification** (inspect the restored data dir).

#### A5 — A pending invite token can be planted, never expires and survives logout

- **Location:** `MainActivity.kt:96-100`; `AcceptInviteViewModel.kt:91-94, 126`;
  `SecurePreferences.kt:199-219`.
- **Failure:** any app (explicit `VIEW` intent, no verification needed) or any link opens the
  accept screen with an attacker's token. If the user is anonymous the token is persisted. It is
  cleared only on `Joined`, so it outlives an invalid or declined invite, logout and account
  deletion. The next person to sign in on the device, possibly weeks later, is taken by the app
  itself to "join this household". One tap puts them in the attacker's household and shares
  their plans and shopping lists with it. The same path hands X's genuine invite to Y on a
  shared device.
- **Confidence:** confirmed from code; the join needs a tap. Reproduce with
  `adb shell am start -n com.tenmilelabs.chefai/.MainActivity -a android.intent.action.VIEW -d 'https://pocket-chef.app/invite?token=X'`.
- **Fix:** clear the token on logout, on account deletion and on every terminal outcome. Also add
  a short lifetime (a `pending_invite_saved_at` key, dropped after about 30 minutes), which
  needs an addition to `SecurePreferencesInterface`.

```diff
--- a/app/src/main/java/com/tenmilelabs/chefai/auth/data/local/SecurePreferences.kt
+++ b/app/src/main/java/com/tenmilelabs/chefai/auth/data/local/SecurePreferences.kt
@@ -211,2 +211,3 @@ override suspend fun clearAuthData() {
                 prefs.remove(KEY_PENDING_UPGRADE_FROM)
+                prefs.remove(KEY_PENDING_INVITE_TOKEN)
--- a/app/src/main/java/com/tenmilelabs/chefai/household/ui/AcceptInviteViewModel.kt
+++ b/app/src/main/java/com/tenmilelabs/chefai/household/ui/AcceptInviteViewModel.kt
@@ -91,3 +91,3 @@ fun onAccept() {
-            if (outcome is HouseholdJoinOutcome.Joined) {
+            if (outcome !is HouseholdJoinOutcome.NetworkError) {
                 securePreferences.clearPendingInviteToken()
@@ -109,2 +109,5 @@ private fun loadPreview(token: String) {
                 .onSuccess { preview -> _uiState.value = resolveAfterPreview(token, preview) }
-                .onFailure { error -> _uiState.value = mapPreviewFailure(error) }
+                .onFailure { error ->
+                    if ((error as? HouseholdApiException)?.statusCode == 404) securePreferences.clearPendingInviteToken()
+                    _uiState.value = mapPreviewFailure(error)
+                }
```

#### A6 — A share intent opens a third-party page in the in-app browser with no tap

- **Location:** `MainActivity.kt:71-85`; `ImportRecipeViewModel.kt:41, 73-76, 103-107`.
- **Failure:** any foreground app sends `ACTION_SEND text/plain` to the exported activity. The
  import starts without a tap, and on `NeedsBrowser` the ViewModel navigates straight to the
  visible WebView. The attacker controls the URL and can force that result by refusing automated
  fetches, so their page is displayed inside Pocket Chef, for example a fake "session expired,
  sign in" form.
- **Confidence:** confirmed from code that no confirmation sits between the intent and the
  browser. Whether the browser screen shows the page's origin was not read (open question 5).
  Reproduce with `adb shell am start -n com.tenmilelabs.chefai/.MainActivity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT '<url of a site that blocks bots>'`.
- **Fix:** keep the silent fetch, but require a tap before the visible browser when the URL came
  from a share. Needs a new `ImportRecipeState.browserConfirmUrl`, an `ImportAction` to confirm
  it, and a dialog that names the host.

```diff
--- a/app/src/main/java/com/tenmilelabs/chefai/recipes/ui/urlimport/ImportRecipeViewModel.kt
+++ b/app/src/main/java/com/tenmilelabs/chefai/recipes/ui/urlimport/ImportRecipeViewModel.kt
@@ -73,4 +73,8 @@ private fun import() {
                 is RecipeImportResult.NeedsBrowser -> {
-                    _state.update { it.copy(isImporting = false) }
-                    _effects.send(ImportEffect.NavigateToBrowserImport(result.url))
+                    if (url == prefillUrl) {
+                        _state.update { it.copy(isImporting = false, browserConfirmUrl = result.url) }
+                    } else {
+                        _state.update { it.copy(isImporting = false) }
+                        _effects.send(ImportEffect.NavigateToBrowserImport(result.url))
+                    }
                 }
```

#### A7 — Coil loads server-supplied image URLs from any host and scheme

- **Location:** `ImageLoaderModule.kt:56-91`; `SyncMapper.kt:54, 145`.
- **Failure:** the author of a PUBLIC recipe learns the IP address and viewing time of everyone
  who displays it (a tracking pixel). A server-supplied `file://` or `content://` value is
  handed to Coil's local fetchers. Nothing is exfiltrated, because the result is only drawn, but
  it is an unintended read and a decode-bomb surface. LAN and loopback targets are mostly closed
  off, since release builds deny cleartext.
- **Confidence:** confirmed that no restriction exists. Which fetchers Coil 3.3.0 registers by
  default is **needs verification**: set a recipe's `imageUrl` to a `content://` URI in a debug
  database and watch the interceptor log.
- **Fix:** restrict string models to https and app-private files. Check that call sites pass the
  raw `String` and that `localImagePath` values all live under `filesDir`. The tracking pixel
  needs a **backend change** (serve every recipe image from the blob store or a proxy).

```diff
--- a/app/src/main/java/com/tenmilelabs/chefai/core/di/ImageLoaderModule.kt
+++ b/app/src/main/java/com/tenmilelabs/chefai/core/di/ImageLoaderModule.kt
@@ -63,2 +63,5 @@ override suspend fun intercept(chain: Interceptor.Chain): coil3.request.ImageResult {
                         val data = request.data
+                        require(isAllowedImageModel(data, context.filesDir.path, BuildConfig.DEBUG)) {
+                            "Blocked image source"
+                        }
@@ -108,0 +112,8 @@
+
+internal fun isAllowedImageModel(data: Any?, appFilesDir: String, allowCleartext: Boolean): Boolean {
+    if (data !is String) return true
+    return data.startsWith("https://") ||
+        (allowCleartext && data.startsWith("http://")) ||
+        data.startsWith("file://$appFilesDir/") ||
+        data.startsWith("$appFilesDir/")
+}
```

### Low

#### A8 — A failed logout resurrects the session on the next launch

- **Location:** `SessionManager.kt:539, 555-566`.
- **Failure:** if `clearAuthData()` throws (DataStore I/O), the catch block shows Anonymous, but
  the tokens stay on disk and the refresh token is never revoked. The next cold start signs A
  back in on a device they believe they left.
- **Confidence:** confirmed from code; needs a storage failure to trigger.

```diff
--- a/app/src/main/java/com/tenmilelabs/chefai/auth/domain/SessionManager.kt
+++ b/app/src/main/java/com/tenmilelabs/chefai/auth/domain/SessionManager.kt
@@ -525,5 +525,5 @@
     suspend fun logout(): Unit = sessionEndMutex.withLock {
+        val refreshTokenToRevoke = (_userSession.value as? UserSession.Authenticated)?.authToken?.refreshToken
         try {
             val currentUser = getCurrentUser()
-            val refreshTokenToRevoke = (_userSession.value as? UserSession.Authenticated)?.authToken?.refreshToken
@@ -555,2 +555,5 @@
         } catch (e: Exception) {
             Timber.e(e, "Error during logout")
+            runCatching { securePreferences.clearAuthData() }
+                .onFailure { Timber.e(it, "Logout: stored session could not be cleared") }
+            refreshTokenToRevoke?.let { token -> applicationScope.launch { revokeRefreshToken(token) } }
```

#### A9 — The rotated refresh token is written in a second, separate edit

- **Location:** `SessionManager.kt:682-690`; `SecurePreferences.updateAccessToken` /
  `updateRefreshToken`.
- **Failure:** process death between the two edits leaves a new access token with the old,
  already-rotated refresh token. The next refresh presents a consumed token; a backend with reuse
  detection revokes the session and the user must sign in again (this compounds backend F8).
- **Confidence:** confirmed from code; the window is small.
- **Fix:** one edit. Adds `updateTokens` to `SecurePreferencesInterface` and its fake (internal).

```diff
--- a/app/src/main/java/com/tenmilelabs/chefai/auth/domain/SessionManager.kt
+++ b/app/src/main/java/com/tenmilelabs/chefai/auth/domain/SessionManager.kt
@@ -682,9 +682,5 @@
-                securePreferences.updateAccessToken(
-                    accessToken = newAuthToken.accessToken,
-                    tokenExpiry = newAuthToken.expiresAt
-                )
-                securePreferences.updateRefreshToken(
-                    refreshToken = newAuthToken.refreshToken
-                )
+                securePreferences.updateTokens(
+                    accessToken = newAuthToken.accessToken,
+                    refreshToken = newAuthToken.refreshToken,
+                    tokenExpiry = newAuthToken.expiresAt,
+                )
```

#### A10 — Process death after a successful account deletion leaves the data on the device

- **Location:** `DeleteAccountUseCase.kt:41-44`; `SessionManager.kt:619-629`.
- **Failure:** the server deletes the account, then the app dies before
  `endDeletedAccountSession`. The next cold start loads the stored session, the refresh is
  rejected, and the deleted account's rows stay in Room, visible through A1.
- **Confidence:** confirmed from code; narrow window.
- **Fix:** a plaintext `pending_account_wipe` marker (new preference key), set before the request,
  cleared on a definitive non-deletion, and honoured by `loadSession` when the refresh is
  rejected. `setPendingAccountWipe` is a new `SessionManager` pass-through.

```diff
--- a/app/src/main/java/com/tenmilelabs/chefai/auth/domain/usecase/DeleteAccountUseCase.kt
+++ b/app/src/main/java/com/tenmilelabs/chefai/auth/domain/usecase/DeleteAccountUseCase.kt
@@ -41,4 +41,11 @@
+        sessionManager.setPendingAccountWipe(true)
         val result = accountRepository.deleteAccount(password, disposition)
-        if (result == DeleteAccountResult.Deleted) {
-            endLocalSession()
+        when (result) {
+            DeleteAccountResult.Deleted -> endLocalSession()
+            DeleteAccountResult.WrongPassword,
+            DeleteAccountResult.Conflict,
+            DeleteAccountResult.RateLimited,
+            is DeleteAccountResult.PublicRecipeChoiceRequired -> sessionManager.setPendingAccountWipe(false)
+            else -> Unit // Failed / SessionExpired: the server may have deleted; keep the marker
         }
```

#### A11 — Task hijacking on Android 9–10

- **Location:** `AndroidManifest.xml:19-22`.
- **Failure:** `singleTask` with the default task affinity lets a malicious app on API 28–29
  place its own activity in Pocket Chef's task and show a fake login when the user opens the app.
  Patched by the platform from Android 11.
- **Confidence:** needs verification on an API 28 or 29 emulator.

```diff
--- a/app/src/main/AndroidManifest.xml
+++ b/app/src/main/AndroidManifest.xml
@@ -21,2 +21,3 @@
             android:exported="true"
+            android:taskAffinity=""
             android:launchMode="singleTask">
```

#### A12 — Invite App Link verification is unconfirmed

- **Location:** `AndroidManifest.xml:45-54`.
- **Failure:** if `https://pocket-chef.app/.well-known/assetlinks.json` is missing or wrong,
  invite links open in the browser on Android 12+ (the flow breaks), and on Android 9–11 another
  app declaring the same filter can appear in the chooser and capture the bearer invite token.
- **Confidence:** needs verification: `adb shell pm get-app-links com.tenmilelabs.chefai` should
  report `verified` for the host.
- **Fix:** deploy the asset links file with the release signing certificate's SHA-256. This is a
  hosting change, so there is no client diff.

#### A13 — Debug test credentials may ship in the release dex

- **Location:** `LoginViewModel.kt:74-83`; `app/build.gradle.kts:52`.
- **Failure:** the `if (BuildConfig.DEBUG)` gate works at runtime, but with minification off the
  three email/password literals may still be in the release APK. If those accounts exist on
  production, anyone can sign in to them.
- **Confidence:** needs verification:
  `unzip -p app-release.apk 'classes*.dex' | strings | grep 'test123!'`.
- **Fix:** move the list into the debug source set behind a main-source-set stub.

```diff
--- a/app/src/main/java/com/tenmilelabs/chefai/auth/ui/LoginViewModel.kt
+++ b/app/src/main/java/com/tenmilelabs/chefai/auth/ui/LoginViewModel.kt
@@ -74,10 +74,1 @@
-    private val testAccounts: List<EmailSuggestion> = if (BuildConfig.DEBUG) {
-        listOf(
-            EmailSuggestion("test1@ex.com", "test123!"),
-            EmailSuggestion("test2@ex.com", "test123!"),
-            EmailSuggestion("test3@ex.com", "test123!")
-        )
-    } else {
-        emptyList()
-    }
+    private val testAccounts: List<EmailSuggestion> = debugTestAccounts()
```

`debugTestAccounts()` lives in `app/src/debug/.../DebugTestAccounts.kt` (the list) and
`app/src/release/.../DebugTestAccounts.kt` (`emptyList()`).

### Checked, no finding

- **Keystore and GCM:** fresh random 12-byte IV per encryption, 128-bit tag, key never leaves the
  Keystore. Decryption failures return null rather than crash.
- **Token attachment:** exact scheme, host and port match.
- **Invite token transport:** sent as a query parameter and a JSON body (`HouseholdApiService.kt:
  111-123`), so a crafted token can't alter the request path. `InviteLinkParser` rejects
  userinfo and look-alike hosts.
- **Interrupted upgrade (T3):** every await point in login re-runs idempotently from the
  `pending_upgrade_from` marker.
- **Release exposure:** Developer options, the environment interceptor and Ktor logging are
  compile-time gated; Timber plants no tree; cleartext is denied by the platform default.

## 3. Tests

The fake and fixture member names below (`createSessionManager()`, `refreshError`, the in-memory
database helpers) are assumed. The fakes were not read, so adapt them to `SessionManagerTest` and
`AccountSwitchHandlerDatabaseTest`.

**A1** — instrumented, because the assertion is on Room rows (extend
`AccountSwitchHandlerDatabaseTest`):

```kotlin
@Test
fun purgeAccountData_removesDepartingAccountsPrivateRows_keepsAnonymousRows() = runTest {
    // Given an account's private recipe, another creator's private recipe and an anonymous recipe
    insertRecipe(id = accountRecipeId, creatorId = accountId, privacy = "PRIVATE")
    insertRecipe(id = sharedRecipeId, creatorId = otherCreatorId, privacy = "PRIVATE")
    insertRecipe(id = anonymousRecipeId, creatorId = anonymousId, privacy = "PRIVATE")

    // When the account's data is purged, as logout now does
    handler.purgeAccountData(departingUserId = accountId, keepOwnedBy = anonymousId)

    // Then only the anonymous recipe is left for the next person to see
    val remaining = recipeDao.observeRecipesWithDetails().first().map { it.recipe.uuid }
    assertEquals(listOf(anonymousRecipeId), remaining)
}
```

**A2** — JVM, in `SessionManagerTest`:

```kotlin
@Test
fun `rate-limited refresh at cold start keeps the session authenticated`() = runTest {
    // Given a stored session whose access token has expired
    securePreferences.saveAuthData(userId, "Ana", "ana@example.com", "", "access", "refresh", 0L, null)
    authNetwork.refreshError = AuthHttpException("Too many requests", 429)

    // When the session is loaded
    val sessionManager = createSessionManager()
    sessionManager.loadSession()

    // Then the user is still signed in
    assertTrue(sessionManager.userSession.value is UserSession.Authenticated)
}

@Test
fun `rejected refresh at cold start ends the session and clears the stored tokens`() = runTest {
    // Given a stored session whose refresh token the server rejects
    securePreferences.saveAuthData(userId, "Ana", "ana@example.com", "", "access", "refresh", 0L, null)
    authNetwork.refreshError = AuthHttpException("Invalid refresh token", 401)

    // When the session is loaded
    val sessionManager = createSessionManager()
    sessionManager.loadSession()

    // Then the session is anonymous and no token is left to retry with
    assertTrue(sessionManager.userSession.value is UserSession.Anonymous)
    assertNull(securePreferences.getRefreshToken().first())
}
```

The first fails today on the session state, the second on the leftover token.

**A3** — JVM. A regression test, since the function is new; the instrumented equivalent asserts
`cacheRecipeImage` is not called for a recipe with another `creatorId`.

```kotlin
@Test
fun `url ladder is used only for recipes the current user owns`() {
    // Given a recipe authored by someone else and one authored by the current user
    val me = UUID.randomUUID()
    val someoneElse = UUID.randomUUID()

    // When / Then
    assertFalse(mayFetchImageFromUrl("https://attacker.example/x.png", someoneElse, me))
    assertFalse(mayFetchImageFromUrl("https://attacker.example/x.png", someoneElse, null))
    assertTrue(mayFetchImageFromUrl("https://food.example/cake.jpg", me, me))
    assertFalse(mayFetchImageFromUrl("", me, me))
}
```

## 4. Backend cross-check

- **Q1:** no refresh failure ever clears tokens or calls `logout`. During a sync, search or
  account call, a 401, 429 or 5xx from `/auth/refresh` leaves the session Authenticated and the
  call fails or falls back. At cold start with an expired access token, a 5xx or a non-JSON error
  body is transient, while any 4xx with a JSON body (401, 429) makes the user Anonymous for that
  process with the tokens left on disk (A2).
- **Q2:** `imageUrl` is pushed and stored exactly as held, with no scheme check in either
  direction (`SyncMapper.kt:54, 145`), so the backend must validate it. `recipeExternalUrl` is
  never opened or rendered as a link, and descriptions are plain `Text`: no `openUri`,
  `ACTION_VIEW` launch, `fromHtml`, `Linkify` or `LinkAnnotation` exists in `:app` main sources.
- **F1 (shared rate-limit bucket):** worse than an availability bug if the 429 body is JSON:
  everyone who cold-starts during the window looks signed out and can create mis-owned rows.
  It recovers on the next cold start, and A2's fix removes the client side.
- **F8 (transient DB error treated as theft):** confirmed as a forced re-login for each affected
  user, noticed at their next cold start, not before. No local data is lost and pending changes
  push after they sign in again. The client can't mitigate a real revocation, so F8's severity
  stands. A9 adds a second way to trip it.

## 5. Open questions

1. Does `/sync/pull` deliver other users' PUBLIC recipes to every account unprompted, and does
   the backend validate `imageUrl` on push? If both answers are safe, A3 and A7 drop to Low.
2. On push, does the backend take a recipe's owner from the token or from the DTO's `creatorId`,
   and does it reject a mismatch? This decides whether A4 puts A's data in B's server account.
3. What status does `/auth/refresh` return for an unknown or expired token (400 or 401), is its
   429 body JSON, and does the 426 protocol gate cover `/auth/*`? This sets A2's rejection list
   and whether old clients are stranded by a version bump.
4. Which stream does the library screen bind: `getRecipesPreviewStream()` (unfiltered) or the
   `ForUser` variant? If filtered, A1's exposure narrows to local search and the on-disk copy.
5. Does the in-app browser screen show the page's host? If it does, A6 drops to Low.
6. Can Room hold non-PUBLIC recipes authored by another household member? If so, A1's
   switch-time residue is private data rather than only stale rows.

## 6. Not reviewed

- **`CacheRecipeImage` and its URL validation** as applied to server-supplied URLs: covered by
  the scraper review, not re-read. A3 assumes its SSRF guards hold.
- **Image upload worker:** `RecipeDao.getImageUploadCandidates` (L242-254) has no owner filter;
  the worker that consumes it was not read.
- **`LoginViewModel` / `RegisterViewModel`:** the double-submit guard and the pending-invite
  resume. A login started while the session is `Loading` has `anonymousUserId == null`, writes no
  upgrade marker, and can reach `clearAllTables()`; whether the UI allows that was not checked.
- **`UserDao`, `SavedMealPlanDao`, `BookmarkedRecipeDao`, `RecipeCookEventDao`,
  `ShoppingListCheckDao`:** the queries behind the switch cleanup, and who owns cook events,
  cooked marks and grocery checks.
- **`HouseholdRepository.clearLocalCache`** and what the household cache holds (member names,
  emails).
- **The browser-import screen** (WebView chrome and settings) and **`ShareTextUrlExtractor`**.
- **`SyncMapper.toRecipeEntity`** beyond the `imageUrl` lines: field-size limits on pulled
  content.
- **`PickedImageStorage`, `RecipeImageStore`, `SavedPlanImageStore`.**
- **WebView profile contents** under `app_webview/` and whether backups carry them.
- **Release signing and the `debuggable` flag** of distributed builds.
- **Test fakes** (`FakeSecurePreferences`, `FakeAuthNetworkDataSource`): the tests in §3 assume
  their API.

**Files read beyond the handover (8):** `SyncOrchestrator.kt`, `RecipeDao.kt`, `MealPlanDao.kt`,
`SecurePreferences.kt`, `ImportRecipeViewModel.kt`, `HouseholdApiService.kt`,
`DefaultRecipeRepository.kt`, `RecipeImageBackfillWorker.kt`. Plus two tree-wide greps for Q2
(link/HTML APIs, `imageUrl` in the sync mapper) and one for owner fields in `SyncDtos.kt`.
