# Code Conventions — ChefAI

Rules extracted from real patterns in the codebase. These supplement the coding rules in `CLAUDE.md`.

---

## Naming

| Thing | Convention | Example |
|-------|-----------|---------|
| Packages | Feature-based, lowercase, singular | `auth/`, `recipes/`, `core/` |
| Screens | `[Feature][Purpose]Screen` | `RecipeDetailsScreen`, `CreateRecipeScreen` |
| ViewModels | `[Feature][Purpose]ViewModel` | `RecipeDetailsViewModel` |
| Repository interface | `[Entity]Repository` | `RecipesRepository` |
| Repository impl | `Default[Entity]Repository` | `DefaultRecipeRepository` |
| Mappers | `[Source][Target]Map.kt` (extension fns) | `RoomDomainMap.kt`, `NetworkDomainMap.kt` |
| Test fakes | `Fake[ClassName]` | `FakeRecipeRepository`, `FakeRecipeDao` |
| UiState | `[Feature]UiState` (data class) | `HomeUiState`, `LoginUiState` |
| UiEvent | `[Feature]UiEvent` (sealed interface) | `HomeUiEvent`, `LoginUiEvent` |
| DB entities | `[Name]Entity` | `RecipeEntity` |
| Network DTOs | `[Name]Response` / `[Name]Request` | `RecipeResponse` |

## File Organization

```
feature/
├── data/
│   ├── mapper/         # Extension functions for model conversion
│   ├── network/        # API service, network DTOs
│   └── repository/     # Default[X]Repository
├── domain/
│   ├── model/          # Feature-specific domain models (rare)
│   └── repository/     # [X]Repository interface
└── ui/
    ├── [screen]/       # Screen composables, ViewModel
    └── components/     # Feature-specific UI components
```

## Compose Conventions

- `modifier: Modifier = Modifier` — always last parameter
- Callbacks: lambda params with `= {}` defaults (e.g., `onClick: (UUID) -> Unit = {}`)
- State: `collectAsStateWithLifecycle()` (not `collectAsState()`)
- Events: `LaunchedEffect(viewModel) { viewModel.uiEvents.collect { ... } }`
- Previews: always light + dark via `ChefAITheme(darkTheme = true)`
- Preview data: use `PreviewData` object from `core/ui/preview/`
- Resources: `stringResource()`, `dimensionResource()`, `painterResource()` — no hardcoded strings
- Loading/Empty: use `LoadingContent()` and `EmptyContent()` from `core/util/ComposeUtils.kt`

## ViewModel Conventions

- Always `@HiltViewModel` + `@Inject constructor`
- State: `private MutableStateFlow` → public `StateFlow` via `asStateFlow()`
- Events: `MutableSharedFlow` → `asSharedFlow()` for one-time events
- Derived state: `combine()` + `.stateIn(viewModelScope, WhileUiSubscribed, initialValue)`
- State mutation: `.update { it.copy(...) }`
- Coroutines: `viewModelScope.launch` only. Never `GlobalScope`.
- Error handling: always rethrow `CancellationException`

## Data Layer Conventions

- Room entities: `@Entity(tableName = "snake_case")`, `@PrimaryKey` with `@ColumnInfo`
- All IDs: UUIDv7, stored as 16-byte blobs in SQLite
- DAO functions: `suspend` for writes, `Flow<T>` for reads
- Mappers: extension functions (`RecipeEntity.toDomain()`, `Recipe.toEntity()`)
- Network: Ktor client, `@Serializable` DTOs with `@SerialName("snake_case")`
- TypeConverters: centralized in `core/data/local/util/`

## Testing Conventions

- Framework: JUnit4 + `kotlinx-coroutines-test`
- Flow assertions: Turbine
- Style: Given/When/Then
- Fakes over mocks (prefer `FakeRecipeRepository` over MockK)
- Pragmatic duplication: fakes can exist in both `test/` and `androidTest/`
- MainCoroutineRule for dispatcher overrides
- ViewModel tests: test state emissions and event emissions separately

## Git & PR Conventions

- Diff-based edits, not full file rewrites
- Small, reviewable PRs with rationale
- Schema changes require migration + in-memory DAO test
- Major architecture changes require ADR in `docs/adrs/`
