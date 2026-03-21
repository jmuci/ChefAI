# Unified Recipe Editor — Implementation Plan

## Current State Analysis

### What exists
- `CreateRecipeScreen` + `CreateRecipeViewModel` — fully functional create flow
- `RecipeDetailsScreen` — read-only view (no edit button)
- `DefaultRecipeRepository.createRecipe()` — handles all sub-entities atomically
- `DefaultRecipeRepository.updateRecipe()` — **broken**: only upserts RecipeEntity, ignores steps/ingredients/tags/labels
- `DefaultRecipeRepository.deleteRecipe()` — **hard delete**: `DELETE FROM recipes WHERE uuid = :uuid`, no soft-delete for sync
- `RecipeEntity` — **missing `version` field** for optimistic concurrency
- `Recipe` domain model — also missing `version`
- Good test infrastructure: Fake DAOs, test data (`TestRecipeData.kt`), repository tests

### What needs to change
| Gap | Impact |
|-----|--------|
| No `version` field on entity/model | Cannot support optimistic concurrency |
| `updateRecipe()` incomplete | Editing would lose steps/ingredients/tags/labels |
| Hard delete, no soft-delete | Sync can't propagate deletes to backend |
| No edit entry point in UI | No way to reach editor in edit mode |
| No draft persistence | No auto-save for in-progress edits |
| No dirty tracking | Can't warn on back-nav with unsaved changes |

### Key architectural constraint
`SyncState.PENDING` already means "locally changed, needs sync" — this IS the DIRTY state. We use `PENDING` throughout (not adding a new `DIRTY` enum value).

---

## Package Structure (target)

```
recipes/
├── data/
│   ├── mapper/
│   │   └── RoomDomainMap.kt              ← MODIFY (version field mapping)
│   ├── repository/
│   │   └── DefaultRecipeRepository.kt    ← MODIFY (fix updateRecipe, soft delete)
│   └── network/ (unchanged)
├── domain/
│   ├── model/
│   │   ├── EditorMode.kt                 ← NEW
│   │   └── RecipeDraft.kt               ← NEW
│   └── repository/
│       └── RecipesRepository.kt          ← MODIFY (add softDeleteRecipe)
└── ui/
    ├── editor/                           ← NEW (replaces create/)
    │   ├── RecipeEditorScreen.kt
    │   ├── RecipeEditorViewModel.kt
    │   ├── RecipeEditorState.kt          ← NEW (MVI state + actions)
    │   ├── RecipeEditorReducer.kt        ← NEW (pure reducer)
    │   └── components/                   ← MOVE from create/components/
    │       ├── AutocompleteInput.kt
    │       ├── IngredientInput.kt
    │       ├── StepCard.kt
    │       ├── ImageUploadContent.kt
    │       └── UnsavedChangesDialog.kt   ← NEW
    ├── details/
    │   └── RecipeDetailsScreen.kt        ← MODIFY (add edit button)
    └── RecipesScreen.kt (unchanged)

core/
├── data/local/room/
│   ├── RecipeEntity.kt                   ← MODIFY (add version field)
│   ├── RecipeDraftEntity.kt              ← NEW
│   ├── dao/
│   │   ├── ChefAIDataBase.kt            ← MODIFY (migration 3→4, add draft DAO)
│   │   └── RecipeDraftDao.kt            ← NEW
│   └── (other entities unchanged)
├── domain/model/
│   └── Recipe.kt                         ← MODIFY (add version field)
└── ui/navigation/
    ├── AppDestinations.kt                ← MODIFY (EDIT_RECIPE route)
    └── ChefAINavGraph.kt                 ← MODIFY (wire editor)
```

---

## Phase 1: Data Layer Foundation

**Goal**: Add `version` field, fix `updateRecipe`, implement soft-delete.

### 1.1 Add `version` to RecipeEntity

**File**: `core/data/local/room/RecipeEntity.kt`
```kotlin
data class RecipeEntity(
    // ... existing fields ...
    val version: Int = 1,    // ← ADD
    // ... sync fields ...
): SyncableEntity
```

### 1.2 Add `version` to Recipe domain model

**File**: `core/domain/model/Recipe.kt`
```kotlin
data class Recipe(
    // ... existing fields ...
    val version: Int = 1,    // ← ADD
    val updatedAt: Long,
)
```

### 1.3 Database migration 3 → 4

**File**: `core/data/local/room/dao/ChefAIDataBase.kt`
- Bump `version = 4`
- Add `MIGRATION_3_4`:
```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
    }
}
```
- Register migration in the Hilt database provider module

### 1.4 Update mappers

**File**: `recipes/data/mapper/RoomDomainMap.kt`
- `RecipeWithDetails.toDomain()`: map `recipe.version` → `Recipe.version`
- `Recipe.toRoomEntity()`: map `version` → `RecipeEntity.version`

### 1.5 Fix `updateRecipe` in DefaultRecipeRepository

**File**: `recipes/data/repository/DefaultRecipeRepository.kt`

Current `updateRecipe` only does `recipeDao.upsertRecipe(recipe.toRoomEntity())`. Must be updated to mirror `createRecipe` logic:

```kotlin
@Transaction
override suspend fun updateRecipe(recipe: Recipe) {
    recipeDao.upsertRecipe(recipe.toRoomEntity())

    // Replace steps
    recipeStepDao.deleteAllForRecipe(recipe.uuid)
    recipe.steps.forEach { step ->
        recipeStepDao.upsertStep(step.toRoomEntity(recipe.uuid))
    }

    // Replace ingredients
    recipe.ingredients.forEach { ingredient ->
        ingredientDao.upsertIngredient(ingredient.toRoomEntity())
    }
    recipeIngredientDao.upsertAllForRecipe(
        recipe.uuid,
        recipe.ingredients.map { it.toCrossRef(recipe.uuid) }
    )

    // Replace tags
    recipeTagDao.deleteAllForRecipe(recipe.uuid)
    recipe.tags.forEach { tag ->
        tagDao.upsertTag(tag.toRoomEntity())
        recipeTagDao.upsertCrossRef(RecipeTagCrossRef(...))
    }

    // Replace labels
    recipeLabelDao.deleteAllForRecipe(recipe.uuid)
    recipe.labels.forEach { label ->
        labelDao.upsertLabel(label.toRoomEntity())
        recipeLabelDao.upsertCrossRef(RecipeLabelCrossRef(...))
    }

    syncManager.requestMutationSync()
}
```

**Requires new DAO methods**:
- `RecipeTagCrossRefDao.deleteAllForRecipe(recipeId: UUID)` — if not already present
- `RecipeLabelCrossRefDao.deleteAllForRecipe(recipeId: UUID)` — if not already present

### 1.6 Implement soft-delete

**File**: `recipes/domain/repository/RecipesRepository.kt`
- Add: `suspend fun softDeleteRecipe(recipeId: UUID)`

**File**: `recipes/data/repository/DefaultRecipeRepository.kt`
```kotlin
override suspend fun softDeleteRecipe(recipeId: UUID) {
    val now = System.currentTimeMillis()
    recipeDao.softDelete(recipeId, now)  // sets deletedAt + syncState = DELETED
    syncManager.requestMutationSync()
}
```

**File**: `core/data/local/room/dao/RecipeDao.kt`
- Add:
```kotlin
@Query("UPDATE recipes SET deletedAt = :deletedAt, syncState = 'DELETED' WHERE uuid = :uuid")
suspend fun softDelete(uuid: UUID, deletedAt: Long)
```

### 1.7 Register migration in Hilt module

**File**: Wherever `Room.databaseBuilder` is called (likely a `@Provides` in a Hilt module)
- Add `.addMigrations(MIGRATION_3_4)` to the builder chain

### Tests for Phase 1
- **File**: `test/.../recipes/data/repository/DefaultRecipeRepositoryTest.kt`
  - `updateRecipe() persists all sub-entities` — update recipe, verify steps/ingredients/tags/labels change
  - `softDeleteRecipe() sets deletedAt and DELETED syncState`
  - `updateRecipe() replaces old steps with new steps`

### Risks & Edge Cases
- **Migration on existing devices**: `ALTER TABLE ADD COLUMN` with default is safe; no data loss
- **`updateRecipe` atomicity**: Must use `@Transaction` — if any sub-entity write fails, all roll back
- **Cross-ref delete+recreate**: The delete-all-then-insert pattern in `upsertAllForRecipe` is already used for ingredients; replicate for tags/labels

---

## Phase 2: Draft Infrastructure

**Goal**: Room-persisted draft entity for auto-save and process-death recovery.

### 2.1 RecipeDraft domain model

**File**: `recipes/domain/model/RecipeDraft.kt` (NEW)
```kotlin
/**
 * Transient representation of an in-progress recipe edit.
 * Separate from Recipe to avoid corrupting persisted data during editing.
 */
data class RecipeDraft(
    val recipeId: UUID,           // The actual recipe UUID (same for edit, new for create)
    val isNewRecipe: Boolean,     // true = create mode, false = edit mode
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val selectedImageUri: String? = null,
    val prepTimeMinutes: String = "",
    val cookTimeMinutes: String = "",
    val servings: String = "",
    val externalUrl: String = "",
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val labels: List<Label> = emptyList(),
    val version: Int = 1,         // Preserved from original for sync
    val updatedAt: Long = System.currentTimeMillis(),
)
```

### 2.2 EditorMode sealed class

**File**: `recipes/domain/model/EditorMode.kt` (NEW)
```kotlin
sealed class EditorMode {
    object Create : EditorMode()
    data class Edit(val recipeId: UUID) : EditorMode()
}
```

### 2.3 RecipeDraftEntity (Room)

**File**: `core/data/local/room/RecipeDraftEntity.kt` (NEW)

Store draft as a single row with JSON-serialized lists. This avoids complex relational modeling for transient data.

```kotlin
@Entity(tableName = "recipe_drafts")
data class RecipeDraftEntity(
    @PrimaryKey val recipeId: UUID,
    val isNewRecipe: Boolean,
    val title: String,
    val description: String,
    val imageUrl: String,
    val selectedImageUri: String?,
    val prepTimeMinutes: String,
    val cookTimeMinutes: String,
    val servings: String,
    val externalUrl: String,
    val ingredientsJson: String,   // JSON-serialized List<RecipeIngredient>
    val stepsJson: String,         // JSON-serialized List<RecipeStep>
    val tagsJson: String,          // JSON-serialized List<Tag>
    val labelsJson: String,        // JSON-serialized List<Label>
    val version: Int,
    val updatedAt: Long,
)
```

**TypeConverters**: Add a `DraftJsonConverters` class using `kotlinx.serialization` or Moshi (whichever is already in the project) for the JSON fields. Since the lists are stored as plain String columns, we handle serialization at the mapper level rather than with Room TypeConverters.

### 2.4 RecipeDraftDao

**File**: `core/data/local/room/dao/RecipeDraftDao.kt` (NEW)
```kotlin
@Dao
interface RecipeDraftDao {
    @Upsert
    suspend fun saveDraft(draft: RecipeDraftEntity)

    @Query("SELECT * FROM recipe_drafts WHERE recipeId = :recipeId")
    suspend fun getDraft(recipeId: UUID): RecipeDraftEntity?

    @Query("DELETE FROM recipe_drafts WHERE recipeId = :recipeId")
    suspend fun deleteDraft(recipeId: UUID)

    @Query("DELETE FROM recipe_drafts")
    suspend fun deleteAllDrafts()
}
```

### 2.5 Draft mappers

**File**: `recipes/data/mapper/DraftMapper.kt` (NEW)
- `Recipe.toRecipeDraft(): RecipeDraft` — convert loaded recipe to editable draft
- `RecipeDraft.toRecipeDraftEntity(): RecipeDraftEntity` — serialize for Room
- `RecipeDraftEntity.toRecipeDraft(): RecipeDraft` — deserialize from Room
- `RecipeDraft.toRecipe(creator: User): Recipe` — convert draft back to domain recipe for saving

### 2.6 Register in database

**File**: `core/data/local/room/dao/ChefAIDataBase.kt`
- Add `RecipeDraftEntity::class` to `@Database(entities = [...])`
- Add `abstract fun recipeDraftDao(): RecipeDraftDao`
- Extend `MIGRATION_3_4` to also create the `recipe_drafts` table (single migration since neither is deployed yet):

```sql
CREATE TABLE IF NOT EXISTS recipe_drafts (
    recipeId BLOB NOT NULL PRIMARY KEY,
    isNewRecipe INTEGER NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    imageUrl TEXT NOT NULL,
    selectedImageUri TEXT,
    prepTimeMinutes TEXT NOT NULL,
    cookTimeMinutes TEXT NOT NULL,
    servings TEXT NOT NULL,
    externalUrl TEXT NOT NULL,
    ingredientsJson TEXT NOT NULL,
    stepsJson TEXT NOT NULL,
    tagsJson TEXT NOT NULL,
    labelsJson TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    updatedAt INTEGER NOT NULL
)
```

### 2.7 Provide DAO via Hilt

**File**: Whichever `@Module` provides the other DAOs
- Add `@Provides` for `RecipeDraftDao`

### Tests for Phase 2
- Unit test draft mappers: `Recipe → RecipeDraft → RecipeDraftEntity → RecipeDraft` round-trip
- Unit test JSON serialization for lists (ingredients, steps, tags, labels)
- DAO test: save draft, retrieve, delete

### Risks & Edge Cases
- **JSON serialization**: Must handle UUID serialization correctly. Verify the project's existing serialization setup.
- **Stale drafts**: Need a cleanup strategy. Could clear drafts older than 7 days, or clear on successful save. At minimum, clear on save.
- **Migration bundling**: Since `version` field and draft table are both new, bundle into a single migration (3→4) if neither has been deployed. If `version` migration ships first, use 3→4 + 4→5.

---

## Phase 3: RecipeEditorViewModel (MVI)

**Goal**: Unified ViewModel supporting create and edit modes with pure reducer, dirty tracking, auto-save.

### 3.1 State definitions

**File**: `recipes/ui/editor/RecipeEditorState.kt` (NEW)

```kotlin
/** All possible user intents */
sealed class EditorAction {
    // Recipe fields
    data class TitleChanged(val title: String) : EditorAction()
    data class DescriptionChanged(val description: String) : EditorAction()
    data class PrepTimeChanged(val time: String) : EditorAction()
    data class CookTimeChanged(val time: String) : EditorAction()
    data class ServingsChanged(val servings: String) : EditorAction()
    data class ExternalUrlChanged(val url: String) : EditorAction()
    data class ImageUrlChanged(val url: String) : EditorAction()
    data class ImageSelected(val uri: String?) : EditorAction()
    object ClearImage : EditorAction()

    // Ingredients
    data class IngredientInputChanged(val input: String) : EditorAction()
    data class IngredientQuantityChanged(val quantity: String) : EditorAction()
    data class IngredientUnitChanged(val unit: String) : EditorAction()
    data class IngredientSelected(val name: String) : EditorAction()
    data class RemoveIngredient(val ingredient: RecipeIngredient) : EditorAction()

    // Steps
    data class StepInputChanged(val input: String) : EditorAction()
    object AddStep : EditorAction()
    data class RemoveStep(val step: RecipeStep) : EditorAction()
    data class MoveStepUp(val step: RecipeStep) : EditorAction()
    data class MoveStepDown(val step: RecipeStep) : EditorAction()

    // Tags
    data class TagInputChanged(val input: String) : EditorAction()
    data class AddTag(val name: String) : EditorAction()
    data class RemoveTag(val tag: Tag) : EditorAction()

    // Labels
    data class LabelInputChanged(val input: String) : EditorAction()
    data class AddLabel(val name: String) : EditorAction()
    data class RemoveLabel(val label: Label) : EditorAction()

    // Lifecycle
    object Save : EditorAction()
    object Delete : EditorAction()
    object ConfirmDelete : EditorAction()
    object DismissDeleteDialog : EditorAction()
    object ClearError : EditorAction()
}

/** Side effects emitted by the ViewModel (one-shot events) */
sealed class EditorEffect {
    object NavigateBack : EditorEffect()
    object RecipeSaved : EditorEffect()
    object RecipeDeleted : EditorEffect()
    data class ShowError(val message: String) : EditorEffect()
}

/** Immutable UI state */
data class RecipeEditorState(
    val mode: EditorMode = EditorMode.Create,

    // Form fields (same shape as before, kept flat for compose performance)
    val recipeFields: RecipeFields = RecipeFields(),
    val ingredients: IngredientsFields = IngredientsFields(),
    val steps: StepsFields = StepsFields(),
    val tags: TagsFields = TagsFields(),
    val labels: LabelsFields = LabelsFields(),

    // Status
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val saveError: String? = null,
    val isFormValid: Boolean = false,
    val isDirty: Boolean = false,

    // Original snapshot for dirty tracking (null in create mode)
    val originalDraft: RecipeDraft? = null,
)
```

Reuse existing data classes: `RecipeFields`, `IngredientsFields`, `StepsFields`, `TagsFields`, `LabelsFields` — move them from the old ViewModel file to `RecipeEditorState.kt`.

### 3.2 Pure reducer

**File**: `recipes/ui/editor/RecipeEditorReducer.kt` (NEW)

A pure function: `(RecipeEditorState, EditorAction) → RecipeEditorState`

This handles all synchronous state transformations. No side effects. Testable without coroutines or Android dependencies.

```kotlin
object RecipeEditorReducer {
    fun reduce(state: RecipeEditorState, action: EditorAction): RecipeEditorState {
        return when (action) {
            is EditorAction.TitleChanged -> state.copy(
                recipeFields = state.recipeFields.copy(title = action.title)
            ).revalidate().markDirty()

            // ... all other synchronous actions ...

            // Actions that require side effects return state unchanged
            // (ViewModel handles the effect and dispatches a result action)
            is EditorAction.Save,
            is EditorAction.Delete,
            is EditorAction.ConfirmDelete -> state

            else -> state
        }
    }

    private fun RecipeEditorState.revalidate(): RecipeEditorState { ... }
    private fun RecipeEditorState.markDirty(): RecipeEditorState { ... }
}
```

**Dirty tracking logic**:
```kotlin
private fun RecipeEditorState.markDirty(): RecipeEditorState {
    if (originalDraft == null) {
        // Create mode: dirty if any required field is non-empty
        return copy(isDirty = recipeFields.title.isNotBlank() ||
                              ingredients.selectedIngredients.isNotEmpty() ||
                              steps.steps.isNotEmpty())
    }
    // Edit mode: compare current state to original snapshot
    val currentDraft = this.toRecipeDraft()
    return copy(isDirty = currentDraft != originalDraft)
}
```

### 3.3 RecipeEditorViewModel

**File**: `recipes/ui/editor/RecipeEditorViewModel.kt` (NEW)

```kotlin
@HiltViewModel
class RecipeEditorViewModel @Inject constructor(
    private val recipesRepository: RecipesRepository,
    private val metadataRepository: MetadataRepository,
    private val sessionManager: SessionManager,
    private val recipeDraftDao: RecipeDraftDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Determine mode from nav arg
    private val recipeId: String? = savedStateHandle["recipeId"]
    val mode: EditorMode = if (recipeId != null) EditorMode.Edit(UUID.fromString(recipeId))
                           else EditorMode.Create

    private val _state = MutableStateFlow(RecipeEditorState(mode = mode, isLoading = mode is EditorMode.Edit))
    val state: StateFlow<RecipeEditorState> = _state.asStateFlow()

    private val _effects = Channel<EditorEffect>(Channel.BUFFERED)
    val effects: Flow<EditorEffect> = _effects.receiveAsFlow()

    // Metadata for autocomplete
    private val allIngredients = metadataRepository.observeAllIngredients().stateIn(...)
    private val allTags = metadataRepository.observeAllTags().stateIn(...)
    private val allLabels = metadataRepository.observeAllLabels().stateIn(...)

    // Auto-save timer
    private var autoSaveJob: Job? = null

    init {
        if (mode is EditorMode.Edit) loadRecipe(mode.recipeId)
        startAutoSave()
    }

    fun dispatch(action: EditorAction) {
        // Apply reducer for synchronous state changes
        _state.update { RecipeEditorReducer.reduce(it, action) }

        // Handle side effects
        when (action) {
            is EditorAction.Save -> save()
            is EditorAction.ConfirmDelete -> delete()
            is EditorAction.IngredientInputChanged -> updateIngredientSuggestions(action.input)
            is EditorAction.TagInputChanged -> updateTagSuggestions(action.input)
            is EditorAction.LabelInputChanged -> updateLabelSuggestions(action.input)
            else -> { /* pure state change, already handled by reducer */ }
        }
    }

    private fun loadRecipe(recipeId: UUID) { ... }
    private fun save() { ... }
    private fun delete() { ... }

    private fun startAutoSave() {
        autoSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000) // 10 seconds
                if (_state.value.isDirty) {
                    saveDraftToRoom()
                }
            }
        }
    }

    private suspend fun saveDraftToRoom() {
        withContext(ioDispatcher) {
            val draft = _state.value.toRecipeDraft()
            recipeDraftDao.saveDraft(draft.toRecipeDraftEntity())
        }
    }

    // On save success, clear draft
    private fun onSaveSuccess() {
        viewModelScope.launch(ioDispatcher) {
            recipeDraftDao.deleteDraft(_state.value.recipeFields./* recipeId */)
        }
        _effects.trySend(EditorEffect.RecipeSaved)
    }
}
```

**Key behaviors**:
- `loadRecipe()`: Fetch Recipe from repository → convert to RecipeDraft → populate state + set `originalDraft` for dirty tracking. Also check for existing draft in Room (if user had a previous unsaved edit, restore from draft instead).
- `save()`: In create mode → `recipesRepository.createRecipe()`. In edit mode → `recipesRepository.updateRecipe()`. Both mark entity as PENDING (happens in mapper).
- `delete()`: `recipesRepository.softDeleteRecipe(recipeId)` → emit `RecipeDeleted` effect.
- Auto-save: Every 10s if dirty, write current state to `RecipeDraftDao`.
- On `onCleared()`: Cancel auto-save job, persist final draft if dirty.

### Tests for Phase 3
- **RecipeEditorReducerTest**: Test every action produces correct state. Pure functions = easy to test.
  - `TitleChanged updates title and revalidates`
  - `IngredientSelected adds ingredient and clears input`
  - `MoveStepUp swaps steps correctly`
  - `isDirty is true when state differs from original`
  - `isDirty is false when state matches original`
  - `isFormValid requires title + description + prep + cook + servings + 1 ingredient + 1 step`
- **RecipeEditorViewModelTest**: Test side effects with fake dependencies.
  - `Edit mode loads recipe and populates state`
  - `Edit mode restores draft if one exists`
  - `Save in create mode calls createRecipe`
  - `Save in edit mode calls updateRecipe`
  - `Delete calls softDeleteRecipe and emits effect`
  - `Auto-save writes draft every 10 seconds when dirty`
  - `Save clears draft from Room`

### Risks & Edge Cases
- **Draft vs Recipe conflict**: If user opens editor for recipe A, auto-save writes draft, then recipe A is updated by sync in the background → the draft is stale. **Mitigation**: On save, compare `version` field. If version changed since edit started, show conflict warning (future Phase — for now, last-write-wins with version bump).
- **Process death during save**: The save operation is fire-and-forget from the UI perspective. If the process dies mid-save, the draft is still in Room, so the user can resume. The recipe may or may not have been persisted.
- **Double navigation**: If user taps Save twice quickly, guard with `isSaving` flag (already in state).

---

## Phase 4: UI Refactor

**Goal**: Unified `RecipeEditorScreen` that replaces `CreateRecipeScreen`, supporting both modes.

### 4.1 Move components

Move `recipes/ui/create/components/` → `recipes/ui/editor/components/`
- `AutocompleteInput.kt`
- `IngredientInput.kt`
- `StepCard.kt`
- `ImageUploadContent.kt`

Update package declarations and imports. No logic changes.

### 4.2 Create RecipeEditorScreen

**File**: `recipes/ui/editor/RecipeEditorScreen.kt` (NEW, based on `CreateRecipeScreen`)

Key changes from `CreateRecipeScreen`:
- Accept `viewModel: RecipeEditorViewModel = hiltViewModel()` instead of `CreateRecipeViewModel`
- All callbacks go through `viewModel.dispatch(action)` instead of direct method calls
- ActionBar title changes based on mode: "New Recipe" / "Edit Recipe"
- Show delete button in ActionBar when `mode is EditorMode.Edit`
- Show loading spinner while `isLoading` (edit mode preloading)
- Handle `EditorEffect` in a `LaunchedEffect` for navigation/snackbar
- Add `BackHandler` that checks `isDirty` and shows `UnsavedChangesDialog`

```kotlin
@Composable
fun RecipeEditorScreen(
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: RecipeEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showUnsavedDialog by remember { mutableStateOf(false) }

    // Handle one-shot effects
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditorEffect.RecipeSaved -> onNavigateBack()
                EditorEffect.RecipeDeleted -> onNavigateBack()
                is EditorEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                EditorEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    // Back navigation with dirty check
    BackHandler(enabled = true) {
        if (state.isDirty) showUnsavedDialog = true
        else onNavigateBack()
    }

    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            onDiscard = onNavigateBack,
            onKeepEditing = { showUnsavedDialog = false }
        )
    }

    if (state.isLoading) {
        LoadingContent()
    } else {
        // Same Column structure as CreateRecipeScreen
        // but all onXxxChange callbacks dispatch EditorActions
    }
}
```

### 4.3 UnsavedChangesDialog

**File**: `recipes/ui/editor/components/UnsavedChangesDialog.kt` (NEW)
```kotlin
@Composable
fun UnsavedChangesDialog(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
)
```
Standard AlertDialog with "Discard" and "Keep Editing" buttons.

### 4.4 ActionBar update

The existing `ActionBar` composable gets enhanced:
```kotlin
@Composable
private fun ActionBar(
    mode: EditorMode,
    saveButtonOnClick: () -> Unit,
    saveButtonEnabled: Boolean,
    savingState: Boolean,
    onDelete: (() -> Unit)? = null,  // ← shown only in edit mode
)
```

### 4.5 Delete confirmation dialog

Add `DeleteConfirmationDialog` shown when `state.showDeleteConfirmation == true`.

### 4.6 Delete old create package

After verification, delete:
- `recipes/ui/create/CreateRecipeScreen.kt`
- `recipes/ui/create/CreateRecipeViewModel.kt`
- `recipes/ui/create/components/` (already moved)

### Tests for Phase 4
- Compose UI tests (optional, lower priority):
  - ActionBar shows "Edit Recipe" in edit mode
  - Delete button visible in edit mode, hidden in create mode
  - Back press shows dialog when dirty
  - Save button disabled when form invalid

### Risks & Edge Cases
- **Image picker launcher**: Must be created at composable scope (not inside callbacks). The existing `rememberLauncherForActivityResult` pattern works fine — just dispatch `ImageSelected` action in the callback.
- **Recomposition performance**: The state object is large. Consider using `derivedStateOf` for computed values like `isFormValid` if recomposition becomes a concern. The current approach (revalidate on every action) is fine for now.

---

## Phase 5: Navigation Integration

**Goal**: Wire the editor into the nav graph for both create and edit flows.

### 5.1 Update AppDestinations

**File**: `core/ui/navigation/AppDestinations.kt`

Replace `CREATE_RECIPE` with a unified `RECIPE_EDITOR` that accepts an optional `recipeId`:

```kotlin
// In ScreenBaseRoutes:
const val RECIPE_EDITOR = "recipe_editor_screen"

// In AppDestinationArgs:
const val RECIPE_ID_ARG = "recipeId"  // reuse existing name

// In AppDestinations enum:
RECIPE_EDITOR(
    R.string.app_dest_title_recipe_editor,
    "${ScreenBaseRoutes.RECIPE_EDITOR}?$RECIPE_ID_ARG={$RECIPE_ID_ARG}"
),
```

Keep `CREATE_RECIPE` as a deprecated alias or remove it outright if nothing else references it.

### 5.2 Update NavigationActions

**File**: `core/ui/navigation/AppDestinations.kt`

```kotlin
fun navigateToCreateRecipe() {
    navController.navigate(ScreenBaseRoutes.RECIPE_EDITOR)
}

fun navigateToEditRecipe(recipeId: UUID) {
    navController.navigate("${ScreenBaseRoutes.RECIPE_EDITOR}?recipeId=$recipeId")
}
```

### 5.3 Update ChefAINavGraph

**File**: `core/ui/navigation/ChefAINavGraph.kt`

Replace the `CREATE_RECIPE` composable with:
```kotlin
composable(
    route = AppDestinations.RECIPE_EDITOR.route,
    arguments = listOf(
        navArgument(AppDestinationArgs.RECIPE_ID_ARG) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        }
    )
) {
    RecipeEditorScreen(
        onNavigateBack = { navController.popBackStack() },
        snackbarHostState = snackbarHostState
    )
}
```

### 5.4 Add edit button to RecipeDetailsScreen

**File**: `recipes/ui/details/RecipeDetailsScreen.kt`

Add an "Edit" FAB or toolbar button that navigates to `navigateToEditRecipe(recipeUuid)`.

The edit button should only be visible when the current user is the recipe creator:
```kotlin
// In RecipeDetailsScreen, add onEditClick parameter
// In RecipeDetailsViewModel, expose isOwnedByCurrentUser
```

**File**: `recipes/ui/details/RecipeDetailsViewModel.kt`
- Add `val isOwnedByCurrentUser: StateFlow<Boolean>` — compare `recipe.creator.uuid == sessionManager.getCurrentUserId()`

### 5.5 Update FAB on Recipes screen

**File**: `core/ui/navigation/ChefAINavGraph.kt`
- `onCreateRecipeClick` already calls `navActions.navigateToCreateRecipe()` which now routes to the editor

### 5.6 Update string resources

**File**: `res/values/strings.xml`
- Add `app_dest_title_recipe_editor`
- Add `edit_recipe_header`
- Add `unsaved_changes_title`, `unsaved_changes_message`
- Add `discard_button`, `keep_editing_button`
- Add `delete_recipe_button`, `delete_recipe_confirmation_title`, `delete_recipe_confirmation_message`

### Tests for Phase 5
- Navigation test: FAB still navigates to editor in create mode
- Navigation test: Edit button on details screen navigates with recipeId
- Navigation test: Back from editor returns to previous screen

### Risks & Edge Cases
- **Deep link handling**: If using deep links to recipe editor, ensure `recipeId` arg is properly optional
- **Nav back stack**: When editor navigates back after save/delete, it should return to the correct screen (details or recipes list)
- **Edit non-owned recipe**: The edit button must be hidden for recipes not owned by the current user. The `isOwnedByCurrentUser` check handles this.

---

## Phase 6: Testing & Polish

**Goal**: Comprehensive test coverage and cleanup.

### 6.1 Unit tests

| Test file | Tests |
|-----------|-------|
| `RecipeEditorReducerTest.kt` | All action → state transformations, validation, dirty tracking |
| `RecipeEditorViewModelTest.kt` | Load recipe, save (create/edit), delete, auto-save, effects |
| `DraftMapperTest.kt` | Round-trip: Recipe → Draft → Entity → Draft, JSON serialization |
| `DefaultRecipeRepositoryTest.kt` | Add tests for updateRecipe (full), softDeleteRecipe |

### 6.2 Integration tests (androidTest)

| Test file | Tests |
|-----------|-------|
| `RecipeDraftDaoTest.kt` | Save, retrieve, delete drafts; verify JSON fields survive round-trip |
| `RecipeDatabaseMigrationTest.kt` | Test MIGRATION_3_4 with MigrationTestHelper |

### 6.3 Fake DAOs

- Create `FakeRecipeDraftDao` for ViewModel tests (in-memory map)

### 6.4 Cleanup

- Remove `recipes/ui/create/` package entirely
- Remove `CreateRecipeViewModel` and `CreateRecipeScreen` imports throughout
- Update any remaining references to old routes
- Run full build + test suite

---

## Cross-Cutting Concerns

### Conflict Resolution (Future)
The `version` field is added now but conflict resolution UI is deferred. Current behavior:
- On save, version is preserved from the loaded recipe
- If sync detects a version mismatch, it sets `syncState = CONFLICT`
- Future work: show conflict UI in the editor when `syncState == CONFLICT`

### Image Upload
Images continue to be stored as URI strings. The editor stores `selectedImageUri` (local file) and `imageUrl` (remote URL). Actual upload happens during sync (existing behavior). No changes needed.

### Accessibility
- All new dialogs use Material3 AlertDialog (built-in accessibility)
- Delete button has contentDescription
- Form fields already have labels

### Performance
- The draft auto-save uses `withContext(ioDispatcher)` to avoid main thread I/O
- JSON serialization of draft lists is cheap (typically < 50 items)
- The reducer is a pure function with no allocations beyond the new state copy

---

## Execution Order Summary

| Phase | Depends On | Estimated Scope |
|-------|------------|-----------------|
| Phase 1: Data Layer | None | 7 files modified, 1 new DAO method |
| Phase 2: Draft Infrastructure | Phase 1 (migration) | 4 new files, 2 modified |
| Phase 3: ViewModel (MVI) | Phase 1 + 2 | 3 new files, core logic |
| Phase 4: UI Refactor | Phase 3 | 2 new files, 1 major refactor, move components |
| Phase 5: Navigation | Phase 4 | 4 files modified |
| Phase 6: Testing & Polish | Phase 1-5 | 5+ test files |
