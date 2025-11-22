package com.tenmilelabs.chefai.recipes.ui.create

import android.net.Uri
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.recipes.ui.create.components.AutocompleteInput
import com.tenmilelabs.chefai.recipes.ui.create.components.ImageUploadContent
import com.tenmilelabs.chefai.recipes.ui.create.components.IngredientInput
import com.tenmilelabs.chefai.recipes.ui.create.components.StepCard
import com.tenmilelabs.chefai.core.ui.preview.SharedData.carbonaraIngredients
import com.tenmilelabs.chefai.core.ui.preview.SharedData.carbonaraSteps
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRecipeScreen(
    onNavigateBack: () -> Unit,
    onRecipeCreated: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: CreateRecipeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.onImageSelected(uri?.toString())
    }

    // Check for user messages to display on the screen
    uiState.saveError?.let { message ->
        val snackBarText = stringResource(R.string.snackbar_save_error)
        LaunchedEffect(snackbarHostState, viewModel, message, snackBarText) {
            snackbarHostState.showSnackbar(
                message = snackBarText,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }
    val focusManager = LocalFocusManager.current

    Column() {
        ActionBar(
            saveButtonOnCLick = {
                focusManager.clearFocus()
                viewModel.saveRecipe(onRecipeCreated)
            },
            saveButtonEnabled = uiState.isFormValid && !uiState.isSaving,
            savingState = uiState.isSaving
        )

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            CoreRecipeForm(
                recipeState = uiState.recipeFields,
                onTitleChange = viewModel::onTitleChange,
                onDescriptionChange = viewModel::onDescriptionChange,
                onPrepTimeChange = viewModel::onPrepTimeChange,
                onCookTimeChange = viewModel::onCookTimeChange,
                onServingsChange = viewModel::onServingsChange,
                onExternalUrlChange = viewModel::onExternalUrlChange
            )

            ImageUploadSection(
                recipeState = uiState.recipeFields,
                onImageUrlChange = viewModel::onImageUrlChange,
                onSelectImage = { imagePickerLauncher.launch("image/*") },
                onClearImage = viewModel::clearSelectedImage
            )

            IngredientsForm(
                ingredientsState = uiState.ingredients,
                onIngredientInputChange = viewModel::onIngredientInputChange,
                onIngredientQuantityChange = viewModel::onIngredientQuantityChange,
                onIngredientUnitChange = viewModel::onIngredientUnitChange,
                onIngredientSelected = viewModel::onIngredientSelected,
                onRemoveIngredient = viewModel::removeIngredient
            )

            StepsForm(
                stepsState = uiState.steps,
                onStepInputChange = viewModel::onStepInputChange,
                onAddStep = viewModel::addStep,
                onRemoveStep = viewModel::removeStep,
                onMoveStepUp = viewModel::moveStepUp,
                onMoveStepDown = viewModel::moveStepDown
            )

            TagsForm(
                tags = uiState.tags,
                onTagInputChange = viewModel::onTagInputChange,
                onAddTag = viewModel::addTag,
                onRemoveTag = viewModel::removeTag
            )

            LabelsForm(
                labels = uiState.labels,
                onLabelInputChange = viewModel::onLabelInputChange,
                onAddLabel = viewModel::addLabel,
                onRemoveLabel = viewModel::removeLabel
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionBar(
    modifier: Modifier = Modifier,
    saveButtonOnCLick: () -> Unit = {},
    saveButtonEnabled: Boolean = false,
    savingState: Boolean = false
) {
        Row(
            modifier = modifier
                .height(dimensionResource(R.dimen.row_height_medium))
                .padding(horizontal = dimensionResource(R.dimen.padding_small))
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(id = R.string.create_recipe_header),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { saveButtonOnCLick() },
                enabled = saveButtonEnabled,
                modifier = Modifier.padding(end = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (savingState) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringResource(id = R.string.save_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
}

@Composable
private fun CoreRecipeForm(
    recipeState: RecipeFields,
    onTitleChange: (String) -> Unit = {},
    onDescriptionChange: (String) -> Unit = {},
    onPrepTimeChange: (String) -> Unit = {},
    onCookTimeChange: (String) -> Unit = {},
    onServingsChange: (String) -> Unit = {},
    onExternalUrlChange: (String) -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Basic Information Section
        SectionHeader(title = stringResource(R.string.section_basic_information))

        OutlinedTextField(
            value = recipeState.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.label_recipe_title)) },
            placeholder = { Text(stringResource(R.string.placeholder_recipe_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        OutlinedTextField(
            value = recipeState.description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(R.string.label_description)) },
            placeholder = { Text(stringResource(R.string.placeholder_description)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = recipeState.prepTimeMinutes,
                onValueChange = onPrepTimeChange,
                label = { Text(stringResource(R.string.label_prep_time)) },
                placeholder = { Text(stringResource(R.string.placeholder_minutes)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )

            OutlinedTextField(
                value = recipeState.cookTimeMinutes,
                onValueChange = onCookTimeChange,
                label = { Text(stringResource(R.string.label_cook_time)) },
                placeholder = { Text(stringResource(R.string.placeholder_minutes)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )

            OutlinedTextField(
                value = recipeState.servings,
                onValueChange = onServingsChange,
                label = { Text(stringResource(R.string.label_servings)) },
                placeholder = { Text(stringResource(R.string.placeholder_servings)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )
        }

        OutlinedTextField(
            value = recipeState.externalUrl,
            onValueChange = onExternalUrlChange,
            label = { Text(stringResource(R.string.label_external_url)) },
            placeholder = { Text(stringResource(R.string.placeholder_external_url)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
    }
}

@Composable
private fun ImageUploadSection(
    recipeState: RecipeFields,
    onImageUrlChange: (String) -> Unit = {},
    onSelectImage: () -> Unit = {},
    onClearImage: () -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Image Section
        SectionHeader(title = stringResource(R.string.section_recipe_photo))

        ImageUploadContent(
            selectedImageUri = recipeState.selectedImageUri,
            imageUrl = recipeState.imageUrl,
            onImageUrlChange = onImageUrlChange,
            onSelectImage = onSelectImage,
            onClearImage = onClearImage
        )
    }
}

@Composable
private fun IngredientsForm(
    ingredientsState: IngredientsFields,
    onIngredientInputChange: (String) -> Unit = {},
    onIngredientQuantityChange: (String) -> Unit = {},
    onIngredientUnitChange: (String) -> Unit = {},
    onIngredientSelected: (String) -> Unit = {},
    onRemoveIngredient: (RecipeIngredient) -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Ingredients Section
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
            }
        )

        IngredientChips(
            ingredients = ingredientsState.selectedIngredients,
            onRemove = onRemoveIngredient
        )
    }
}

@Composable
private fun StepsForm(
    stepsState: StepsFields,
    onStepInputChange: (String) -> Unit = {},
    onAddStep: () -> Unit = {},
    onRemoveStep: (RecipeStep) -> Unit = {},
    onMoveStepUp: (RecipeStep) -> Unit = {},
    onMoveStepDown: (RecipeStep) -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Steps Section
        SectionHeader(title = stringResource(R.string.section_instructions))

        // Dynamic list of steps
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
                } else null
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
                    enabled = stepsState.input.isNotBlank()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.content_description_add_step)
                    )
                }
            },
            keyboardActions = KeyboardActions(
                onDone = { onAddStep() }
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
    }
}

@Composable
private fun TagsForm(
    tags: TagsFields,
    onTagInputChange: (String) -> Unit = {},
    onAddTag: (String) -> Unit = {},
    onRemoveTag: (Tag) -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tags Section
        SectionHeader(title = stringResource(R.string.section_tags))

        AutocompleteInput(
            value = tags.input,
            onValueChange = onTagInputChange,
            suggestions = tags.suggestions,
            onSuggestionClick = onAddTag,
            onEnterPressed = { onAddTag(tags.input) },
            label = stringResource(R.string.label_add_tags),
            placeholder = stringResource(R.string.placeholder_add_tags)
        )

        ChipGroup(
            items = tags.selectedTags,
            onRemove = onRemoveTag,
            displayText = { it.displayName }
        )
    }
}

@Composable
private fun LabelsForm(
    labels: LabelsFields,
    onLabelInputChange: (String) -> Unit = {},
    onAddLabel: (String) -> Unit = {},
    onRemoveLabel: (Label) -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Labels Section
        SectionHeader(title = stringResource(R.string.section_labels))

        AutocompleteInput(
            value = labels.input,
            onValueChange = onLabelInputChange,
            suggestions = labels.suggestions,
            onSuggestionClick = onAddLabel,
            onEnterPressed = { onAddLabel(labels.input) },
            label = stringResource(R.string.label_add_labels),
            placeholder = stringResource(R.string.placeholder_add_labels)
        )

        ChipGroup(
            items = labels.selectedLabels,
            onRemove = onRemoveLabel,
            displayText = { it.displayName }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary
    )
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientChips(
    ingredients: List<RecipeIngredient>,
    onRemove: (RecipeIngredient) -> Unit = {}
) {
    if (ingredients.isEmpty()) return

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            ingredient.ingredientDisplayName
                        )
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { onRemove(ingredient) },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.content_description_remove),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(
    items: List<T>,
    onRemove: (T) -> Unit = {},
    displayText: (T) -> String
) {
    if (items.isEmpty()) {
        Text(
            text = stringResource(R.string.no_items_added),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            FilterChip(
                selected = false,
                onClick = { },
                label = { Text(displayText(item)) },
                trailingIcon = {
                    IconButton(
                        onClick = { onRemove(item) },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.content_description_remove),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CoreRecipeFormPreview() {
    ChefAITheme {
        CoreRecipeForm(
            recipeState = RecipeFields(
                title = "Delicious Pancakes",
                description = "Fluffy and delicious pancakes, perfect for breakfast.",
                prepTimeMinutes = "10",
                cookTimeMinutes = "15",
                servings = "4",
                externalUrl = "http://example.com"
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun IngredientsFormPreview() {
    ChefAITheme {
        IngredientsForm(
            ingredientsState = IngredientsFields(
                input = "Salt",
                quantity = "1",
                unit = "tsp",
                selectedIngredients = carbonaraIngredients,
                suggestions = listOf("Salt", "Sea Salt", "Kosher Salt")
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StepsFormPreview() {
    ChefAITheme {
        StepsForm(
            stepsState = StepsFields(
                input = "New step",
                steps = carbonaraSteps
            )
        )
    }
}


@Preview(showBackground = true)
@Composable
fun ActionBarWithSaveButtonDisabledPreview() {
    ChefAITheme {
        ActionBar()
    }
}

@Preview(showBackground = true)
@Composable
fun ActionBarWithSaveButtonSavingPreview() {
    ChefAITheme {
        ActionBar(savingState = true)
    }
}
