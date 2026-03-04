# Skill: Update Existing Code

Use this skill when modifying existing code — bug fixes, refactors, adding functionality to existing files.

---

## Before You Start

1. **Read the file(s) you're modifying** — never edit blind
2. **Read adjacent files** — understand the context (ViewModel → Repository → DAO chain)
3. **Check tests** — find existing tests for the code you're changing
4. **Check gotchas** — read `docs/claude/gotchas.md` for known pitfalls in the area

---

## Core Principle: Minimal, Focused Diffs

- **Edit, don't rewrite** — propose diffs targeting only what needs to change
- **Don't touch what isn't broken** — resist the urge to "clean up" nearby code
- **Don't add docstrings/comments to code you didn't change**
- **Don't rename variables or reformat code outside your change scope**
- **Don't add error handling for scenarios that can't happen**

---

## Change Categories

### Bug Fix
1. Reproduce: understand the failing behavior from logs, tests, or description
2. Identify root cause: trace through the call chain
3. Fix at the right layer (don't patch symptoms in UI when the bug is in Data)
4. Add a test that would have caught the bug
5. Verify the fix doesn't break existing tests

### Feature Addition to Existing Screen
1. Read the existing UiState — add new fields with defaults
2. Add new UiEvent variants if needed (sealed interface is additive)
3. Add ViewModel method to handle the new action
4. Update the Content composable to render the new state
5. Update previews to show the new state
6. Add/update ViewModel test

### Refactor
1. Explain the motivation before proposing changes
2. Move in small, verifiable steps — each step should compile and pass tests
3. If renaming, use IDE-safe renames (update all references)
4. Don't combine refactors with feature changes in one diff

### Schema Change
1. Add/modify the `@Entity` class
2. Write the migration in `ChefAIDataBase.kt`
3. Update the DAO if query shape changed
4. Update mapper functions
5. Update repository if needed
6. Add in-memory DAO test for the migration
7. Bump the database version

---

## State Modification Patterns

### Adding a field to UiState
```kotlin
// BEFORE
data class FeatureUiState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = false
)

// AFTER — add with default, no breaking change
data class FeatureUiState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = false,
    val selectedFilter: Filter = Filter.ALL  // new field
)
```

### Adding a new event
```kotlin
// Just add a new variant — sealed interfaces are additive
sealed interface FeatureUiEvent {
    data class ShowSnackbar(@StringRes val message: Int) : FeatureUiEvent
    data object NavigateBack : FeatureUiEvent
    data class OpenFilter(val currentFilter: Filter) : FeatureUiEvent  // new
}
```

### Modifying a repository method
```kotlin
// If changing a signature, update:
// 1. The interface in domain/repository/
// 2. The implementation in data/repository/
// 3. The fake in test/
// 4. All callers (ViewModel, UseCase)
```

---

## Updating Compose UI

### Adding a section to an existing screen
```kotlin
// Find the existing Content composable
// Add the new section in the correct position
// Don't restructure the whole layout — insert at the right place

// GOOD: Targeted addition
LazyColumn {
    // existing items...

    item {
        NewSectionHeader(title = stringResource(R.string.new_section))
    }
    items(newItems) { item ->
        NewItemCard(item = item, onClick = onItemClick)
    }
}
```

### Modifying a component's appearance
```kotlin
// Change only the visual properties you need
// Don't refactor the component structure unless asked

// GOOD: Targeted style change
Text(
    text = recipe.title,
    style = MaterialTheme.typography.titleLarge,  // was titleMedium
    maxLines = 2,  // added
    overflow = TextOverflow.Ellipsis  // added
)
```

---

## Testing Updates

### When to add tests
- Bug fix: always add a regression test
- New ViewModel logic: add test covering the new behavior
- New UI state: test the ViewModel emits the correct state

### When to update tests
- Changed ViewModel behavior: update assertions
- Changed UiState shape: update test data construction
- Changed repository interface: update fake implementation

### Test update pattern
```kotlin
// Don't rewrite the whole test file
// Add new test methods or modify specific assertions

@Test
fun `new filter changes displayed items`() = runTest {
    fakeRepository.setRecipes(TestData.mixedRecipes)

    viewModel.onFilterChange(Filter.VEGETARIAN)

    viewModel.uiState.test {
        val state = awaitItem()
        assertTrue(state.items.all { it.isVegetarian })
        assertEquals(Filter.VEGETARIAN, state.selectedFilter)
    }
}
```

---

## Checklist Before Submitting Changes

- [ ] Read every file being modified before editing
- [ ] Changes are minimal and focused on the task
- [ ] No unrelated formatting or cleanup changes
- [ ] Existing tests still pass
- [ ] New test added for new behavior or bug fix
- [ ] UiState defaults maintained (no breaking changes)
- [ ] If repository interface changed: fake updated too
- [ ] If schema changed: migration + DAO test added
- [ ] Previews updated if UI changed
- [ ] No `!!`, `GlobalScope`, or swallowed `CancellationException` introduced

---

## Common Mistakes When Updating Code

1. **Editing without reading** — leads to style mismatches and broken patterns
2. **Rewriting entire files** — creates noisy diffs, hides the real change
3. **Fixing things that aren't broken** — adds risk with no value
4. **Forgetting to update the fake** — causes test compilation failures
5. **Adding a ViewModel dependency without @Inject** — Hilt crashes at runtime
6. **Changing a sealed interface to sealed class** — breaks existing `when` exhaustiveness
7. **Removing a UiState default** — breaks all existing constructors
