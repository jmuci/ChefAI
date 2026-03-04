# Skill: Build a Compose Component

Use this skill when creating a new Compose screen, reusable component, or modifying existing UI.

---

## Before You Start

1. Read `docs/claude/conventions.md` for naming rules
2. Read `docs/claude/gotchas.md` — especially items 1-4 (Compose section)
3. Read nearby existing components to match the project style
4. Check `core/ui/components/` — the component may already exist or a similar one can be extended

---

## Screen Architecture

Every screen follows the **Container + Content** pattern:

```kotlin
// 1. CONTAINER — handles ViewModel injection and event collection
@Composable
fun FeatureScreen(
    viewModel: FeatureViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
    onNavigateToDetail: (UUID) -> Unit = {},
    // other navigation callbacks
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is FeatureUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(event.message),
                        duration = SnackbarDuration.Short
                    )
                }
                is FeatureUiEvent.NavigateTo -> onNavigateToDetail(event.id)
            }
        }
    }

    FeatureContent(
        uiState = uiState,
        onAction = viewModel::onAction,  // or individual callbacks
        onItemClick = onNavigateToDetail
    )
}

// 2. CONTENT — stateless, previewable, testable
@Composable
fun FeatureContent(
    uiState: FeatureUiState,
    onAction: (FeatureAction) -> Unit = {},
    onItemClick: (UUID) -> Unit = {},
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> LoadingContent()
        uiState.items.isEmpty() -> EmptyContent(
            title = R.string.no_items_title,
            subtitle = R.string.no_items_subtitle,
            noRecipesIconRes = R.drawable.ic_empty_state
        )
        else -> {
            LazyColumn(modifier = modifier.fillMaxSize()) {
                items(items = uiState.items, key = { it.uuid }) { item ->
                    ItemCard(item = item, onClick = { onItemClick(item.uuid) })
                }
            }
        }
    }
}
```

**Rules:**
- Screen function: ViewModel injection, event collection, navigation wiring
- Content function: pure UI, receives state + callbacks, no ViewModel reference
- Always handle Loading, Empty, and Success states
- Use `LoadingContent()` and `EmptyContent()` from `core/util/ComposeUtils.kt`

---

## Reusable Component Pattern

```kotlin
@Composable
fun RecipeCard(
    recipe: RecipePreview,                    // data
    onClick: (UUID) -> Unit = {},             // callbacks with defaults
    onSaveToCollection: (UUID) -> Unit = {},
    modifier: Modifier = Modifier             // always last, always defaulted
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = { onClick(recipe.uuid) }
    ) {
        // Component content
    }
}
```

**Rules:**
- Data params first, callbacks second, `modifier` last
- All callbacks default to `= {}`
- Use Material3 components (`Card`, `Surface`, `Text` with `MaterialTheme.typography`)
- Use `dimensionResource()` for spacing where dimension resources exist

---

## Image Loading (Coil 3)

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(recipe.imageUrlThumbnail.ifEmpty { null })  // guard empty strings!
        .crossfade(true)
        .build(),
    placeholder = painterResource(R.drawable.ic_img_placeholder),
    error = painterResource(R.drawable.ic_img_error),
    contentDescription = stringResource(R.string.recipe_image_content_description),
    contentScale = ContentScale.Crop,
    modifier = Modifier
        .size(100.dp)
        .clip(RoundedCornerShape(8.dp))
)
```

**Rules:**
- Always use `ImageRequest.Builder` — not just the URL string
- Always provide `placeholder` and `error` drawables
- Guard empty strings with `.ifEmpty { null }`
- Use `crossfade(true)` for smooth transitions
- Add `contentDescription` for accessibility

---

## Card with Image Overlay (Gradient Pattern)

Used by `LargeCard`, `SquaredCardComponent`:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // Layer 1: Background image
    AsyncImage(model = ..., contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())

    // Layer 2: Gradient scrim for text legibility
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    startY = 100f
                )
            )
    )

    // Layer 3: Content overlay
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Tags at top
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            recipe.labels.take(2).forEach { label ->
                InfoChip(text = label.displayName, type = InfoChipType.LABEL)
            }
        }
        // Title + metadata at bottom
        Column { /* ... */ }
    }
}
```

---

## Previews

Every component MUST have light and dark previews:

```kotlin
@Preview(showBackground = true)
@Composable
private fun FeatureContentPreview() {
    ChefAITheme {
        FeatureContent(
            uiState = FeatureUiState(items = PreviewData.recipePreviewList)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FeatureContentDarkPreview() {
    ChefAITheme(darkTheme = true) {
        FeatureContent(
            uiState = FeatureUiState(items = PreviewData.recipePreviewList)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FeatureContentEmptyPreview() {
    ChefAITheme {
        FeatureContent(uiState = FeatureUiState(items = emptyList()))
    }
}
```

**Rules:**
- Use `PreviewData` from `core/ui/preview/` — realistic data catches layout bugs
- Include: populated state, empty state, loading state (if applicable)
- Wrap in `ChefAITheme` (light and dark)

---

## Form Screens

```kotlin
@Composable
fun LoginScreenContent(
    uiState: LoginUiState,
    onEmailChange: (String) -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onLoginClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Individual callbacks per field
    OutlinedTextField(
        value = uiState.email,
        onValueChange = onEmailChange,
        isError = uiState.emailError != null,
        supportingText = uiState.emailError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )
}
```

**Rules:**
- One callback per field (not a single `onAction` for form inputs)
- Nullable error strings in UiState (`emailError: String? = null`)
- Clear error on field change in ViewModel
- Wire `ImeAction` (Next, Done) and `KeyboardActions`

---

## Checklist

Before submitting a new Compose component:

- [ ] Stateless content composable (no ViewModel reference)
- [ ] `modifier: Modifier = Modifier` as last parameter
- [ ] Callbacks have `= {}` defaults
- [ ] Loading + empty + error states handled
- [ ] Images use `AsyncImage` with placeholder/error/crossfade
- [ ] Light + dark preview with `PreviewData`
- [ ] Material3 components (no deprecated APIs)
- [ ] No business logic in composables
- [ ] `collectAsStateWithLifecycle()` (not `collectAsState()`)
- [ ] String resources (no hardcoded strings)
