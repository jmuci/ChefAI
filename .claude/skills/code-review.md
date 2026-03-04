# Skill: Code Review

Use this skill when reviewing code — PR reviews, ad-hoc reviews, or auditing existing code.

---

## Review Process

### 1. Understand the Change
- Read the PR description or user's explanation of what changed and why
- Identify which layers are touched (UI, Domain, Data)
- Check if any public API surface changed

### 2. Run the Checklist (by layer)

---

## Architecture & Structure

- [ ] Changes are in the correct feature package (not scattered across features)
- [ ] New files follow naming conventions (`[Feature][Purpose]Screen.kt`, etc.)
- [ ] No cross-feature dependencies (features depend on `core/`, not each other)
- [ ] Domain layer has no Android/framework imports
- [ ] Repository interface in `domain/repository/`, implementation in `data/repository/`
- [ ] No Room entities or network DTOs leaked into UI or Domain layers
- [ ] Mappers exist at layer boundaries (`toDomain()`, `toEntity()`, `toDto()`)

---

## Kotlin Quality

- [ ] No `!!` operator — use `requireNotNull()` with message, `?: return`, or restructure
- [ ] No `GlobalScope` — all coroutines in `viewModelScope` or owned scope
- [ ] `CancellationException` rethrown in every `catch` block
- [ ] Explicit return types on public functions
- [ ] `sealed interface` for State/Event types (not `sealed class`)
- [ ] `data class` for pure data, not objects with identity semantics
- [ ] Trailing lambdas used consistently
- [ ] No silent `catch` blocks — errors handled explicitly
- [ ] Structured concurrency respected (child coroutines die with parent)

---

## Compose UI

- [ ] Composables are stateless — state hoisted to ViewModel or caller
- [ ] `modifier: Modifier = Modifier` always last parameter
- [ ] `collectAsStateWithLifecycle()` (not `collectAsState()`)
- [ ] No business logic inside composables
- [ ] Loading + empty + error states all handled
- [ ] Images use `AsyncImage` with placeholder, error drawable, and `crossfade(true)`
- [ ] Empty string guard on image URLs (`.ifEmpty { null }`)
- [ ] Material3 components — no deprecated APIs
- [ ] Light + dark previews with `PreviewData`
- [ ] String resources via `stringResource()` — no hardcoded strings
- [ ] Callbacks have `= {}` defaults

---

## ViewModel

- [ ] `@HiltViewModel` + `@Inject constructor`
- [ ] UiState as `data class` with sensible defaults
- [ ] UiEvent as `sealed interface`
- [ ] State exposed as `StateFlow` (not `MutableStateFlow`)
- [ ] Events via `SharedFlow` or `Channel` (not LiveData)
- [ ] `WhileUiSubscribed` for `stateIn()` calls
- [ ] State mutation via `.update { it.copy(...) }`
- [ ] No Android framework references (Context, Activity, etc.)
- [ ] No direct data source access — goes through Repository

---

## Data Layer

- [ ] Room entities use `@ColumnInfo(name = "snake_case")`
- [ ] UUIDv7 for all IDs (via project's `generateUuid7()`)
- [ ] DAO queries: `suspend` for writes, `Flow<T>` for reads
- [ ] Schema changes include migration
- [ ] Network DTOs use `@Serializable` + `@SerialName("snake_case")`
- [ ] No blocking I/O on main thread
- [ ] `flowOn(Dispatchers.IO)` in producers, not `withContext` inside `collect`
- [ ] SyncableEntity fields maintained correctly (`syncState`, `updatedAt`)

---

## Testing

- [ ] Unit tests exist for ViewModel (success + error paths)
- [ ] Fakes used over mocks where possible
- [ ] `CancellationException` handling tested
- [ ] Flow assertions use Turbine
- [ ] Given/When/Then structure
- [ ] Schema changes have in-memory DB DAO tests
- [ ] No flaky test patterns (race conditions, timing dependencies)

---

## Security & Performance

- [ ] No secrets or tokens in committed code
- [ ] Auth tokens only accessed through `SessionManager`
- [ ] No unnecessary recompositions (stable types, proper keys)
- [ ] Paging used for large lists (not loading all into memory)
- [ ] No memory leaks (Activity/Context references in ViewModel)

---

## Red Flags to Call Out

These warrant blocking feedback:

1. **`!!` operator** — always a potential crash
2. **`GlobalScope`** — leaks coroutines
3. **Swallowed `CancellationException`** — breaks structured concurrency
4. **Room entity in UI layer** — architecture violation
5. **Missing migration** — crashes on app update
6. **Hardcoded user UUID** — use `SessionManager`
7. **`LiveData` in new code** — use `StateFlow`
8. **`collectAsState()` without lifecycle** — wastes resources when UI not visible
9. **Silent catch blocks** — errors must be handled or propagated
10. **New dependency added without discussion** — flag and ask

---

## Tone & Approach

- Lead with what's good about the change
- Be specific: point to the exact line and explain the issue
- Suggest fixes, don't just flag problems
- Distinguish: must-fix (blocking) vs nice-to-have (non-blocking)
- If unsure about intent, ask instead of assuming
