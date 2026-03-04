# Skill: Build a ViewModel

Use this skill when creating a new ViewModel or modifying an existing one.

---

## Before You Start

1. Read `docs/claude/gotchas.md` — items 5-7 (ViewModel section)
2. Read the repository interface the ViewModel will depend on
3. Check if an `Async<T>` wrapper is appropriate (see Pattern B below)

---

## UiState & UiEvent Types

Define these alongside or inside the ViewModel file:

```kotlin
// UiState — all fields the UI needs to render
data class FeatureUiState(
    val items: List<ItemPreview> = emptyList(),
    val isLoading: Boolean = false,
    val selectedFilter: Filter = Filter.ALL,
    // Form fields (if applicable)
    val title: String = "",
    val titleError: String? = null
)

// UiEvent — one-time side effects (snackbars, navigation)
sealed interface FeatureUiEvent {
    data class ShowSnackbar(@StringRes val message: Int) : FeatureUiEvent
    data class NavigateToDetail(val id: UUID) : FeatureUiEvent
    data object NavigateBack : FeatureUiEvent
}
```

**Rules:**
- `data class` for UiState — immutable, copyable
- `sealed interface` for UiEvent (not `sealed class`)
- `data object` for no-arg events, `data class` for events with data
- Use `@StringRes Int` for message resources, not raw strings
- All state fields have sensible defaults

---

## Pattern A: Simple ViewModel (Direct State)

For screens with form inputs or simple state:

```kotlin
@HiltViewModel
class CreateRecipeViewModel @Inject constructor(
    private val recipesRepository: RecipesRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateRecipeUiState())
    val uiState: StateFlow<CreateRecipeUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<CreateRecipeUiEvent>()
    val uiEvents: SharedFlow<CreateRecipeUiEvent> = _uiEvent.asSharedFlow()

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title, titleError = null) }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(titleError = "Title is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                recipesRepository.createRecipe(/* ... */)
                _uiEvent.emit(CreateRecipeUiEvent.NavigateBack)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiEvent.emit(CreateRecipeUiEvent.ShowSnackbar(R.string.save_error))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
```

---

## Pattern B: Reactive ViewModel (Derived State)

For screens that observe repository flows:

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    recipesRepository: RecipesRepository
) : ViewModel() {

    private val _uiEvent = MutableSharedFlow<HomeUiEvent>()
    val uiEvents: SharedFlow<HomeUiEvent> = _uiEvent.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)

    private val _recipesAsync = recipesRepository.getRecipesPreviewStream()
        .map<List<RecipePreview>, Async<List<RecipePreview>>> { Async.Success(it) }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(Async.Error(R.string.loading_recipes_error))
        }

    val uiState: StateFlow<HomeUiState> = combine(
        _isLoading,
        _recipesAsync
    ) { isLoading, recipesAsync ->
        when (recipesAsync) {
            Async.Loading -> HomeUiState(isLoading = true)
            is Async.Error -> {
                viewModelScope.launch {
                    _uiEvent.emit(HomeUiEvent.ShowSnackbar(recipesAsync.errorMessage))
                }
                HomeUiState(isLoading = false)
            }
            is Async.Success -> HomeUiState(
                recipes = recipesAsync.data,
                isLoading = isLoading
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = WhileUiSubscribed,
        initialValue = HomeUiState(isLoading = true)
    )
}
```

**When to use which:**
- **Pattern A**: Forms, settings, screens where user actions drive state
- **Pattern B**: List screens, dashboards, screens that observe data streams

---

## Async Wrapper

Located in `core/util/Async.kt`:

```kotlin
sealed class Async<out T> {
    object Loading : Async<Nothing>()
    data class Error(@StringRes val errorMessage: Int) : Async<Nothing>()
    data class Success<out T>(val data: T) : Async<T>()
}
```

Use to wrap repository flows that can be in loading/error/success states.

---

## WhileUiSubscribed

Located in `core/util/CoroutineUtils.kt`:

```kotlin
val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(5000)
```

The 5-second timeout keeps upstream flows alive during configuration changes. Always use this instead of `SharingStarted.Eagerly` or `SharingStarted.Lazily` unless you have a specific reason.

---

## Error Handling

```kotlin
viewModelScope.launch {
    try {
        // operation
    } catch (e: Exception) {
        if (e is CancellationException) throw e  // NEVER swallow this
        _uiEvent.emit(FeatureUiEvent.ShowSnackbar(R.string.generic_error))
    }
}
```

**Rules:**
- Always rethrow `CancellationException` — swallowing it breaks structured concurrency
- Map errors to `@StringRes` resource IDs, not raw strings
- Use `Async.Error` for stream-based errors, `try/catch` for one-shot operations
- Don't log errors in production builds without `Timber`

---

## Testing a ViewModel

```kotlin
class FeatureViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var fakeRepository: FakeRecipesRepository
    private lateinit var viewModel: FeatureViewModel

    @Before
    fun setup() {
        fakeRepository = FakeRecipesRepository()
        viewModel = FeatureViewModel(fakeRepository)
    }

    @Test
    fun `initial state is loading`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.isLoading)
        }
    }

    @Test
    fun `recipes loaded successfully`() = runTest {
        fakeRepository.setRecipes(TestData.recipes)

        viewModel.uiState.test {
            skipItems(1) // skip Loading
            val success = awaitItem()
            assertFalse(success.isLoading)
            assertEquals(TestData.recipes, success.recipes)
        }
    }

    @Test
    fun `error emits snackbar event`() = runTest {
        fakeRepository.setShouldError(true)

        viewModel.uiEvents.test {
            val event = awaitItem()
            assertTrue(event is FeatureUiEvent.ShowSnackbar)
        }
    }
}
```

**Rules:**
- Use `MainCoroutineRule` to override `Dispatchers.Main`
- Use Turbine's `.test { }` for Flow assertions
- Test state and events separately
- Use `FakeRepository` (not MockK) — fakes in `test/[feature]/data/`
- Cover: initial state, success, error, edge cases

---

## Checklist

Before submitting a new ViewModel:

- [ ] `@HiltViewModel` + `@Inject constructor`
- [ ] `data class` UiState with defaults
- [ ] `sealed interface` UiEvent
- [ ] Private `MutableStateFlow` → public `StateFlow`
- [ ] Events via `MutableSharedFlow` → `asSharedFlow()`
- [ ] `CancellationException` rethrown in all catch blocks
- [ ] `WhileUiSubscribed` used with `stateIn()`
- [ ] State mutation via `.update { it.copy(...) }`
- [ ] No Android framework references (no Context, Activity, View)
- [ ] Unit test with Turbine covering success + error
