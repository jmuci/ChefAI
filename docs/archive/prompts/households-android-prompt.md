# Android Prompt: Households — Shared Meal Plans & Grocery Lists

> Generated: September 2026
> Companion backend prompt: `docs/prompts/households-backend-prompt.md`
> Target repo: this one (`ChefAI`) — Kotlin, Compose, Hilt, Room, Ktor client

---

## Context

From their profile, a user can create a household and invite others — by share link, by manual
code, or by email for an existing user. All members see and edit **the same meal plan and its
grocery list**. Personal recipe libraries stay personal.

Read `CLAUDE.md` and `docs/architecture.md` first. Everything below assumes the house rules:
Kotlin only, no `!!`, MVI-ish unidirectional flow, stateless hoisted composables, `StateFlow<UiState>`
with sealed state, Hilt constructor injection, Room as local SSOT, previews in light + dark,
`modifier: Modifier = Modifier` last.

**No new dependencies are required anywhere in this feature.** If you think you need one, stop and
ask first.

---

## 0. Settled contract

The backend is being built against `docs/prompts/households-backend-prompt.md`. These points were
mismatches between the two plans and are now resolved — build against the resolved version.

1. **`SyncMealPlanDto` gains `ownerId` and `householdId`.** See §5.1 — this fixes a real latent bug.
2. **Pulled `creators` includes meal-plan owners**, so the local FK on owner id resolves. Backend
   guarantees this; you may rely on it.
3. **Grocery items use an explicit `checked: Boolean`**, not tombstone-on-uncheck. An uncheck is an
   ordinary LWW update. `deletedAt` means only "this item left the list". Wire field is
   `groceryListItems`.
4. **Accepting an in-app invite uses `POST /households/invites/{inviteId}/accept`** — no token
   needed. `POST /households/join` with `{token}` is the link path only.
5. **Declining uses `POST /households/invites/{inviteId}/decline`.**
6. **Do NOT reset the sync cursor on join.** The server bumps `server_updated_at` on household rows
   at accept time, and referenced recipes arrive via the gap clause, which ignores the cursor. A
   client-side reset would force a needless full re-pull of the entire corpus.
7. A departing member's own plans are un-shared server-side too, so local and server agree.

---

## 1. Architectural decisions (record these in the ADR)

1. **Household membership lives on `UserSession.Authenticated`**, not a parallel `StateFlow`. Every
   existing `when (session)` call site keeps compiling, and "no household" is representable as
   `null` rather than a separate absent/loading distinction.
2. **Household CRUD is plain REST, not the sync protocol.** Create/invite/accept/leave/remove are
   authorization operations, not offline-editable data. The local `households` / `household_members`
   tables are a **read-through cache** (wholesale replace on fetch, same idiom as `meal_plan_days`),
   **not** `SyncableEntity` rows — there is nothing to push.
   *Consequence:* a removed member sees stale membership until the next refresh. That is acceptable
   only because the server treats their stale state as non-authoritative. **The client cache must
   never be a security boundary.**
3. **Only `meal_plans` and grocery-list state cross the sync boundary for sharing.** The recipe DTO
   is untouched.
4. **No active-household switcher**, because a user belongs to at most one household.

---

## 2. Room Migration v8 → v9

New entities (`core/data/local/room/`):

```kotlin
@Entity(tableName = "households")
data class HouseholdEntity(
    @PrimaryKey val uuid: UUID,
    val name: String,
    val ownerId: UUID,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "household_members",
    primaryKeys = ["householdId", "userId"],
    indices = [Index("householdId")],
)
data class HouseholdMemberEntity(
    val householdId: UUID,
    val userId: UUID,
    val displayName: String,
    val avatarUrl: String,
    val role: String,        // "OWNER" | "MEMBER"
    val joinedAt: Long,
)

/** Invites addressed to the signed-in user — the in-app "you've been invited" inbox. */
@Entity(tableName = "household_invites")
data class HouseholdInviteEntity(
    @PrimaryKey val inviteId: UUID,
    val householdId: UUID,
    val householdName: String,
    val inviterDisplayName: String,
    val createdAt: Long,
)
```

Note there is **no `token` column** — accepting an in-app invite goes by `inviteId` (§0.4), so the
client never holds a raw token for these.

**No FK from `MealPlanEntity.householdId` to `households.uuid`.** The household tables are
wholesale-replaced on every refresh; a real FK would either cascade-delete plans on refresh or block
the refresh. Same reasoning as the existing absence of a local FK on `meal_plan_days.dinnerRecipeId`.

```kotlin
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `households` (
                `uuid` BLOB NOT NULL, `name` TEXT NOT NULL, `ownerId` BLOB NOT NULL,
                `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`uuid`)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `household_members` (
                `householdId` BLOB NOT NULL, `userId` BLOB NOT NULL, `displayName` TEXT NOT NULL,
                `avatarUrl` TEXT NOT NULL, `role` TEXT NOT NULL, `joinedAt` INTEGER NOT NULL,
                PRIMARY KEY(`householdId`, `userId`)
            )
        """.trimIndent())
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_household_members_householdId` " +
                "ON `household_members` (`householdId`)"
        )
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `household_invites` (
                `inviteId` BLOB NOT NULL, `householdId` BLOB NOT NULL,
                `householdName` TEXT NOT NULL, `inviterDisplayName` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`inviteId`)
            )
        """.trimIndent())

        // Every existing plan is personal.
        db.execSQL("ALTER TABLE meal_plans ADD COLUMN householdId BLOB")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_meal_plans_householdId` " +
                "ON `meal_plans` (`householdId`)"
        )

        // shopping_list_checks becomes a SyncableEntity.
        // Backfill SYNCED, not PENDING: these are local ticks made before the server knew this
        // feature existed. Pushing them as brand-new rows would be a lie, and the server may
        // reject them for a plan it doesn't consider this device's to edit. They stay local facts
        // until the next real toggle marks them PENDING. updatedAt backfills from checkedAt — the
        // closest fact the row already carries about "when".
        db.execSQL("ALTER TABLE shopping_list_checks ADD COLUMN checked INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE shopping_list_checks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE shopping_list_checks ADD COLUMN deletedAt INTEGER")
        db.execSQL("ALTER TABLE shopping_list_checks ADD COLUMN syncState TEXT NOT NULL DEFAULT 'SYNCED'")
        db.execSQL("UPDATE shopping_list_checks SET updatedAt = checkedAt")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shopping_list_checks_syncState_updatedAt` " +
                "ON `shopping_list_checks` (`syncState`, `updatedAt`)"
        )
    }
}
```

`ShoppingListCheckEntity` now implements `SyncableEntity` and gains `checked: Boolean`. Its existing
`ON DELETE CASCADE` from `meal_plans` is untouched and still correct — a locally deleted plan should
take its checks with it. Room migrations cannot alter an FK clause anyway.

**Important behavioural change:** with an explicit `checked` column, unchecking an item is now an
`UPDATE ... SET checked = 0, syncState = 'PENDING'`, **not** a row delete. This aligns the entity
with the `SyncableEntity` convention instead of deviating from it.

---

## 3. Domain & Data Layers

New feature package `household/` (per ADR-005 — start in the feature package, promote to `core/`
only when a second feature needs it).

```kotlin
// household/domain/model/
enum class HouseholdRole { OWNER, MEMBER }

data class Household(
    val uuid: UUID,
    val name: String,
    val ownerId: UUID,
    val members: List<HouseholdMember>,
)

data class HouseholdMember(
    val userId: UUID,
    val displayName: String,
    val avatarUrl: String,
    val role: HouseholdRole,
)

data class PendingHouseholdInvite(
    val inviteId: UUID,
    val householdName: String,
    val inviterDisplayName: String,
)

data class HouseholdInviteLink(val url: String, val manualCode: String, val expiresAt: Long?)

sealed interface HouseholdJoinOutcome {
    data class Joined(val household: Household) : HouseholdJoinOutcome
    data object InvalidOrExpired : HouseholdJoinOutcome
    data object AlreadyInAHousehold : HouseholdJoinOutcome
}

// household/domain/repository/HouseholdRepository.kt
interface HouseholdRepository {
    /** Cache-first; emits local cache immediately, refreshed by [refresh]. Null = no household. */
    fun observeMyHousehold(userId: UUID): Flow<Household?>
    fun observePendingInvites(): Flow<List<PendingHouseholdInvite>>

    suspend fun refresh(): Result<Unit>
    suspend fun createHousehold(name: String): Result<Household>
    suspend fun leaveHousehold(): Result<Unit>
    suspend fun removeMember(userId: UUID): Result<Unit>
    suspend fun createInviteLink(): Result<HouseholdInviteLink>
    suspend fun inviteByEmail(email: String): Result<Unit>
    suspend fun previewInvite(token: String): Result<PendingHouseholdInvite>
    suspend fun joinWithToken(token: String): Result<HouseholdJoinOutcome>
    suspend fun acceptInvite(inviteId: UUID): Result<HouseholdJoinOutcome>
    suspend fun declineInvite(inviteId: UUID): Result<Unit>
}
```

`HouseholdApiService` mirrors `AuthApiService`'s shape — direct Ktor calls, `expectSuccess = false`
with manual status checks, no dirty queue. `getMyHousehold()` maps `404` to `null`, not an exception.

`DefaultHouseholdRepository.refresh()` calls `getMyHousehold()` + `getPendingInvites()` and, in one
`transactionRunner` block, replaces the cache: `clearMembers(id)` + `upsertMembers(...)`, and diffs
invites so one the server no longer lists is removed locally.

### Session wiring

```kotlin
data class Authenticated(
    val user: User,
    val authToken: AuthToken,
    val household: AuthenticatedHousehold? = null,
) : UserSession()

/** Present iff the user belongs to a household — at most one, so no switcher. */
data class AuthenticatedHousehold(val householdId: UUID, val role: HouseholdRole)
```

`SessionManager` takes a `Provider<HouseholdRepository>` (avoiding a circular DI edge, same pattern
as the existing `accountUpgradeUseCaseProvider`). In `loadSession()` / `login()` / `register()`,
after `persistAuthenticatedUser(user)`, call a private `attachHouseholdState(user)` that reads the
**local cache only** — never a network call on the hot session-restore path. Fire
`householdRepository.get().refresh()` in the background the same way `requestImmediateSync()` already
is, and re-emit `_userSession.value` when it lands.

`AccountSwitchHandler` needs no change — `database.clearAllTables()` already covers the new tables
once registered. Add a test asserting it, because that is exactly the kind of accidental correctness
that regresses silently. `AccountUpgradeUseCase` also needs no change: anonymous sessions never have
household rows. Add a doc comment recording that invariant so nobody later "fixes" a non-bug.

---

## 4. UI

| Screen | Entry | States |
|---|---|---|
| **Household** | Profile menu → "Household" (`Authenticated` only) | `Loading`; `NoHousehold` (create CTA + pending-invite inbox + "enter a code" CTA); `Success` (members, owner-only invite/remove, leave); `Error` |
| **Create household** | Inline in `NoHousehold` — one text field, no navigation | blank-name validation |
| **Invite via link** | Owner-only button → `ACTION_SEND` | Android's own share sheet *is* the UI |
| **Invite by email** | Owner-only secondary action, small dialog | success snackbar; inline error (already a member, invalid email, no such user) |
| **Enter invite code** | `NoHousehold` secondary CTA, or from an invalid-link state | code field → same accept flow |
| **Accept invite** | App Link tap, code entry, or a pending-invite card | `Loading`; `Preview`; `InvalidOrExpired`; `AlreadyInAHousehold`; `Joined`; `RequiresSignIn` |
| **Meal plan list / detail** | unchanged | adds a "Shared" badge + owner name when `householdId != null` |
| **Shopping list** | unchanged | same badge in the header |

```kotlin
sealed interface HouseholdUiState {
    data object Loading : HouseholdUiState
    data object NoHousehold : HouseholdUiState
    data class Success(
        val household: Household,
        val myRole: HouseholdRole,
        val isRefreshing: Boolean,
    ) : HouseholdUiState
    data class Error(val message: String) : HouseholdUiState
}

sealed interface HouseholdEvent {
    data class ShowError(val message: String) : HouseholdEvent
    data class ShareInviteLink(val link: HouseholdInviteLink) : HouseholdEvent
    data object LeftHousehold : HouseholdEvent
}
```

Composables are stateless + previewable, with a thin ViewModel-resolving entry point — the split
`FloatingRecipeTimerWidget` already uses:

```kotlin
@Composable
fun HouseholdContent(
    household: Household,
    myRole: HouseholdRole,
    onInviteClick: () -> Unit,
    onInviteByEmail: (String) -> Unit,
    onRemoveMember: (UUID) -> Unit,
    onLeaveClick: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun HouseholdMemberRow(
    member: HouseholdMember,
    canRemove: Boolean,   // myRole == OWNER && member.role != OWNER
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Owner-only controls are driven off session role. Leaving is available to everyone including the
owner; only invite and remove are owner-exclusive. Previews: light + dark, for both OWNER and
MEMBER, plus the empty state.

---

## 5. Sync Changes

### 5.1 The ownership bug — fix this carefully

`SyncMealPlanDto` has no owner field, and `SyncMapper.kt:239` stamps `userId = <the caller>`. That
is harmless today because you only ever pull your own plans. The instant a shared plan arrives it
silently reassigns ownership on the pulling device — corrupting the column that is the FK target for
the `users` cascade and what `AccountUpgradeUseCase` / `AccountSwitchHandler` key off.

```kotlin
// BEFORE: fun SyncMealPlanDto.toMealPlanEntity(userId: UUID): MealPlanEntity
// AFTER — the DTO is self-describing; drop the parameter at every call site.
fun SyncMealPlanDto.toMealPlanEntity(): MealPlanEntity = MealPlanEntity(
    uuid = UUID.fromString(uuid),
    userId = UUID.fromString(ownerId),
    householdId = householdId?.let(UUID::fromString),
    name = name, status = status, preferencesJson = preferencesJson,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    syncState = SyncState.SYNCED,
)
```

Add a regression test: pulling a plan whose `ownerId != authenticatedUserId` must persist
`MealPlanEntity.userId == ownerId`.

Also fix the documented gap where `applyPulledMealPlan` is skipped when there is no authenticated
user. Households require auth, so this is really "assert the invariant", not a behaviour change for
anonymous sessions.

### 5.2 Grocery list

```kotlin
@Serializable
data class SyncGroceryListItem(
    val mealPlanId: String,
    val itemKey: String,
    val checked: Boolean,
    val checkedBy: String?,
    val updatedAt: Long,
    val deletedAt: Long?,
)
```

`SyncPushRequest` / `SyncPullResponse` each gain `groceryListItems`, defaulted to `emptyList()`.

```kotlin
private suspend fun applyPulledGroceryListItem(dto: SyncGroceryListItem) {
    val mealPlanId = UUID.fromString(dto.mealPlanId)
    // Mirrors applyPulledBookmark's dangling-FK guard: an item for a plan this device hasn't
    // received yet is skipped, not crashed on. The same pull cycle delivers the plan, and the
    // next one re-delivers this.
    if (mealPlanDao.getMealPlanById(mealPlanId) == null) {
        Timber.w("applyPulledGroceryListItem: unknown mealPlanId %s, skipping", mealPlanId)
        return
    }
    val local = shoppingListCheckDao.getCheck(mealPlanId, dto.itemKey)
    if (local != null && local.syncState == SyncState.PENDING && local.updatedAt > dto.updatedAt) {
        return // local is newer — same LWW shape as applyPulledRecipe
    }
    shoppingListCheckDao.upsert(dto.toEntity())
}
```

`DefaultShoppingListRepository.setChecked` now stamps `syncState = PENDING`, `updatedAt = now`, sets
`checked`, and calls `syncScheduler.requestMutationSync()` — the same "every local mutation nudges
sync" idiom `DefaultMealPlanRepository.createMealPlan` already follows.

New DAO queries: `getAllDirty()` (`syncState IN ('PENDING','DELETED')`), `getCheck(mealPlanId, itemKey)`,
`updateSyncState(...)`.

### 5.3 Leave / removal cleanup

```kotlin
/** Drops shared plans this device no longer has any claim to. A plan the leaving user owns is
 *  kept, with its household link cleared — the server does the same, so the two agree. */
@Query("DELETE FROM meal_plans WHERE householdId = :householdId AND userId != :keepOwnedBy")
suspend fun deleteHouseholdPlansNotOwnedBy(householdId: UUID, keepOwnedBy: UUID)

@Query("UPDATE meal_plans SET householdId = NULL WHERE householdId = :householdId AND userId = :userId")
suspend fun clearHouseholdLinkForOwnPlans(householdId: UUID, userId: UUID)
```

`leaveHousehold()` → call the API, then in one `transactionRunner`: both queries above, then
`householdDao.deleteHousehold(id)`. Local deletes here are **hard**, not soft — pushing a `DELETED`
state for a plan owned by somebody else is not a request this client is authorized to make.

**Do not add a cursor reset on join** (§0.6).

---

## 6. App Links

The manifest has no deep links today — only `MAIN` and an `ACTION_SEND` share target. This is the
first one.

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https" android:host="<CONFIRM_HOST>" android:pathPrefix="/invite" />
</intent-filter>
```

Follow the existing `MainActivity.consumeShareIntent` pattern rather than pulling in nav deep-link
machinery — capture the intent, stash it, let the nav graph react via `LaunchedEffect`:

```kotlin
private var pendingInviteToken by mutableStateOf<String?>(null)

private fun consumeInviteIntent(intent: Intent) {
    if (intent.action != Intent.ACTION_VIEW) return
    val token = InviteLinkParser.extractToken(intent.data) ?: return
    pendingInviteToken = token
    intent.data = null   // mirrors the EXTRA_TEXT removal — survives recreation without re-firing
}
```

Call from both `onCreate` and `onNewIntent`. `InviteLinkParser` is a pure function in `core/util/`,
unit-testable without Android — mirrors the existing `extractSharedRecipeUrl`.

**Out of scope for v1:** deferred deep linking (the app-not-installed case). The manual invite code
is the acknowledged fallback. Record this in the ADR.

**Outside this repo:** `https://<host>/.well-known/assetlinks.json` must be served with the app's
SHA-256 signing cert fingerprint, or verification silently fails and Android shows a disambiguation
dialog instead of opening the app. Deploy-pipeline task — flag it, don't assume it.

---

## 7. Anonymous → Authenticated While Holding an Invite

The messiest flow in the feature. Be explicit; do not improvise.

1. Anonymous user taps an invite link. The token is stashed in memory **and persisted** via
   `SecurePreferencesInterface.savePendingInviteToken(token)`. In-memory alone does not survive
   process death, and the sign-up round trip is exactly when the OS reclaims the process.
2. `AcceptInviteViewModel` resolves `RequiresSignIn(token)` for an anonymous session. The UI shows a
   short explainer and Login/Register buttons — **not** a silent redirect. The user's anonymous
   recipes are real data and deserve a beat of visibility before anything happens to them.
3. Login/Register ViewModels read the pending token from `SecurePreferencesInterface` directly,
   rather than threading one more nav arg through every existing call site.
4. On success, `AccountUpgradeUseCase.execute()` already runs and **completes** inside
   `SessionManager.login()`/`register()` before `Result.success(user)` returns — it is awaited, not
   fire-and-forget. So by the time `onSuccess` fires, the anonymous→auth reassignment transaction is
   done and `getCurrentUserId()` is stable. Only then attempt the join.

   **Caveat, verified in the code:** that upgrade call is wrapped in a `try/catch` that logs and
   swallows — "Account upgrade failed, but login succeeded." So a successful login does **not**
   prove the reassignment happened. The join must not assume it did: it only needs a stable
   authenticated user id, which it has either way. Do not add a dependency on upgrade success, and
   do not "fix" the swallow as part of this feature.
5. On success, navigate to the accept screen instead of home. Clear the stored token **only** once
   the join itself succeeds, so a crash mid-join resumes on next launch.
6. If the user abandons sign-up, leave the token stored deliberately — returning later and logging
   in resumes the join. Clear it only on explicit decline or successful accept.

**What happens to the anonymous user's existing data:** nothing invite-specific. It goes through the
existing, unmodified `AccountUpgradeUseCase` path exactly as any other upgrade does. State this
plainly in the ADR, because it is the one place someone will be tempted to design a "merge my
recipes into the household" flow. That is explicitly rejected: **joining a household never touches
the joiner's personal recipes or personal meal plans.** It only makes the household's shared plan
newly visible.

---

## 8. Offline Behaviour & Conflicts

Document in the ADR; most of it is existing behaviour that sharing does not change.

- **Always available offline:** viewing the shared plan and list (Room is SSOT), toggling grocery
  items (writes `PENDING`, syncs on reconnect), marking a meal cooked (local-only, unaffected), and
  assigning recipes to days (already local + `requestMutationSync()`).
- **Two members tick the same item offline, then both reconnect:** last-writer-wins on `updatedAt`.
  Whoever's push lands second wins; the other device converges on its next pull. This is a real,
  user-visible "my tick got undone" possibility. Document it plainly rather than promise a merge
  that isn't built.
- **Two members edit the same day's recipe:** identical LWW via `MealPlanEntity.updatedAt` — already
  the behaviour for one user across two devices. Sharing changes who can trigger it, not the
  mechanism.

Extend `docs/sync-deep-dive.md` with a household scenario, matching the existing scenario style.

---

## 9. PR Sequence

Each PR leaves `./gradlew :app:testDebugUnitTest` passing.

| PR | Scope | Blocked on |
|---|---|---|
| **A1** | ADR-014 + Room v8→v9 + entities + `HouseholdDao` | wire shape agreed |
| **A2** | Domain models + `HouseholdRepository` interface + `UserSession` extension | A1 |
| **A3** | DTOs, `HouseholdApiService`, `DefaultHouseholdRepository`, Hilt module | backend B2 live |
| **A4** | `SessionManager` wiring; `AccountSwitchHandler`/`AccountUpgradeUseCase` invariant tests | A2, A3 |
| **A5** | Household screen, member row, create empty state, profile-menu entry, nav | A4 |
| **A6** | Accept-invite screen, manual code entry, pending-invite inbox | A5 |
| **A7** | App Links + `InviteLinkParser` + `MainActivity` wiring | A6 |
| **A8** | **Sync core** — `ownerId`/`householdId`, grocery items, ownership fix | backend **B3 + B5** |
| **A9** | Anonymous→auth upgrade holding an invite | A7, A8 |
| **A10** | Leave/removal local cleanup | A8 |
| **A11** | Shared badges in meal-plan list/detail and shopping-list header | A8 |
| **A12** | Offline/conflict docs + `sync-deep-dive.md` scenario | A10 |

A1 is schema-only and can land in parallel with backend work. A8 is the first point the feature
works end to end.

---

## 10. Tests

Per project rules: JUnit4, Turbine for Flows, fakes over mocks, in-memory Room for DAO tests, Ktor
`MockEngine` for network.

- `ChefAIDatabaseMigrationTest` — `migrate8To9_addsHouseholdTables`,
  `migrate8To9_mealPlansGetsNullableHouseholdId`,
  `migrate8To9_shoppingListChecksBackfillsSyncedFromCheckedAt` (insert a v8 row via raw SQL, migrate,
  assert `syncState='SYNCED'` and `updatedAt == checkedAt`)
- `HouseholdDaoTest` — upsert/observe, members scoped per household, invite add/remove
- `ShoppingListCheckDaoTest` — extend for the new columns; `getAllDirty()` returns only dirty rows
- `HouseholdApiServiceTest` — MockEngine, success/404/error per endpoint, plus a request-shape
  assertion (auth header, path, method) via `engine.requestHistory.last()`
- `DefaultHouseholdRepositoryTest` — refresh replaces members; invite diffing removes stale rows;
  leave sequencing is network → cleanup → cache clear
- `HouseholdViewModelTest` — `NoHousehold` with no cache; `Success` with correct role;
  `onRemoveMember` refused for a MEMBER; `onLeaveHousehold` emits `LeftHousehold`
- `AcceptInviteViewModelTest` — valid → `Preview` → `Joined`; expired → `InvalidOrExpired`;
  anonymous → `RequiresSignIn`; already in a household → `AlreadyInAHousehold` (never calls accept)
- `SyncOrchestratorTest` — **the ownership regression** (pulled plan with a foreign `ownerId`
  persists that owner, not the caller); grocery item for an unknown plan is skipped, not thrown;
  grocery LWW resolves to the newer `updatedAt` regardless of arrival order
- `InviteLinkParserTest` — valid URL extracts; wrong host/path returns null; malformed doesn't throw
- `MealPlanDaoTest` — `deleteHouseholdPlansNotOwnedBy` deletes only the right rows and cascades;
  `clearHouseholdLinkForOwnPlans` nulls without deleting
- `SecurePreferencesTest` — pending-invite-token round trip

Manual verification (document in the ADR, matching `docs/sync-deep-dive.md`'s checklist style):
`adb shell am start -W -a android.intent.action.VIEW -d "https://<host>/invite/abc"`.

---

## 11. Definition of Done

- [ ] `./gradlew :app:testDebugUnitTest` green; report pass/fail counts
- [ ] Two devices on two accounts in one household see and edit the same plan and list
- [ ] A pulled shared plan keeps its real owner — verified by the regression test
- [ ] A newly joined member receives a plan created before they joined, with no client cursor reset
- [ ] Leaving removes shared rows this device doesn't own and keeps + un-shares those it does
- [ ] An invite link resolves cold-start, warm-start, and after process death mid-sign-up
- [ ] An anonymous user can accept an invite via sign-up without losing their own recipes
- [ ] Grocery ticks round-trip between two members, LWW on conflict
- [ ] No new dependency added
- [ ] ADR-014 written; `sync-deep-dive.md` extended
- [ ] `.claude/session-context.md` updated
