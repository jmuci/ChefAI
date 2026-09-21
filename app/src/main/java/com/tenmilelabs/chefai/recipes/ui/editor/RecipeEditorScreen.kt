package com.tenmilelabs.chefai.recipes.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.core.ui.components.flat.DragReorderColumn
import com.tenmilelabs.chefai.core.ui.components.flat.FlatBlockButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButtonVariant
import com.tenmilelabs.chefai.core.ui.components.flat.FlatChip
import com.tenmilelabs.chefai.core.ui.components.flat.FlatField
import com.tenmilelabs.chefai.core.ui.components.flat.FlatTag
import com.tenmilelabs.chefai.core.ui.components.flat.FlatTagTone
import com.tenmilelabs.chefai.core.ui.components.flat.RowRule
import com.tenmilelabs.chefai.core.ui.components.flat.SectionRule
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.navigation.ChefAINavigation
import com.tenmilelabs.chefai.core.ui.navigation.ChefAITopAppBarWithTag
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.core.util.QuantityFormat
import com.tenmilelabs.chefai.recipes.domain.model.EditorMode
import com.tenmilelabs.chefai.recipes.ui.components.DeleteConfirmationDialog
import com.tenmilelabs.chefai.recipes.ui.editor.components.AutocompleteInput
import com.tenmilelabs.chefai.recipes.ui.editor.components.IngredientAddForm
import com.tenmilelabs.chefai.recipes.ui.editor.components.IngredientRow
import com.tenmilelabs.chefai.recipes.ui.editor.components.ImageUploadContent
import com.tenmilelabs.chefai.recipes.ui.editor.components.StepCard
import com.tenmilelabs.chefai.recipes.ui.editor.components.UnsavedChangesDialog
import kotlin.math.abs

@Composable
fun RecipeEditorScreen(
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: RecipeEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val attemptClose = {
        if (state.isDirty) showUnsavedDialog = true else onNavigateBack()
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditorEffect.RecipeSaved -> onNavigateBack()
                EditorEffect.RecipeDeleted -> onNavigateBack()
                is EditorEffect.ShowError -> {
                    snackbarHostState.showSnackbar(
                        message = effect.message,
                        duration = SnackbarDuration.Short,
                    )
                }
                EditorEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    BackHandler(enabled = true) { attemptClose() }

    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            onDiscard = { viewModel.dispatch(EditorAction.DiscardDraft) },
            onKeepEditing = { showUnsavedDialog = false },
        )
    }

    if (state.showDeleteConfirmation) {
        DeleteConfirmationDialog(
            onConfirm = { viewModel.dispatch(EditorAction.ConfirmDelete) },
            onDismiss = { viewModel.dispatch(EditorAction.DismissDeleteDialog) },
        )
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        viewModel.dispatch(EditorAction.ImageSelected(uri?.toString()))
    }

    state.saveError?.let { message ->
        val snackBarText = stringResource(R.string.snackbar_save_error)
        LaunchedEffect(snackbarHostState, message, snackBarText) {
            snackbarHostState.showSnackbar(
                message = snackBarText,
                duration = SnackbarDuration.Short,
            )
            viewModel.dispatch(EditorAction.ClearError)
        }
    }

    val focusManager = LocalFocusManager.current

    if (state.isLoading) {
        LoadingContent()
        return
    }

    Column(
        modifier = Modifier
            .testTag("RecipeEditorScreen")
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ChefAITopAppBarWithTag(
            title = if (state.mode is EditorMode.Edit) {
                stringResource(R.string.edit_recipe_header)
            } else {
                stringResource(R.string.recipe_editor_new_title)
            },
            tag = stringResource(R.string.recipe_editor_draft_tag),
            navigation = ChefAINavigation.Close(onClick = attemptClose),
            actions = {
                if (state.mode is EditorMode.Edit) {
                    DeleteAction(
                        isDeleting = state.isDeleting,
                        enabled = !state.isSaving,
                        onClick = { viewModel.dispatch(EditorAction.Delete) },
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ImageUploadSection(
                recipeState = state.recipeFields,
                onImageUrlChange = { viewModel.dispatch(EditorAction.ImageUrlChanged(it)) },
                onSelectImage = { imagePickerLauncher.launch("image/*") },
                onClearImage = { viewModel.dispatch(EditorAction.ClearImage) },
            )

            CoreRecipeForm(
                recipeState = state.recipeFields,
                onTitleChange = { viewModel.dispatch(EditorAction.TitleChanged(it)) },
                onDescriptionChange = { viewModel.dispatch(EditorAction.DescriptionChanged(it)) },
                onPrepTimeChange = { viewModel.dispatch(EditorAction.PrepTimeChanged(it)) },
                onCookTimeChange = { viewModel.dispatch(EditorAction.CookTimeChanged(it)) },
                onServingsChange = { viewModel.dispatch(EditorAction.ServingsChanged(it)) },
                onCaloriesChange = { viewModel.dispatch(EditorAction.CaloriesChanged(it)) },
                onProteinChange = { viewModel.dispatch(EditorAction.ProteinChanged(it)) },
                onExternalUrlChange = { viewModel.dispatch(EditorAction.ExternalUrlChanged(it)) },
            )

            VisibilitySection(
                privacy = state.recipeFields.privacy,
                onPrivacyChange = { viewModel.dispatch(EditorAction.PrivacyChanged(it)) },
            )

            IngredientsSection(
                ingredientsState = state.ingredients,
                onIngredientInputChange = { viewModel.dispatch(EditorAction.IngredientInputChanged(it)) },
                onIngredientQuantityChange = { viewModel.dispatch(EditorAction.IngredientQuantityChanged(it)) },
                onIngredientUnitChange = { viewModel.dispatch(EditorAction.IngredientUnitChanged(it)) },
                onIngredientSelected = { viewModel.selectIngredient(it) },
                onRemoveIngredient = { viewModel.dispatch(EditorAction.RemoveIngredient(it)) },
                onReorderIngredient = { from, to ->
                    reorderRows<RecipeIngredient, EditorAction>(
                        rows = state.ingredients.selectedIngredients,
                        from = from,
                        to = to,
                        dispatchUp = { EditorAction.MoveIngredientUp(it) },
                        dispatchDown = { EditorAction.MoveIngredientDown(it) },
                        dispatch = viewModel::dispatch,
                    )
                },
            )

            StepsSection(
                stepsState = state.steps,
                onStepInputChange = { viewModel.dispatch(EditorAction.StepInputChanged(it)) },
                onAddStep = { viewModel.dispatch(EditorAction.AddStep) },
                onRemoveStep = { viewModel.dispatch(EditorAction.RemoveStep(it)) },
                onReorderStep = { from, to ->
                    reorderRows<RecipeStep, EditorAction>(
                        rows = state.steps.steps,
                        from = from,
                        to = to,
                        dispatchUp = { EditorAction.MoveStepUp(it) },
                        dispatchDown = { EditorAction.MoveStepDown(it) },
                        dispatch = viewModel::dispatch,
                    )
                },
            )

            TagsSection(
                tags = state.tags,
                onTagInputChange = { viewModel.dispatch(EditorAction.TagInputChanged(it)) },
                onAddTag = { viewModel.addTagByName(it) },
                onRemoveTag = { viewModel.dispatch(EditorAction.RemoveTag(it)) },
            )

            LabelsSection(
                labels = state.labels,
                onLabelInputChange = { viewModel.dispatch(EditorAction.LabelInputChanged(it)) },
                onAddLabel = { viewModel.addLabelByName(it) },
                onRemoveLabel = { viewModel.dispatch(EditorAction.RemoveLabel(it)) },
            )

            Spacer(Modifier.height(8.dp))
        }

        SaveFooter(
            enabled = state.isFormValid && !state.isSaving,
            isSaving = state.isSaving,
            onSave = {
                focusManager.clearFocus()
                viewModel.dispatch(EditorAction.Save)
            },
        )
    }
}

/**
 * Turns one drag gesture's (from, to) into the right number of dispatched single-step moves — see
 * [DragReorderColumn] for why the reducer only needs to know how to move a row by one, not to an
 * arbitrary index.
 */
private fun <T, A> reorderRows(
    rows: List<T>,
    from: Int,
    to: Int,
    dispatchUp: (T) -> A,
    dispatchDown: (T) -> A,
    dispatch: (A) -> Unit,
) {
    val row = rows.getOrNull(from) ?: return
    val step = if (to > from) dispatchDown else dispatchUp
    repeat(abs(to - from)) { dispatch(step(row)) }
}

@Composable
private fun DeleteAction(
    isDeleting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .flatClickable(onClick = onClick, enabled = enabled && !isDeleting, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        if (isDeleting) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            Icon(
                painter = painterResource(ChefAIIcons.Trash),
                contentDescription = stringResource(R.string.delete_recipe_button),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.chefColors.accentText,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun ImageUploadSection(
    recipeState: RecipeFields,
    onImageUrlChange: (String) -> Unit,
    onSelectImage: () -> Unit,
    onClearImage: () -> Unit,
) {
    ImageUploadContent(
        localImagePath = recipeState.localImagePath,
        imageUrl = recipeState.imageUrl,
        onImageUrlChange = onImageUrlChange,
        onSelectImage = onSelectImage,
        onClearImage = onClearImage,
    )
}

@Composable
private fun CoreRecipeForm(
    recipeState: RecipeFields,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPrepTimeChange: (String) -> Unit,
    onCookTimeChange: (String) -> Unit,
    onServingsChange: (String) -> Unit,
    onCaloriesChange: (String) -> Unit,
    onProteinChange: (String) -> Unit,
    onExternalUrlChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FlatField(
            value = recipeState.title,
            onValueChange = onTitleChange,
            label = stringResource(R.string.label_recipe_title),
            placeholder = stringResource(R.string.placeholder_recipe_title),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        FlatField(
            value = recipeState.description,
            onValueChange = onDescriptionChange,
            label = stringResource(R.string.label_description),
            placeholder = stringResource(R.string.placeholder_description),
            minLines = 3,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlatField(
                value = recipeState.prepTimeMinutes,
                onValueChange = onPrepTimeChange,
                label = stringResource(R.string.label_prep_time),
                placeholder = stringResource(R.string.placeholder_minutes),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            FlatField(
                value = recipeState.cookTimeMinutes,
                onValueChange = onCookTimeChange,
                label = stringResource(R.string.label_cook_time),
                placeholder = stringResource(R.string.placeholder_minutes),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            FlatField(
                value = recipeState.servings,
                onValueChange = onServingsChange,
                label = stringResource(R.string.label_servings),
                placeholder = stringResource(R.string.placeholder_servings),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
        }

        // Optional and per-serving — as published by the source or entered by hand, never computed
        // from ingredients. Not part of the handoff's screen 19; kept and restyled rather than
        // dropped, since nothing asked for these fields to go away.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlatField(
                value = recipeState.caloriesPerServing,
                onValueChange = onCaloriesChange,
                label = stringResource(R.string.label_calories),
                placeholder = stringResource(R.string.placeholder_calories),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            FlatField(
                value = recipeState.proteinGramsPerServing,
                onValueChange = onProteinChange,
                label = stringResource(R.string.label_protein),
                placeholder = stringResource(R.string.placeholder_protein),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
        }

        FlatField(
            value = recipeState.externalUrl,
            onValueChange = onExternalUrlChange,
            label = stringResource(R.string.label_external_url),
            placeholder = stringResource(R.string.placeholder_external_url),
            leadingIcon = ChefAIIcons.Link,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
    }
}

/** Left-to-right order is deliberate: Private first — see [RecipeFields.privacy]'s default. */
private val PRIVACY_OPTIONS = listOf(RecipePrivacy.PRIVATE, RecipePrivacy.PUBLIC)

@Composable
private fun VisibilitySection(
    privacy: RecipePrivacy,
    onPrivacyChange: (RecipePrivacy) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.section_visibility).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.chefColors.accentText,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRIVACY_OPTIONS.forEach { option ->
                FlatChip(
                    label = stringResource(
                        if (option == RecipePrivacy.PRIVATE) R.string.privacy_private else R.string.privacy_public
                    ),
                    selected = privacy == option,
                    onClick = { onPrivacyChange(option) },
                    singleSelect = true,
                    modifier = Modifier.testTag("PrivacyOption_${option.name}"),
                )
            }
        }
        Text(
            text = stringResource(
                if (privacy == RecipePrivacy.PRIVATE) R.string.visibility_private_description
                else R.string.visibility_public_description
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IngredientsSection(
    ingredientsState: IngredientsFields,
    onIngredientInputChange: (String) -> Unit,
    onIngredientQuantityChange: (String) -> Unit,
    onIngredientUnitChange: (String) -> Unit,
    onIngredientSelected: (String) -> Unit,
    onRemoveIngredient: (RecipeIngredient) -> Unit,
    onReorderIngredient: (from: Int, to: Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.section_ingredients).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.chefColors.accentText,
            )
            Text(
                text = pluralIngredientCount(ingredientsState.selectedIngredients.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (ingredientsState.selectedIngredients.isNotEmpty()) {
            SectionRule()
            DragReorderColumn(
                items = ingredientsState.selectedIngredients,
                itemKey = { it.ingredientId },
                onMove = onReorderIngredient,
            ) { ingredient, index, dragHandle ->
                Column {
                    if (index > 0) RowRule()
                    IngredientRow(
                        quantityText = QuantityFormat.decimal(ingredient.quantity),
                        unit = ingredient.unit,
                        name = ingredient.ingredientDisplayName,
                        dragHandle = dragHandle,
                        onDelete = { onRemoveIngredient(ingredient) },
                    )
                }
            }
            SectionRule()
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IngredientAddForm(
                ingredientInput = ingredientsState.input,
                onIngredientInputChange = onIngredientInputChange,
                ingredientQuantity = ingredientsState.quantity,
                onIngredientQuantityChange = onIngredientQuantityChange,
                ingredientUnit = ingredientsState.unit,
                onIngredientUnitChange = onIngredientUnitChange,
                suggestions = ingredientsState.suggestions,
                onSuggestionClick = onIngredientSelected,
                onAddIngredient = {
                    if (ingredientsState.input.isNotBlank()) onIngredientSelected(ingredientsState.input)
                },
            )
            FlatBlockButton(
                text = stringResource(R.string.button_add_ingredient),
                onClick = {
                    if (ingredientsState.input.isNotBlank()) onIngredientSelected(ingredientsState.input)
                },
                variant = FlatButtonVariant.Secondary,
                enabled = ingredientsState.input.isNotBlank() && ingredientsState.quantity.isNotBlank(),
                leadingIcon = ChefAIIcons.Plus,
            )
        }
    }
}

@Composable
private fun pluralIngredientCount(count: Int): String {
    val context = LocalContext.current
    return context.resources.getQuantityString(R.plurals.ingredient_count, count, count)
}

@Composable
private fun StepsSection(
    stepsState: StepsFields,
    onStepInputChange: (String) -> Unit,
    onAddStep: () -> Unit,
    onRemoveStep: (RecipeStep) -> Unit,
    onReorderStep: (from: Int, to: Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel(stringResource(R.string.section_instructions))

        if (stepsState.steps.isNotEmpty()) {
            SectionRule()
            DragReorderColumn(
                items = stepsState.steps,
                itemKey = { it.uuid },
                onMove = onReorderStep,
            ) { step, index, dragHandle ->
                Column {
                    if (index > 0) RowRule()
                    StepCard(
                        stepNumber = index + 1,
                        step = step,
                        dragHandle = dragHandle,
                        onDelete = { onRemoveStep(step) },
                    )
                }
            }
            SectionRule()
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlatField(
                value = stepsState.input,
                onValueChange = onStepInputChange,
                label = stringResource(R.string.label_add_step),
                placeholder = stringResource(R.string.placeholder_add_step),
                minLines = 2,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAddStep() }),
            )
            FlatBlockButton(
                text = stringResource(R.string.label_add_step),
                onClick = onAddStep,
                variant = FlatButtonVariant.Secondary,
                enabled = stepsState.input.isNotBlank(),
                leadingIcon = ChefAIIcons.Plus,
            )
        }
    }
}

@Composable
private fun TagsSection(
    tags: TagsFields,
    onTagInputChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (Tag) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel(stringResource(R.string.section_tags))
        RemovableTagRow(
            items = tags.selectedTags,
            displayText = { it.displayName },
            tone = FlatTagTone.Accent,
            onRemove = onRemoveTag,
            addLabel = stringResource(R.string.label_add_tags_chip),
        )
        TagAutocomplete(
            value = tags.input,
            onValueChange = onTagInputChange,
            suggestions = tags.suggestions,
            onSuggestionClick = onAddTag,
            onEnterPressed = { onAddTag(tags.input) },
            label = stringResource(R.string.label_add_tags),
            chipLabel = stringResource(R.string.label_add_tags_chip),
            placeholder = stringResource(R.string.placeholder_add_tags),
        )
    }
}

@Composable
private fun LabelsSection(
    labels: LabelsFields,
    onLabelInputChange: (String) -> Unit,
    onAddLabel: (String) -> Unit,
    onRemoveLabel: (Label) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel(stringResource(R.string.section_labels))
        RemovableTagRow(
            items = labels.selectedLabels,
            displayText = { it.displayName },
            tone = FlatTagTone.Neutral,
            onRemove = onRemoveLabel,
            addLabel = stringResource(R.string.label_add_labels_chip),
        )
        TagAutocomplete(
            value = labels.input,
            onValueChange = onLabelInputChange,
            suggestions = labels.suggestions,
            onSuggestionClick = onAddLabel,
            onEnterPressed = { onAddLabel(labels.input) },
            label = stringResource(R.string.label_add_labels),
            chipLabel = stringResource(R.string.label_add_labels_chip),
            placeholder = stringResource(R.string.placeholder_add_labels),
        )
    }
}

@Composable
private fun TagAutocomplete(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    onEnterPressed: () -> Unit,
    label: String,
    chipLabel: String,
    placeholder: String,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        if (expanded) {
            AutocompleteInput(
                value = value,
                onValueChange = onValueChange,
                suggestions = suggestions,
                onSuggestionClick = {
                    onSuggestionClick(it)
                    expanded = false
                },
                onEnterPressed = {
                    onEnterPressed()
                    expanded = false
                },
                label = label,
                placeholder = placeholder,
            )
        } else {
            FlatChip(
                label = chipLabel,
                selected = false,
                onClick = { expanded = true },
            )
        }
    }
}

/**
 * The saved-item chips: each as a non-interactive [FlatTag] — "it does not take an `onClick`, and
 * that is the point" — paired with a small, separate trash affordance so removing one is still
 * possible without making the tag itself a [FlatChip].
 */
@Composable
private fun <T> RemovableTagRow(
    items: List<T>,
    displayText: (T) -> String,
    tone: FlatTagTone,
    onRemove: (T) -> Unit,
    addLabel: String,
) {
    if (items.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlatTag(text = displayText(item), tone = tone)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .flatClickable(onClick = { onRemove(item) }, role = Role.Button),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(ChefAIIcons.X),
                        contentDescription = stringResource(R.string.content_description_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveFooter(
    enabled: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionRule()
        FlatBlockButton(
            text = stringResource(R.string.button_save_recipe),
            onClick = onSave,
            enabled = enabled,
            loading = isSaving,
            modifier = Modifier.padding(16.dp),
        )
    }
}
