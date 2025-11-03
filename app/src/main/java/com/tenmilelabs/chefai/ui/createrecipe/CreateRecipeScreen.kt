package com.tenmilelabs.chefai.ui.createrecipe

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.data.source.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.domain.model.RecipeStep
import com.tenmilelabs.chefai.domain.model.User
import com.tenmilelabs.chefai.ui.theme.ChefAITheme
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRecipeScreen(
    onNavigateBack: () -> Unit,
    onRecipeCreated: () -> Unit,
    viewModel: CreateRecipeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.onImageSelected(uri?.toString())
    }

    // Show error snackbar
    LaunchedEffect(uiState.saveError) {
        uiState.saveError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    Column(Modifier.fillMaxSize()) {
        ActionBar(
            saveButtonOnCLick = {
                // TODO: Get current user from auth/user repository
                val mockUser = User(
                    uuid = UUID.randomUUID(),
                    displayName = "Current User",
                    email = "user@example.com",
                    avatarUrl = null
                )
                viewModel.saveRecipe(mockUser, onRecipeCreated)
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

            CoreRecipeForm(uiState.recipeFields, viewModel)
            ImageUploadSection(uiState.recipeFields, viewModel, imagePickerLauncher)
            IngredientsForm(uiState.ingredients, viewModel)
            StepsForm(uiState.steps, viewModel)
            TagsForm(uiState.tags, viewModel)
            LabelsForm(uiState.labels, viewModel)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionBar(
    saveButtonOnCLick: () -> Unit = {},
    saveButtonEnabled: Boolean = false,
    savingState: Boolean = false
) {
    Row(
        modifier = Modifier
            .height(dimensionResource(R.dimen.row_height_medium))
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(id = R.string.create_recipe_header),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
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
    viewModel: CreateRecipeViewModel,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Basic Information Section
        SectionHeader(title = "Basic Information")

        OutlinedTextField(
            value = recipeState.title,
            onValueChange = viewModel::onTitleChange,
            label = { Text("Recipe Title *") },
            placeholder = { Text("E.g., Chocolate Chip Cookies") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        OutlinedTextField(
            value = recipeState.description,
            onValueChange = viewModel::onDescriptionChange,
            label = { Text("Description *") },
            placeholder = { Text("Describe your recipe...") },
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
                onValueChange = viewModel::onPrepTimeChange,
                label = { Text("Prep Time *") },
                placeholder = { Text("Minutes") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )

            OutlinedTextField(
                value = recipeState.cookTimeMinutes,
                onValueChange = viewModel::onCookTimeChange,
                label = { Text("Cook Time *") },
                placeholder = { Text("Minutes") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )

            OutlinedTextField(
                value = recipeState.servings,
                onValueChange = viewModel::onServingsChange,
                label = { Text("Servings *") },
                placeholder = { Text("4") },
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
            onValueChange = viewModel::onExternalUrlChange,
            label = { Text("External Recipe URL") },
            placeholder = { Text("https://...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
    }
}

@Composable
private fun ImageUploadSection(
    recipeState: RecipeFields,
    viewModel: CreateRecipeViewModel,
    imagePickerLauncher: ManagedActivityResultLauncher<String, Uri?>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Image Section
        SectionHeader(title = "Recipe Photo")

        ImageUploadContent(
            selectedImageUri = recipeState.selectedImageUri,
            imageUrl = recipeState.imageUrl,
            onImageUrlChange = viewModel::onImageUrlChange,
            onSelectImage = { imagePickerLauncher.launch("image/*") },
            onClearImage = viewModel::clearSelectedImage
        )
    }
}

@Composable
private fun IngredientsForm(
    ingredientsState: IngredientsFields,
    viewModel: CreateRecipeViewModel,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Ingredients Section
        SectionHeader(title = "Ingredients *")

        IngredientInput(
            ingredientInput = ingredientsState.input,
            onIngredientInputChange = viewModel::onIngredientInputChange,
            ingredientQuantity = ingredientsState.quantity,
            onIngredientQuantityChange = viewModel::onIngredientQuantityChange,
            ingredientUnit = ingredientsState.unit,
            onIngredientUnitChange = viewModel::onIngredientUnitChange,
            suggestions = ingredientsState.suggestions,
            onSuggestionClick = viewModel::onIngredientSelected,
            onAddIngredient = {
                if (ingredientsState.input.isNotBlank()) {
                    viewModel.onIngredientSelected(ingredientsState.input)
                }
            }
        )

        IngredientChips(
            ingredients = ingredientsState.selectedIngredients,
            onRemove = viewModel::removeIngredient
        )
    }
}

@Composable
private fun StepsForm(
    stepsState: StepsFields,
    viewModel: CreateRecipeViewModel,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Steps Section
        SectionHeader(title = "Instructions *")

        // Dynamic list of steps
        stepsState.steps.forEachIndexed { index, step ->
            StepCard(
                stepNumber = index + 1,
                step = step,
                onDelete = { viewModel.removeStep(step) },
                onMoveUp = if (index > 0) {
                    { viewModel.moveStepUp(step) }
                } else null,
                onMoveDown = if (index < stepsState.steps.size - 1) {
                    { viewModel.moveStepDown(step) }
                } else null
            )
        }

        OutlinedTextField(
            value = stepsState.input,
            onValueChange = viewModel::onStepInputChange,
            label = { Text("Add Step") },
            placeholder = { Text("Describe the next step...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            trailingIcon = {
                IconButton(
                    onClick = viewModel::addStep,
                    enabled = stepsState.input.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add step")
                }
            },
            keyboardActions = KeyboardActions(
                onDone = { viewModel.addStep() }
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
    }
}

@Composable
private fun TagsForm(
    tags: TagsFields,
    viewModel: CreateRecipeViewModel,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tags Section
        SectionHeader(title = "Tags")

        AutocompleteInput(
            value = tags.input,
            onValueChange = viewModel::onTagInputChange,
            suggestions = tags.suggestions,
            onSuggestionClick = viewModel::addTag,
            onEnterPressed = { viewModel.addTag() },
            label = "Add Tags",
            placeholder = "Type and press Enter..."
        )

        ChipGroup(
            items = tags.selectedTags,
            onRemove = viewModel::removeTag,
            displayText = { it.displayName }
        )
    }
}

@Composable
private fun LabelsForm(
    labels: LabelsFields,
    viewModel: CreateRecipeViewModel,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Labels Section
        SectionHeader(title = "Labels")

        AutocompleteInput(
            value = labels.input,
            onValueChange = viewModel::onLabelInputChange,
            suggestions = labels.suggestions,
            onSuggestionClick = viewModel::addLabel,
            onEnterPressed = { viewModel.addLabel() },
            label = "Add Labels",
            placeholder = "Type and press Enter..."
        )

        ChipGroup(
            items = labels.selectedLabels,
            onRemove = viewModel::removeLabel,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientInput(
    ingredientInput: String,
    onIngredientInputChange: (String) -> Unit,
    ingredientQuantity: String,
    onIngredientQuantityChange: (String) -> Unit,
    ingredientUnit: String,
    onIngredientUnitChange: (String) -> Unit,
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    onAddIngredient: () -> Unit
) {
    var expandedSuggestions by rememberSaveable { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = ingredientQuantity,
                onValueChange = onIngredientQuantityChange,
                label = { Text("Qty") },
                modifier = Modifier.weight(0.3f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )

            OutlinedTextField(
                value = ingredientUnit,
                onValueChange = onIngredientUnitChange,
                label = { Text("Unit") },
                placeholder = { Text("cup, tsp...") },
                modifier = Modifier.weight(0.35f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true
            )

            ExposedDropdownMenuBox(
                expanded = expandedSuggestions && suggestions.isNotEmpty(),
                onExpandedChange = { expandedSuggestions = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = ingredientInput,
                    onValueChange = {
                        onIngredientInputChange(it)
                        expandedSuggestions = true
                    },
                    label = { Text("Ingredient") },
                    placeholder = { Text("Search...") },
                    modifier = Modifier
                        .menuAnchor(
                            type = androidx.compose.material3.MenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                        .fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = onAddIngredient,
                            enabled = ingredientInput.isNotBlank() && ingredientQuantity.isNotBlank()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add ingredient")
                        }
                    },
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (ingredientInput.isNotBlank() && ingredientQuantity.isNotBlank()) {
                                onAddIngredient()
                            }
                        }
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = true
                )

                ExposedDropdownMenu(
                    expanded = expandedSuggestions && suggestions.isNotEmpty(),
                    onDismissRequest = { expandedSuggestions = false }
                ) {
                    suggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = {
                                onSuggestionClick(suggestion)
                                expandedSuggestions = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientChips(
    ingredients: List<RecipeIngredient>,
    onRemove: (RecipeIngredient) -> Unit
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
                    Text("${ingredient.quantity} ${ingredient.unit} ${ingredient.ingredientDisplayName}")
                },
                trailingIcon = {
                    IconButton(
                        onClick = { onRemove(ingredient) },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: Int,
    step: RecipeStep,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "$stepNumber.",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 8.dp)
            )

            Text(
                text = step.instruction,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )

            Column {
                onMoveUp?.let {
                    IconButton(
                        onClick = it,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Move up",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                onMoveDown?.let {
                    IconButton(
                        onClick = it,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = "Move down",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete step",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutocompleteInput(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    onEnterPressed: () -> Unit,
    label: String,
    placeholder: String
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            modifier = Modifier
                .menuAnchor(
                    type = androidx.compose.material3.MenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
                .fillMaxWidth(),
            keyboardActions = KeyboardActions(
                onDone = {
                    onEnterPressed()
                    expanded = false
                }
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onSuggestionClick(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(
    items: List<T>,
    onRemove: (T) -> Unit,
    displayText: (T) -> String
) {
    if (items.isEmpty()) {
        Text(
            text = "No items added yet",
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
                            contentDescription = "Remove",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun ImageUploadContent(
    selectedImageUri: String?,
    imageUrl: String,
    onImageUrlChange: (String) -> Unit,
    onSelectImage: () -> Unit,
    onClearImage: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Image Preview
        if (selectedImageUri != null || imageUrl.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Display selected image or URL
                    if (selectedImageUri != null) {
                        // For local URI, we'd use AsyncImage with Coil
                        // Since Coil is not available, show a placeholder
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Image selected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Note: Image upload needs backend implementation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (imageUrl.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Image URL set",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Remove button
                    IconButton(
                        onClick = {
                            onClearImage()
                            onImageUrlChange("")
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove image",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Upload/Select Image Button
        Button(
            onClick = onSelectImage,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors()
        ) {
            Icon(
                Icons.Default.AddAPhoto,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Photo from Gallery")
        }

        // Divider text
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .padding(horizontal = 8.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(1.dp)
                    )
            )
            Text(
                "or",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .padding(horizontal = 8.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }

        // Image URL TextField
        OutlinedTextField(
            value = imageUrl,
            onValueChange = onImageUrlChange,
            label = { Text("Image URL") },
            placeholder = { Text("https://example.com/image.jpg") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            leadingIcon = {
                Icon(Icons.Default.Image, contentDescription = null)
            }
        )
    }
}


@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ActionBarWithSaveButtonDisabledPreview() {
    ChefAITheme {
        ActionBar()
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ActionBarWithSaveButtonSavingPreview() {
    ChefAITheme {
        ActionBar(savingState = true)
    }
}
