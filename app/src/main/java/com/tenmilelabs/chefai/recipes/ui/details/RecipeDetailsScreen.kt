package com.tenmilelabs.chefai.recipes.ui.details

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.units.IngredientAmountFormatter
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import com.tenmilelabs.chefai.core.ui.components.CookedToggleButton
import com.tenmilelabs.chefai.core.ui.components.InfoChip
import com.tenmilelabs.chefai.core.ui.components.InfoChipType
import com.tenmilelabs.chefai.core.ui.components.RecipePrivacyBadge
import com.tenmilelabs.chefai.core.ui.components.NutritionRow
import com.tenmilelabs.chefai.core.ui.components.RecipeTimeRow
import com.tenmilelabs.chefai.core.ui.preview.RecipeData
import com.tenmilelabs.chefai.core.ui.recipeImageModel
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.timer.RecipeTimerViewModel
import com.tenmilelabs.chefai.core.ui.timer.rememberNotificationPermissionRequester
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.core.util.parseStepDurationSeconds
import com.tenmilelabs.chefai.recipes.domain.scaling.RecipeScaling
import com.tenmilelabs.chefai.recipes.ui.components.DeleteConfirmationDialog
import com.tenmilelabs.chefai.recipes.ui.details.components.ServingsStepper
import timber.log.Timber


@Composable
fun RecipeDetailsScreen(
    viewModel: RecipeDetailsViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
    onEditClick: ((java.util.UUID) -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val recipeDeletedText = stringResource(R.string.recipe_deleted)
    LaunchedEffect(viewModel, snackbarHostState, onNavigateBack, recipeDeletedText) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RecipeDetailsEffect.RecipeDeleted -> {
                    snackbarHostState.showSnackbar(
                        message = recipeDeletedText,
                        duration = SnackbarDuration.Short,
                    )
                    onNavigateBack?.invoke()
                }
            }
        }
    }

    if (uiState.isLoading) {
        LoadingContent()
    } else {
        val recipe = uiState.recipe
        if (recipe != null) {
            RecipeDetailsContent(
                recipe = recipe,
                onAction = { action ->
                    when (action) {
                        RecipeDetailsAction.EditClicked -> onEditClick?.invoke(viewModel.recipeUuid)
                        RecipeDetailsAction.ToggleBookmark -> viewModel.toggleBookmark()
                        RecipeDetailsAction.DeleteClicked -> viewModel.onDeleteClick()
                        RecipeDetailsAction.ConfirmDelete -> viewModel.confirmDelete()
                        RecipeDetailsAction.DismissDeleteDialog -> viewModel.dismissDeleteDialog()
                        RecipeDetailsAction.ToggleCooked -> viewModel.onToggleCooked()
                        is RecipeDetailsAction.ServingsChanged ->
                            viewModel.onServingsChange(action.servings)
                    }
                },
                servings = uiState.servings,
                isBookmarked = uiState.isBookmarked,
                canEdit = onEditClick != null,
                // Deleting navigates away, so the button is only offered when there is somewhere to
                // go — and even then, only for a recipe the user owns (uiState.canDelete).
                canDelete = onNavigateBack != null && uiState.canDelete,
                showDeleteConfirmation = uiState.showDeleteConfirmation,
                isDeleting = uiState.isDeleting,
                measurementSystem = uiState.measurementSystem,
                showCookedToggle = uiState.showCookedToggle,
                isCooked = uiState.isCooked,
            )
        } else {
            EmptyContent(
                title = R.string.recipe_not_found_error,
                subtitle = R.string.recipe_not_found_error_subtitle,
                noRecipesIconRes = R.drawable.ic_chef_hat_black_24dp
            )
            Timber.e("Recipe Not Found Loading error!")
        }
    }

    // Check for user messages to display on the screen
    uiState.userMessage?.let { message ->
        val snackbarText = stringResource(message)
        LaunchedEffect(snackbarHostState, viewModel, message, snackbarText) {
            snackbarHostState.showSnackbar(
                message = snackbarText,
                duration = SnackbarDuration.Short
            )
            viewModel.snackbarMessageShown()
        }
    }
}

/**
 * The recipe details screen, stateless.
 *
 * @param servings the portions the ingredient list is shown at; the quantities are scaled from
 *   [recipe] to match, here rather than by the caller, so the stepper and the list can never
 *   disagree about what they are showing.
 * @param measurementSystem the units the ingredient list is read in. Applied here, after scaling,
 *   for the same reason scaling itself is: it is a way of reading the recipe, never an edit to it.
 * @param canEdit whether the caller has somewhere for the edit FAB to go.
 * @param canDelete whether the caller has somewhere to navigate after a delete, and the current
 *   user owns [recipe] (created or imported it themselves).
 */
@Composable
fun RecipeDetailsContent(
    recipe: Recipe,
    onAction: (RecipeDetailsAction) -> Unit = {},
    servings: ServingsUiState = ServingsUiState.forRecipeServings(recipe.servings),
    measurementSystem: MeasurementSystem = MeasurementSystem.DEFAULT,
    isBookmarked: Boolean = false,
    canEdit: Boolean = false,
    canDelete: Boolean = false,
    showDeleteConfirmation: Boolean = false,
    isDeleting: Boolean = false,
    showCookedToggle: Boolean = false,
    isCooked: Boolean = false,
) {
    if (showDeleteConfirmation) {
        DeleteConfirmationDialog(
            onConfirm = { onAction(RecipeDetailsAction.ConfirmDelete) },
            onDismiss = { onAction(RecipeDetailsAction.DismissDeleteDialog) },
        )
    }

    // Scale first and convert second, never the other way round: scaling arithmetic run on an
    // already-converted, already-rounded value compounds the rounding.
    val ingredients = remember(
        recipe.ingredients, servings.base, servings.current, measurementSystem,
    ) {
        RecipeScaling.scale(
            ingredients = recipe.ingredients,
            baseServings = servings.base,
            targetServings = servings.current,
        ).map { ingredient ->
            val amount = IngredientAmountFormatter.format(
                quantity = ingredient.quantity,
                unit = ingredient.unit,
                ingredientName = ingredient.ingredientDisplayName,
                system = measurementSystem,
            )
            IngredientRowUi(
                name = ingredient.ingredientDisplayName,
                amountLabel = amount.text,
                isApproximate = amount.isApproximate,
            )
        }
    }

    val tabTitles = listOf(stringResource(R.string.ingredients), stringResource(R.string.steps))
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.padding_medium))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dimensionResource(id = R.dimen.padding_medium)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                if (showCookedToggle) {
                    CookedToggleButton(
                        isCooked = isCooked,
                        onToggle = { onAction(RecipeDetailsAction.ToggleCooked) },
                        modifier = Modifier.padding(end = dimensionResource(id = R.dimen.padding_extra_small)),
                    )
                }
                IconButton(onClick = { onAction(RecipeDetailsAction.ToggleBookmark) }) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(
                            if (isBookmarked) R.string.remove_from_collection_content_description
                            else R.string.save_to_collection_content_description
                        ),
                        tint = if (isBookmarked) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canDelete) {
                    IconButton(
                        onClick = { onAction(RecipeDetailsAction.DeleteClicked) },
                        enabled = !isDeleting,
                        modifier = Modifier.testTag("DeleteRecipeButton"),
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_recipe_button),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            AsyncImage(
                model = recipeImageModel(recipe.localImagePath, recipe.imageUrl),
                placeholder = painterResource(R.drawable.ic_img_placeholder),
                error = painterResource(R.drawable.ic_img_error),
                contentDescription = stringResource(R.string.recipe_image_content_description),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier
                    .padding(vertical = dimensionResource(id = R.dimen.padding_small))
                    .height(200.dp)
                    .fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_small)))
            RecipeTimeRow(recipe.prepTimeMinutes, recipe.cookTimeMinutes)
            NutritionRow(recipe.caloriesPerServing, recipe.proteinGramsPerServing)
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_small)))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_extra_small)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_extra_small))
            ) {
                RecipePrivacyBadge(privacy = recipe.privacy)
                recipe.labels.forEach { label ->
                    InfoChip(text = label.displayName, type = InfoChipType.LABEL)
                }
                recipe.tags.forEach { tag ->
                    InfoChip(text = tag.displayName, type = InfoChipType.TAG)
                }
            }
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_medium)))
            if (!recipe.description.isEmpty()) {
                RecipeDescription(recipe.description)
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_medium)))
            }

            ServingsStepper(
                servings = servings.current,
                range = servings.range,
                onServingsChange = { onAction(RecipeDetailsAction.ServingsChanged(it)) },
                isEstimated = servings.isEstimated,
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_medium)))
        }

        TabRow(selectedTabIndex = selectedTabIndex) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(text = title) }
                )
            }
        }

        // Plain content, not its own scrollable — it's part of the single scroll on the outer
        // Column above so the whole screen (header + tab content) scrolls as one, rather than the
        // tab section being squeezed into whatever space is left and scrolling independently.
        when (selectedTabIndex) {
            0 -> IngredientsList(ingredients = ingredients)
            1 -> StepsList(steps = recipe.steps)
        }
    }

    if (canEdit) {
        FloatingActionButton(
            onClick = { onAction(RecipeDetailsAction.EditClicked) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.edit_button),
            )
        }
    }
    } // Box
}

private const val DESCRIPTION_COLLAPSED_MAX_LINES = 4

@Composable
private fun RecipeDescription(description: String) {
    var isExpanded by rememberSaveable(description) { mutableStateOf(false) }
    var isOverflowing by remember(description) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (isExpanded) Int.MAX_VALUE else DESCRIPTION_COLLAPSED_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!isExpanded) isOverflowing = result.hasVisualOverflow
            },
        )
        if (isOverflowing || isExpanded) {
            Text(
                text = stringResource(if (isExpanded) R.string.view_less else R.string.view_more),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(top = dimensionResource(id = R.dimen.padding_extra_small))
                    .clickable { isExpanded = !isExpanded },
            )
        }
    }
}

/** One ingredient row, already scaled, converted and rendered. */
data class IngredientRowUi(
    val name: String,
    val amountLabel: String,
    /** True when the amount rests on an assumed density; the row prefixes it with "≈". */
    val isApproximate: Boolean = false,
)

@Composable
fun IngredientsList(ingredients: List<IngredientRowUi>) {
    if (ingredients.isEmpty()) {
        Text(
            text = stringResource(R.string.no_ingredients_listed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(dimensionResource(id = R.dimen.padding_medium)),
        )
        return
    }
    Column(modifier = Modifier.padding(dimensionResource(id = R.dimen.padding_medium))) {
        ingredients.forEach { ingredient ->
            Row {
                Text(
                    text = ingredient.name,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = dimensionResource(id = R.dimen.padding_small)),
                    style = MaterialTheme.typography.bodyLarge
                )
                // "≈" marks a weight arrived at through a typical density rather than measured.
                // A screen reader gets the word rather than the glyph, which it would skip.
                val spokenAmount = if (ingredient.isApproximate) {
                    stringResource(R.string.ingredient_amount_approximate, ingredient.amountLabel)
                } else {
                    null
                }
                Text(
                    text = if (ingredient.isApproximate) "≈ ${ingredient.amountLabel}" else ingredient.amountLabel,
                    modifier = Modifier
                        .padding(vertical = dimensionResource(id = R.dimen.padding_small))
                        .then(
                            if (spokenAmount != null) {
                                Modifier.semantics { contentDescription = spokenAmount }
                            } else {
                                Modifier
                            }
                        ),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun StepsList(steps: List<RecipeStep>) {
    if (steps.isEmpty()) {
        Text(
            text = stringResource(R.string.no_steps_listed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(dimensionResource(id = R.dimen.padding_medium)),
        )
        return
    }
    // hiltViewModel() has no Hilt component to resolve against in Compose Preview and crashes
    // there; steps.forEach below still renders the list (minus a working timer button) in previews.
    val timerViewModel: RecipeTimerViewModel? =
        if (LocalInspectionMode.current) null else hiltViewModel()
    val requestNotificationPermission = rememberNotificationPermissionRequester()
    val context = LocalContext.current
    val timerReplacedMessage = stringResource(R.string.step_timer_replaced_message)
    Column(modifier = Modifier.padding(dimensionResource(id = R.dimen.padding_medium))) {
        steps.sortedBy { it.orderIndex }.forEach { step ->
            val stepLabel = stringResource(R.string.step_timer_label_format, step.orderIndex + 1)
            StepListItem(
                step = step,
                onStartTimer = { totalSeconds ->
                    requestNotificationPermission()
                    val replaced = timerViewModel?.start(
                        stepLabel = stepLabel,
                        totalSeconds = totalSeconds,
                    )
                    if (replaced != null) {
                        val message = timerReplacedMessage.format(replaced.stepLabel)
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun StepListItem(step: RecipeStep, onStartTimer: (Long) -> Unit) {
    val durationSeconds = remember(step.instruction) { parseStepDurationSeconds(step.instruction) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensionResource(id = R.dimen.padding_medium)),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "${step.orderIndex + 1}.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = dimensionResource(id = R.dimen.padding_small))
        )
        Text(
            text = step.instruction,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (durationSeconds != null) {
            IconButton(onClick = { onStartTimer(durationSeconds) }) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = stringResource(R.string.start_step_timer_content_description),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecipeDetailsFullScreenPreview() {
    ChefAITheme {
        RecipeDetailsContent(recipe = RecipeData.recipe, isBookmarked = false)
    }
}

@Preview(showBackground = true)
@Composable
fun RecipeDetailsBookmarkedPreview() {
    ChefAITheme {
        RecipeDetailsContent(recipe = RecipeData.recipe, isBookmarked = true)
    }
}

@Preview(name = "From meal plan — to cook", showBackground = true)
@Composable
fun RecipeDetailsCookedToggleToCookPreview() {
    ChefAITheme {
        RecipeDetailsContent(recipe = RecipeData.recipe, showCookedToggle = true, isCooked = false)
    }
}

@Preview(name = "From meal plan — cooked", showBackground = true)
@Composable
fun RecipeDetailsCookedToggleCookedPreview() {
    ChefAITheme {
        RecipeDetailsContent(recipe = RecipeData.recipe, showCookedToggle = true, isCooked = true)
    }
}

@Preview(showBackground = true)
@Composable
fun RecipeDetailsDeleteConfirmationPreview() {
    ChefAITheme {
        RecipeDetailsContent(
            recipe = RecipeData.recipe,
            canDelete = true,
            showDeleteConfirmation = true,
        )
    }
}

@Preview(name = "Scaled to 8 portions", showBackground = true)
@Preview(
    name = "Scaled to 8 portions — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RecipeDetailsScaledPreview() {
    ChefAITheme {
        RecipeDetailsContent(
            recipe = RecipeData.recipe,
            servings = ServingsUiState.forRecipeServings(RecipeData.recipe.servings).copy(current = 8),
        )
    }
}

@Preview(name = "Recipe with no published yield", showBackground = true)
@Preview(
    name = "Recipe with no published yield — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RecipeDetailsEstimatedServingsPreview() {
    ChefAITheme {
        RecipeDetailsContent(recipe = RecipeData.recipe.copy(servings = 0))
    }
}

@Preview(showBackground = true)
@Composable
fun RecipeDetailsPrivatePreview() {
    ChefAITheme {
        RecipeDetailsContent(
            recipe = RecipeData.recipe.copy(privacy = RecipePrivacy.PRIVATE),
            isBookmarked = false,
        )
    }
}

private val stepsListPreviewSteps = listOf(
    RecipeStep(java.util.UUID.randomUUID(), 0, "Preheat the oven to 220°C."),
    RecipeStep(java.util.UUID.randomUUID(), 1, "Bake for 30 minutes, until golden brown."),
    RecipeStep(java.util.UUID.randomUUID(), 2, "Let rest for 5 minutes before serving."),
)

@Preview(name = "Steps tab", showBackground = true)
@Preview(
    name = "Steps tab — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun StepsListPreview() {
    ChefAITheme {
        StepsList(steps = stepsListPreviewSteps)
    }
}
