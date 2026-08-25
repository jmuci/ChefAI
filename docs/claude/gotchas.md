# Gotchas & Hard-Won Lessons

Things that have bitten us or are easy to get wrong in this codebase.

---

## Compose

### 1. Image loading with empty strings
Coil 3 throws "Unable to create a fetcher that supports:" when `AsyncImage` receives an empty string `""`. Always use `imageUrl.ifEmpty { null }` or guard with a placeholder.
```kotlin
// BAD — crashes Coil
AsyncImage(model = recipe.imageUrlThumbnail, ...)

// GOOD — guard empty strings
AsyncImage(
    model = recipe.imageUrlThumbnail.ifEmpty { null },
    placeholder = painterResource(R.drawable.ic_img_placeholder),
    error = painterResource(R.drawable.ic_img_error),
    ...
)
```

### 2. collectAsState vs collectAsStateWithLifecycle
Always use `collectAsStateWithLifecycle()` — it stops collection when the UI is not visible, preventing wasted work and potential crashes.

### 3. Recomposition traps
- Don't create new lambda instances inside composables (captures cause recomposition)
- Use `remember { }` for expensive objects, but don't overuse it
- Pass stable types (data classes, primitives) to composables, not mutable collections

### 4. Preview data must be realistic
Use `PreviewData` object — previews with `"Title"` and `""` URLs don't catch real layout issues.

---

## ViewModel & State

### 5. CancellationException must be rethrown
```kotlin
// BAD — swallows structured concurrency cancellation
.catch { e -> emit(Async.Error(R.string.error)) }

// GOOD
.catch { e ->
    if (e is CancellationException) throw e
    emit(Async.Error(R.string.error))
}
```

### 6. SharedFlow vs Channel for events
- `MutableSharedFlow` (replay=0): events can be lost if no collector is active
- `Channel` + `receiveAsFlow()`: events are buffered, but only one collector receives them
- We use `MutableSharedFlow` + `asSharedFlow()` — pair with `LaunchedEffect(viewModel)` to ensure collection starts early

### 7. WhileUiSubscribed timeout
`WhileUiSubscribed` = `SharingStarted.WhileSubscribed(5000)`. The 5-second timeout keeps upstream flows alive briefly during config changes (rotation). Don't set it to 0 or you'll re-fetch on every rotation.

---

## Data Layer

### 8. (Resolved) Hardcoded test UUID
`RecipesViewModel` and `DefaultRecipeRepository` used to hardcode `F47AC10B58CC4372A5670E02B2C3D479`
instead of resolving the real user. Fixed — both now go through `SessionManager.userSession`. Kept
in place rather than deleted, since other entries and code comments cite gotchas by number.

### 9. Room schema migrations
Every schema change needs a migration. Forgetting one crashes the app on update. Always add migration + test with in-memory DB.

### 10. UUIDv7 byte ordering
SQLite stores UUIDs as 16-byte blobs. The byte ordering matters for time-sortability. Use the project's `generateUuid7()` utility — don't roll your own.

### 11. SyncState transitions
```
SYNCED → (local edit) → PENDING → (push accepted) → SYNCED
SYNCED → (local delete) → DELETED → (push accepted) → hard delete
PENDING → (pull conflict) → CONFLICT (not yet handled)
```
Never set syncState directly in UI code. Repository methods handle transitions.

---

## Auth

### 12. Anonymous session persistence
The anonymous `localUserId` is stored in SecurePreferences. If you clear app data, a new anonymous ID is generated and all local data becomes orphaned.

### 13. Token refresh race condition
Multiple simultaneous 401s can trigger parallel refresh attempts. The `SessionManager` should serialize refresh calls (currently not fully hardened).

---

## Build & Testing

### 14. KSP not KAPT
Hilt uses KSP. Don't add `kapt` dependencies — they'll slow the build and may conflict.

### 15. Fake duplication is intentional
`FakeRecipeRepository` exists in both `test/` and `androidTest/`. This is by design (ADR decision). Don't try to share them via a `testFixtures` module unless the project moves to multi-module.

### 16. Ktor MockEngine for network tests
Always use `MockEngine` for testing network calls. Don't mock the repository when you can test the actual Ktor client with a fake server response.

---

## Recipe Import / Scraping (`:recipe-scraper`)

### 17. `RecipeDraft.toRecipe()` throws on blank numeric strings
`prepTimeMinutes`, `cookTimeMinutes`, and `servings` are `String` on `RecipeDraft` (so partial or
invalid form input survives auto-save), but `toRecipe()` (`DraftMapper.kt`) calls bare `.toInt()` on
them — a blank string throws `NumberFormatException`. Any code that builds a `RecipeDraft` from a
source other than the editor's own form (e.g. `ScrapedRecipeMapper`) **must emit `"0"`, never `""`,
for an absent numeric value**. `RecipeEditorReducer.revalidate()` also requires these three fields
plus `description` to be non-blank for `isFormValid`, so a draft seeded with `"0"` correctly leaves
Save disabled until the user confirms real values — it doesn't silently save a zero.

### 18. The unqualified `HttpClient` carries ChefAI auth headers
`NetworkModule.provideHttpClient()` installs `AuthInterceptor` and is injected by five API services.
**Never point it at a third-party host** — a scraper, webhook receiver, or any code fetching a
user-supplied URL must use the separate `@ScraperHttpClient`-qualified client (no auth interceptor,
no content negotiation, its own timeout/retry/logging config). Don't add a qualifier to the existing
`provideHttpClient` to "fix" this — it would break all five services that inject it unqualified; add
a new provider alongside it instead, as `ScraperHttpClient.kt` does.

### 19. Never name a package or class after a Kotlin reserved word
`import` is a reserved keyword. A package literally named `...ui.import` (as originally planned for
the recipe-import screens) compiles fine with plain `kotlinc`, but crashes **KSP2's
Analysis-API-based FIR resolver** with a deeply-nested, misleading `NoClassDefFoundError:
kotlin/reflect/full/KClasses` — thrown while KSP tries to render a debug attachment for an *earlier*
internal failure resolving a symbol's containing declaration. It's a real toolchain bug (reproduced
on Kotlin 2.3.0 / KSP 2.3.2), not a code-correctness issue, and it's deterministic: an **empty**
`@HiltViewModel class Foo @Inject constructor() : ViewModel()` alone, in that package, is enough to
trigger it. Renamed to `recipes/ui/urlimport/` and the build succeeded immediately with no other
changes. If this exact `NoClassDefFoundError` shows up again, check package/class names against
Kotlin's reserved-word list (`import`, `object`, `when`, `is`, `as`, `package`, …) before chasing
Gradle daemon / cache / heap theories — those were all dead ends here.

### 20. Building `:recipe-scraper` in a new worktree
1. `local.properties` is gitignored, so a fresh `git worktree add` doesn't carry it over — every
   `:app` Gradle task fails with "SDK location not found" until it's recreated with
   `sdk.dir=<path to your Android SDK>`.
2. The root `build.gradle.kts` needs `alias(libs.plugins.kotlin.multiplatform) apply false` in its
   `plugins {}` block (already on `main`) — without it, `:recipe-scraper` applying
   `kotlin("multiplatform")` fails with "plugin is already on the classpath with an unknown version."
3. `recipe-scraper/build.gradle.kts` targets JVM 11 bytecode via
   `jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }`, not `jvmToolchain(11)` — the latter
   requires an actual JDK 11 installation, which isn't guaranteed on every machine building this.

### 21. `TestRepositoryModule` (androidTest) must bind every leaf dependency any `@HiltViewModel` needs
Hilt builds **one shared test component** for the whole `androidTest` source set — every
`@HiltAndroidTest` class and every `@HiltViewModel` in the app is aggregated into it, whether or not
a given test actually uses them. `TestRepositoryModule` (`TestAuthModule.kt`) replaces production
`RepositoryModule` wholesale, so it must independently provide *everything* production would have —
missing even one binding (e.g. `MealPlanNetworkDataSource`, missed until a second `@HiltAndroidTest`
class was added) fails `hiltJavaCompileDebugAndroidTest` for **every** instrumented test in the
module, not just ones touching that dependency. When adding a new production `@Binds`/`@Provides` to
a module a `Test*Module` replaces, add the equivalent there too — reusing the real implementation
(as `bindMealPlanNetworkDataSource` does) is fine when nothing in the test suite actually invokes it;
Dagger bindings are lazy.

### 22. Compose `LaunchedEffect(Unit)` closures don't see later state changes — use `rememberUpdatedState`
`ImportRecipeScreen`'s clipboard-prefill effect checked `state.url.isNotBlank()` once, before an
`await`ed clipboard read, then unconditionally applied the clipboard text after. Since
`LaunchedEffect(Unit)` runs its block once and the `state` parameter it closed over is whatever was
current at that first composition, a user typing into the field *while* the clipboard read was still
in flight had their input silently overwritten the moment it resolved — a real race, not
hypothetical (it fired in an instrumented test run). Fix: wrap the read in `rememberUpdatedState` and
re-check the *current* value immediately before acting on an awaited result, not just before starting
the `await`.

### 23. `ImageDecoder` returns a hardware bitmap, which cannot be `compress()`ed
`RecipeImageStore.writeFromUri` decodes a picked photo and re-encodes it as JPEG. By default
`ImageDecoder.decodeBitmap` allocates a `Config.HARDWARE` bitmap — GPU-backed, with no CPU-readable
pixel data — and `Bitmap.compress` on one throws. Set `decoder.allocator =
ImageDecoder.ALLOCATOR_SOFTWARE` inside the `OnHeaderDecodedListener` whenever the bitmap will be
read back rather than just drawn. (The upside of `ImageDecoder` over `BitmapFactory` is that it
applies EXIF orientation for free, which is why it's worth the extra line — `BitmapFactory` would
need `androidx.exifinterface` and manual rotation.)

### 24. A device-local column on a synced table is wiped by the next pull
`RecipeDao.upsertRecipe` is a full-row `@Upsert`, and `SyncRecipeDto.toRecipeEntity` constructs a
fresh `RecipeEntity`. Any column the DTO doesn't carry silently reverts to its default on every pull
that touches the row — and because sync runs on a 5s post-mutation debounce, that happens seconds
after the local write, not at some distant future sync. This cost a full debugging session on
`localImagePath` (fixed in #139 by threading the existing value through explicitly). For state that is
purely local and has nothing to do with the server, prefer a **sibling table** keyed to the recipe
(`recipe_image_state`) — structurally immune, and it can't be forgotten by the next person adding a
field to the mapper. See ADR-011 Decision 5.

### 25. MockK cannot reliably mock a suspend function that returns `kotlin.Result<T>`
`coEvery { sessionManager.refreshToken() } returns Result.failure(e)` (and `coAnswers { Result.failure(e) }`
— both were tried) does **not** produce the value you'd expect. `Result<T>` is a compiler-magic inline
class with special-cased suspend-function ABI handling, and MockK's proxy doesn't replicate it: the
stubbed value gets wrapped in an *extra* layer, so the caller actually observes
`Result.success(Result.failure(e))` — `.isFailure` on that outer wrapper is `false`. Confirmed
empirically (printed the actual return value) while testing `RecipeSearchRepository`'s
refresh-then-retry path — cost real debugging time because the bug is silent: `Result.success(Unit)`
stubs are unaffected (the outer wrapper is correctly a success either way), so only the *failure* case
breaks, and a `coVerify(exactly = N)` call-count mismatch is the only visible symptom, not a type error.
**Fix**: don't mock a `Result`-returning suspend function directly. Exercise the real object instead —
e.g. `core/testutil/FakeSessionManager.kt`'s `createTestSessionManagerWithAuthSource` builds a real
`SessionManager` and drives a real failure through `FakeAuthNetworkDataSource.shouldThrowError`, which
sidesteps the bug entirely because nothing mocks a `Result`-typed suspend function.

### 26. `MainCoroutineRule`'s default dispatcher has its own scheduler — `advanceTimeBy` in a bare `runTest {}` won't reach it
`MainCoroutineRule(testDispatcher: TestDispatcher = UnconfinedTestDispatcher())` creates a dispatcher
bound to its **own** `TestCoroutineScheduler` when no scheduler is passed in. A bare `runTest { }` in
your test method creates a **different** one. `viewModelScope` runs on `Dispatchers.Main` (→ the
rule's scheduler), so if a ViewModel test does anything time-based — `.debounce()`, `delay()`, a
timeout — `advanceTimeBy`/`runCurrent` called inside `runTest { }` silently advance the *wrong* clock
and the coroutine under test never proceeds (symptom: `uiState.value` stays stuck at its `initialValue`
forever, no crash, no timeout). This is why `RecipeSearchViewModelTest` (the first ViewModel test in
this codebase to use `.debounce()`) doesn't use `MainCoroutineRule` — it mirrors `LoginViewModelTest`'s
pattern instead: `private val testDispatcher = StandardTestDispatcher()`, `Dispatchers.setMain(testDispatcher)`
+ `TestScope(testDispatcher)` in `@Before`, `Dispatchers.resetMain()` in `@After`, and every test body
is `testScope.runTest { ... }` — one shared scheduler, `advanceTimeBy` actually reaches the debounce.
Separately: reading `uiState.value` directly (instead of subscribing via Turbine's `.test { }`) never
starts the upstream flow at all when `uiState` is `stateIn(..., SharingStarted.WhileSubscribed(5000), ...)`
— `.value` only reads whatever was last pushed by an active collector, and `MutableStateFlow.value = x`
on an *input* flow doesn't force `stateIn`'s upstream to run without at least one subscriber.
