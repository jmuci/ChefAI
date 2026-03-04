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

### 8. Hardcoded test UUID
`RecipesViewModel` and `DefaultRecipeRepository` still use `F47AC10B58CC4372A5670E02B2C3D479`. Must be replaced with real user from `SessionManager`.

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
