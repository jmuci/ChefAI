package com.tenmilelabs.chefai.recipes.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.recipes.domain.model.EditorMode
import com.tenmilelabs.chefai.recipes.ui.editor.components.DeleteConfirmationDialog
import com.tenmilelabs.chefai.recipes.ui.editor.components.UnsavedChangesDialog
import com.tenmilelabs.chefai.recipes.ui.editor.components.AutocompleteInput
import com.tenmilelabs.chefai.recipes.ui.editor.components.ImageUploadContent
import com.tenmilelabs.chefai.recipes.ui.editor.components.IngredientInput
import com.tenmilelabs.chefai.recipes.ui.editor.components.StepCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: RecipeEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showUnsavedDialog by remember { mutableStateOf(false) }

    // Handle one-shot effects
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

    // Back navigation with dirty check
    BackHandler(enabled = true) {
        if (state.isDirty) showUnsavedDialog = true
        else onNavigateBack()
    }

    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            onDiscard = onNavigateBack,
            onKeepEditing = { showUnsavedDialog = false },
        )
    }

    if (state.showDeleteConfirmation) {
        DeleteConfirmationDialog(
            onConfirm = { viewModel.dispatch(EditorAction.ConfirmDelete) },
            onDismiss = { viewModel.dispatch(EditorAction.DismissDeleteDialog) },
        )
    }

    // Image picker launcher — must be at composable scope
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.dispatch(EditorAction.ImageSelected(uri?.toString()))
    }

    // Check for save errors
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
    } else {
        Column {
            EditorActionBar(
                mode = state.mode,
                onSave = {
                    focusManager.clearFocus()
                    viewModel.dispatch(EditorAction.Save)
                },
                saveEnabled = state.isFormValid && !state.isSaving,
                isSaving = state.isSaving,
                onDelete = if (state.mode is EditorMode.Edit) {
                    { viewModel.dispatch(EditorAction.Delete) }
                } else {
                    null
                },
                isDeleting = state.isDeleting,
            )

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                CoreRecipeForm(
                    recipeState = state.recipeFields,
                    onTitleChange = { viewModel.dispatch(EditorAction.TitleChanged(it)) },
                    onDescriptionChange = { viewModel.dispatch(EditorAction.DescriptionChanged(it)) },
                    onPrepTimeChange = { viewModel.dispatch(EditorAction.PrepTimeChanged(it)) },
                    onCookTimeChange = { viewModel.dispatch(EditorAction.CookTimeChanged(it)) },
                    onServingsChange = { viewModel.dispatch(EditorAction.ServingsChanged(it)) },
                    onExternalUrlChange = { viewModel.dispatch(EditorAction.ExternalUrlChanged(it)) },
                )

                ImageUploadSection(
                    recipeState = state.recipeFields,
                    onImageUrlChange = { viewModel.dispatch(EditorAction.ImageUrlChanged(it)) },
                    onSelectImage = { imagePickerLauncher.launch("image/*") },
                    onClearImage = { viewModel.dispatch(EditorAction.ClearImage) },
                )

                IngredientsForm(
                    ingredientsState = state.ingredients,
                    onIngredientInputChange = { viewModel.dispatch(EditorAction.IngredientInputChanged(it)) },
                    onIngredientQuantityChange = { viewModel.dispatch(EditorAction.IngredientQuantityChanged(it)) },
                    onIngredientUnitChange = { viewModel.dispatch(EditorAction.IngredientUnitChanged(it)) },
                    onIngredientSelected = { viewModel.selectIngredient(it) },
                    onRemoveIngredient = { viewModel.dispatch(EditorAction.RemoveIngredient(it)) },
                )

                StepsForm(
                    stepsState = state.steps,
                    onStepInputChange = { viewModel.dispatch(EditorAction.StepInputChanged(it)) },
                    onAddStep = { viewModel.dispatch(EditorAction.AddStep) },
                    onRemoveStep = { viewModel.dispatch(EditorAction.RemoveStep(it)) },
                    onMoveStepUp = { viewModel.dispatch(EditorAction.MoveStepUp(it)) },
                    onMoveStepDown = { viewModel.dispatch(EditorAction.MoveStepDown(it)) },
                )

                TagsForm(
                    tags = state.tags,
                    onTagInputChange = { viewModel.dispatch(EditorAction.TagInputChanged(it)) },
                    onAddTag = { viewModel.addTagByName(it) },
                    onRemoveTag = { viewModel.dispatch(EditorAction.RemoveTag(it)) },
                )

                LabelsForm(
                    labels = state.labels,
                    onLabelInputChange = { viewModel.dispatch(EditorAction.LabelInputChanged(it)) },
                    onAddLabel = { viewModel.addLabelByName(it) },
                    onRemoveLabel = { viewModel.dispatch(EditorAction.RemoveLabel(it)) },
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun EditorActionBar(
    mode: EditorMode,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    isSaving: Boolean,
    onDelete: (() -> Unit)?,
    isDeleting: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(dimensionResource(R.dimen.row_height_medium))
            .padding(horizontal = dimensionResource(R.dimen.padding_small))
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                if (mode is EditorMode.Edit) R.string.edit_recipe_header
                else R.string.create_recipe_header
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    enabled = !isDeleting && !isSaving,
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_recipe_button),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Button(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier.padding(end = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.save_button),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoreRecipeForm(
    recipeState: RecipeFields,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPrepTimeChange: (String) -> Unit,
    onCookTimeChange: (String) -> Unit,
    onServingsChange: (String) -> Unit,
    onExternalUrlChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(title = stringResource(R.string.section_basic_information))

        OutlinedTextField(
            value = recipeState.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.label_recipe_title)) },
            placeholder = { Text(stringResource(R.string.placeholder_recipe_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        OutlinedTextField(
            value = recipeState.description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(R.string.label_description)) },
            placeholder = { Text(stringResource(R.string.placeholder_description)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = recipeState.prepTimeMinutes,
                onValueChange = onPrepTimeChange,
                label = { Text(stringResource(R.string.label_prep_time)) },
                placeholder = { Text(stringResource(R.string.placeholder_minutes)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                singleLine = true,
            )

            OutlinedTextField(
                value = recipeState.cookTimeMinutes,
                onValueChange = onCookTimeChange,
                label = { Text(stringResource(R.string.label_cook_time)) },
                placeholder = { Text(stringResource(R.string.placeholder_minutes)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                singleLine = true,
            )

            OutlinedTextField(
                value = recipeState.servings,
                onValueChange = onServingsChange,
                label = { Text(stringResource(R.string.label_servings)) },
                placeholder = { Text(stringResource(R.string.placeholder_servings)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                singleLine = true,
            )
        }

        OutlinedTextField(
            value = recipeState.externalUrl,
            onValueChange = onExternalUrlChange,
            label = { Text(stringResource(R.string.label_external_url)) },
            placeholder = { Text(stringResource(R.string.placeholder_external_url)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
    }
}

@Composable
private fun ImageUploadSection(
    recipeState: RecipeFields,
    onImageUrlChange: (String) -> Unit,
    onSelectImage: () -> Unit,
    onClearImage: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(title = stringResource(R.string.section_recipe_photo))
        ImageUploadContent(
            selectedImageUri = recipeState.selectedImageUri,
            imageUrl = recipeState.imageUrl,
            onImageUrlChange = onImageUrlChange,
            onSelectImage = onSelectImage,
            onClearImage = onClearImage,
        )
    }
}

@Composable
private fun IngredientsForm(
    ingredientsState: IngredientsFields,
    onIngredientInputChange: (String) -> Unit,
    onIngredientQuantityChange: (String) -> Unit,
    onIngredientUnitChange: (String) -> Unit,
    onIngredientSelected: (String) -> Unit,
    onRemoveIngredient: (RecipeIngredient) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(title = stringResource(R.string.section_ingredients))

        IngredientInput(
            ingredientInput = ingredientsState.input,
            onIngredientInputChange = onIngredientInputChange,
            ingredientQuantity = ingredientsState.quantity,
            onIngredientQuantityChange = onIngredientQuantityChange,
            ingredientUnit = ingredientsState.unit,
            onIngredientUnitChange = onIngredientUnitChange,
            suggestions = ingredientsState.suggestions,
            onSuggestionClick = onIngredientSelected,
            onAddIngredient = {
                if (ingredientsState.input.isNotBlank()) {
                    onIngredientSelected(ingredientsState.input)
                }
            },
        )

        IngredientChips(
            ingredients = ingredientsState.selectedIngredients,
            onRemove = onRemoveIngredient,
        )
    }
}

@Composable
private fun StepsForm(
    stepsState: StepsFields,
    onStepInputChange: (String) -> Unit,
    onAddStep: () -> Unit,
    onRemoveStep: (RecipeStep) -> Unit,
    onMoveStepUp: (RecipeStep) -> Unit,
    onMoveStepDown: (RecipeStep) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(title = stringResource(R.string.section_instructions))

        stepsState.steps.forEachIndexed { index, step ->
            StepCard(
                stepNumber = index + 1,
                step = step,
                onDelete = { onRemoveStep(step) },
                onMoveUp = if (index > 0) {
                    { onMoveStepUp(step) }
                } else null,
                onMoveDown = if (index < stepsState.steps.size - 1) {
                    { onMoveStepDown(step) }
                } else null,
            )
        }

        OutlinedTextField(
            value = stepsState.input,
            onValueChange = onStepInputChange,
            label = { Text(stringResource(R.string.label_add_step)) },
            placeholder = { Text(stringResource(R.string.placeholder_add_step)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            trailingIcon = {
                IconButton(
                    onClick = onAddStep,
                    enabled = stepsState.input.isNotBlank(),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.content_description_add_step),
                    )
                }
            },
            keyboardActions = KeyboardActions(onDone = { onAddStep() }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
    }
}

@Composable
private fun TagsForm(
    tags: TagsFields,
    onTagInputChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (Tag) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(title = stringResource(R.string.section_tags))

        AutocompleteInput(
            value = tags.input,
            onValueChange = onTagInputChange,
            suggestions = tags.suggestions,
            onSuggestionClick = onAddTag,
            onEnterPressed = { onAddTag(tags.input) },
            label = stringResource(R.string.label_add_tags),
            placeholder = stringResource(R.string.placeholder_add_tags),
        )

        ChipGroup(
            items = tags.selectedTags,
            onRemove = onRemoveTag,
            displayText = { it.displayName },
        )
    }
}

@Composable
private fun LabelsForm(
    labels: LabelsFields,
    onLabelInputChange: (String) -> Unit,
    onAddLabel: (String) -> Unit,
    onRemoveLabel: (Label) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(title = stringResource(R.string.section_labels))

        AutocompleteInput(
            value = labels.input,
            onValueChange = onLabelInputChange,
            suggestions = labels.suggestions,
            onSuggestionClick = onAddLabel,
            onEnterPressed = { onAddLabel(labels.input) },
            label = stringResource(R.string.label_add_labels),
            placeholder = stringResource(R.string.placeholder_add_labels),
        )

        ChipGroup(
            items = labels.selectedLabels,
            onRemove = onRemoveLabel,
            displayText = { it.displayName },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientChips(
    ingredients: List<RecipeIngredient>,
    onRemove: (RecipeIngredient) -> Unit,
) {
    if (ingredients.isEmpty()) return

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ingredients.forEach { ingredient ->
            FilterChip(
                selected = false,
                onClick = { },
                label = {
                    Text(
                        stringResource(
                            R.string.ingredient_display_format,
                            ingredient.quantity,
                            ingredient.unit,
                            ingredient.ingredientDisplayName,
                        )
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { onRemove(ingredient) },
                        modifier = Modifier.size(18.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.content_description_remove),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(
    items: List<T>,
    onRemove: (T) -> Unit,
    displayText: (T) -> String,
) {
    if (items.isEmpty()) {
        Text(
            text = stringResource(R.string.no_items_added),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            FilterChip(
                selected = false,
                onClick = { },
                label = { Text(displayText(item)) },
                trailingIcon = {
                    IconButton(
                        onClick = { onRemove(item) },
                        modifier = Modifier.size(18.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.content_description_remove),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )
        }
    }
}
